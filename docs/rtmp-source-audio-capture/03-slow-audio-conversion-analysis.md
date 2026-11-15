Ran inspect Sonic.java implementation
Analyzed RtmpAudioBridge.kt:L1-450
Analyzed output.txt:L150-580
Ran inspect DefaultAudioSink buffer sizing and processing
Ran inspect DefaultAudioSink pipeline and processBuffers
Analyzed MediaCodecEncoder.kt:L230-450
Analyzed MediaCodecEncoder.kt:L451-775

Your intuition is spot on—there are **two compounding reasons** why resampling causes a noticeable, persistent audio delay in the current implementation:

### 1. `SonicAudioProcessor` is heavy and runs inside the shared ring-buffer lock
In [RtmpAudioBridge.kt](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/rtmp/audio/RtmpAudioBridge.kt#L172-L202), `onExoPlayerAudioInput()` runs the entire conversion chain (`ToInt16PcmAudioProcessor` → `Pcm16ChannelMixingProcessor` → `SonicAudioProcessor`) **while holding `synchronized(lock)`**—the exact same lock that [ExoPlayerAudioSource.kt](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/rtmp/audio/ExoPlayerAudioSource.kt#L139) needs every 20 ms to pull audio frames for the encoder.

Under the hood, Media3's `SonicAudioProcessor` (`Sonic.java`) is a time-domain pitch/speed stretcher (WSOLA), not a lightweight resampler. When only `inputSampleRate != outputSampleRate` changes:
- It copies the PCM data **4–5 times** across internal arrays (`inputBuffer` → `outputBuffer` → `pitchBuffer` → `outputBuffer` → output `ByteBuffer`).
- Its rate-adjustment loop (`Sonic.adjustRate`) only simplifies `44100` / `48000` by powers of 2 (to `11025` / `12000` instead of `147` / `160`), calls `ensureSpaceForAdditionalFrames(..., 1)` on every single output frame, and performs 64-bit `long` division per sample per channel.
- When ExoPlayer's `DefaultAudioSink` pushes a burst of decoded audio to fill `AudioTrack`, `Sonic` processes that entire burst on ExoPlayer's audio thread while holding `lock`, blocking `ExoPlayerAudioSource.fillAudioFrame()`.

### 2. Backlog in `ringBuffer` (up to 750 ms) never drains back down
In [RtmpAudioBridge.kt](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/rtmp/audio/RtmpAudioBridge.kt#L283), `maxFillBytes` allows up to **750 ms** of audio to accumulate in the FIFO ring buffer:
- **Startup pre-buffering:** In [RtmpSourceSwitchHelper.kt](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/ui/main/RtmpSourceSwitchHelper.kt#L240-L279), `exoPlayerInstance.prepare()` and `playWhenReady = true` run during `awaitReady()` *before* `RTMPVideoSource` attaches the encoder surface. During that startup burst, `DefaultAudioSink` immediately pre-fills `AudioTrack` (and our `ringBuffer`) with 500–750 ms of audio.
- **No catch-up mechanism:** Because [ExoPlayerAudioSource.kt](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/rtmp/audio/ExoPlayerAudioSource.kt#L118-L163) pulls strictly at 1.0× wall-clock speed (`20 ms` every `20 ms`), any backlog accumulated during startup or during `Sonic` conversion stalls **stays in the ring buffer permanently** as a static 500–750 ms audio delay behind video.
- Furthermore, if `LockSupport.parkNanos` or lock contention makes the consumer even slightly slower than 1.0×, the ring buffer steadily fills until it hits the `750 ms` ceiling and stays pegged there.

---

### Proposed Improvements

1. **Replace the 3-stage `AudioProcessor` chain + `Sonic` with a fast, single-pass PCM converter & resampler:**
   - Convert PCM format (`PCM_8BIT` / `PCM_16BIT` / `PCM_24BIT` / `PCM_32BIT` / `PCM_FLOAT`), mix channels (mono ↔ stereo), and resample (fixed-point fractional linear interpolation with cross-chunk continuity) in a **single pass** into a pre-allocated buffer.
   - This eliminates `Sonic` altogether, removes 4–5 intermediate buffer copies per chunk, and is ~10–20× faster.
2. **Move conversion outside `synchronized(lock)`:**
   - Perform the format conversion, channel mixing, and resampling on ExoPlayer's thread *before* acquiring `synchronized(lock)`, so `lock` is only held for the final `System.arraycopy` into `ringBuffer`.
3. **Tighten the latency bound (~100–120 ms instead of 750 ms) and flush pre-buffered audio on start:**
   - Lower `maxFillBytes` from `750 ms` to ~`120 ms` (keeping only the freshest ~80–100 ms when bursts arrive) and flush stale pre-buffered audio when the stream/video surface starts so audio stays locked to current video frames.

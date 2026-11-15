Implemented the audio conversion, resampling, and buffering latency optimizations across the RTMP/SRT audio bridge:

### Changes Made
1. **Single-Pass PCM Conversion & Lock-Separated Ring Buffer** ([RtmpAudioBridge.kt](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/rtmp/audio/RtmpAudioBridge.kt))
   - Removed the 3-stage Media3 `AudioProcessor` pipeline (`ToInt16PcmAudioProcessor`, `Pcm16ChannelMixingProcessor`, and `SonicAudioProcessor`).
   - Replaced it with `convertAndResampleLocked()`, which decodes supported PCM formats (`PCM_8BIT`, `PCM_16BIT`/`_BIG_ENDIAN`, `PCM_24BIT`/`_BIG_ENDIAN`, `PCM_32BIT`/`_BIG_ENDIAN`, `PCM_FLOAT`), mixes channel layouts, and performs Q15 fixed-point linear resampling with cross-chunk frame continuity in a single pass using pre-allocated scratch buffers.
   - Kept 16-bit matching-rate/channel passthrough directly copying to the ring buffer, and moved CPU conversion/resampling under a dedicated `conversionLock` so `ExoPlayerAudioSource` reading under `lock` is no longer blocked during input bursts.
   - Reduced the ring-buffer capacity to `500 ms` (`RING_CAPACITY_MS`) and max buffered level to `160 ms` (`MAX_BUFFER_MS`), and added `flushRingBuffer()` and `availableDurationMs()`.

2. **Start Flush & Drift Catch-Up** ([ExoPlayerAudioSource.kt](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/rtmp/audio/ExoPlayerAudioSource.kt))
   - Flushed stale buffered audio via `bridge.flushRingBuffer()` inside `startStream()`.
   - Added gentle catch-up pacing in `fillAudioFrame()`: when `bridge.availableDurationMs() > 100`, the inter-frame interval is trimmed by 5% (`(baseFrameDurationNs * 95L) / 100L`) so queue buildup drains smoothly without dropping audio.

3. **Smaller `AudioTrack` Buffer & Flush on Video Attach** ([RtmpSourceSwitchHelper.kt](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/ui/main/RtmpSourceSwitchHelper.kt))
   - Configured `DefaultAudioSink.Builder` in `createExoPlayer()` with `DefaultAudioTrackBufferSizeProvider` (`minPcmBufferDurationUs = 80_000`, `maxPcmBufferDurationUs = 150_000`, `pcmBufferMultiplicationFactor = 2`).
   - Called `RtmpAudioBridge.flushRingBuffer()` in `switchToRtmpSource()` after attaching `RTMPVideoSource` and `ExoPlayerAudioSourceFactory`.

4. **Unit Tests** ([RtmpAudioBridgeTest.kt](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/test/java/com/dimadesu/lifestreamer/rtmp/audio/RtmpAudioBridgeTest.kt))
   - Updated `ringBufferOverflowCapsFillLevelAndKeepsNewestFrameAlignedData` for the `160 ms` cap (`30,720` bytes at 48 kHz stereo) and verified `flushRingBuffer()` behavior.
   - Updated `sampleRateConversion44100To48000ProducesNonZeroOutputAndImmediateAudio` and added `downsamplingFloatMono48000ToStereo44100PreservesContinuityAndImmediateOutput` to check immediate output availability, stereo channel duplication, signal amplitude, and cross-chunk continuity.

### Verification
- Ran `./gradlew :app:testDebugUnitTest :app:assembleDebug`:
  - `BUILD SUCCESSFUL` with zero Kotlin warnings in the modified code.
  - All 14 unit tests passed (`RtmpAudioBridgeTest`: 7/7, `CameraNoiseReductionControllerTest`: 3/3, `RtmpSourceSettingsTest`: 3/3, `ExampleUnitTest`: 1/1; 0 skipped, 0 failed).

---

In simple terms, think of the audio pipeline like a **conveyor belt** between the incoming RTMP video player and the outgoing live stream:

1. **Before (why it was delayed):**
   - **Too many slow stations on the belt:** When the incoming audio didn't match your stream settings (for example, 44.1 kHz vs. 48 kHz, or mono vs. stereo), the app handed the audio through **three separate converter steps**, including a heavy-duty tool built for changing music speed and pitch (`Sonic`). While it was busy converting, the outgoing stream had to wait at the door because they shared the same lock.
   - **A long line built up and never shrank:** While the video player was getting ready to start, about **half a second to three-quarters of a second** of audio piled up in a waiting queue before the video even appeared on screen. Because the outgoing stream only pulled audio out at normal 1× speed, that half-second line never got any shorter—so audio stayed permanently half a second behind the video.

2. **Now (what we changed):**
   - **One fast step instead of three:** Format conversion, mono/stereo mixing, and sample-rate conversion now happen in **one lightweight step** before touching the shared queue, so the outgoing stream never gets held up waiting for conversion.
   - **Cleared the line when video starts & kept the queue short:** Any old audio that piles up while the player is loading is **thrown away the moment the video starts**, and the waiting queue is capped to a tiny cushion (~0.1 seconds instead of 0.75 seconds). If the queue ever starts creeping up, the stream pulls slightly faster for a moment to catch right back up.

**One-sentence summary:**
> *"Previously, converting the audio was slow and let half a second of old audio pile up in a queue before the video started; now we convert it in one fast step and keep the queue tiny so the audio stays in sync with the video."*

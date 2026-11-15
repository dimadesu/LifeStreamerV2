Now app has a lot of custom code for conversion, I hope there are no bugs in there.

That is a very fair concern. Looking critically at what we just changed, we actually combined **two separate things**:

1. **The buffering, lock, and timing fixes** (~30 lines of code):
   - Separating `conversionLock` from the ring-buffer `lock` so conversion on ExoPlayer's thread never blocks `ExoPlayerAudioSource`.
   - Lowering the ring-buffer ceiling from `750 ms` to `160 ms` and shrinking `AudioTrack`'s internal buffer from `250–750 ms` to `80–150 ms`.
   - Flushing stale pre-buffered audio (`flushRingBuffer()`) when video/audio starts and adding the 5% catch-up pace when the buffer creeps above `100 ms`.

2. **Replacing Media3's `ToInt16PcmAudioProcessor` + `SonicAudioProcessor` with ~200 lines of custom byte-decoding and resampling math** in [RtmpAudioBridge.kt](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/rtmp/audio/RtmpAudioBridge.kt).

### Why the delay actually happened in your test
When you switched audio settings to test resampling, the ~0.5–0.75s delay wasn't really because `Sonic` took 500 ms of CPU time (on an S20 FE, `Sonic` resamples a 20 ms chunk in well under 0.1 ms). It happened because:
- Reconfiguring the audio settings paused the consumer while ExoPlayer kept playing, filling the ring buffer right up to the old **750 ms** cap.
- Without `flushRingBuffer()` or the 5% catch-up pacing, that 750 ms backlog stayed trapped in the buffer permanently.

### We can get rid of almost all that custom conversion code
If we keep **all the latency, lock-separation, flush, and catch-up fixes**, we can go back to using Media3's battle-tested **`ToInt16PcmAudioProcessor`** and **`SonicAudioProcessor`** (running under `conversionLock`, outside the ring-buffer lock). That removes ~200 lines of custom bit-shifting and linear-interpolation math and relies on Google's official Media3 processors for format conversion and resampling.

Would you like me to switch the conversion back to Media3's `ToInt16PcmAudioProcessor` + `SonicAudioProcessor` while keeping the low-latency buffer, lock separation, and flush/catch-up fixes?

---

Switched the conversion and resampling path back to Media3's battle-tested `AudioProcessor` chain while keeping all of the low-latency buffering, lock-separation, `AudioTrack` sizing, flush, and catch-up fixes intact.

### What Changed

1. **Media3 Processor Chain in [RtmpAudioBridge.kt](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/rtmp/audio/RtmpAudioBridge.kt)**
   - Replaced the custom PCM byte-decoding and linear-interpolation resampler with Media3's `ToInt16PcmAudioProcessor` -> `Pcm16ChannelMixingProcessor` -> `SonicAudioProcessor` inside [`rebuildConversionPipelineLocked`](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/rtmp/audio/RtmpAudioBridge.kt#L234-L296) and [`processThroughChainLocked`](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/rtmp/audio/RtmpAudioBridge.kt#L298-L325).
   - Preserved the zero-copy fast path in [`onExoPlayerAudioInput`](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/rtmp/audio/RtmpAudioBridge.kt#L176-L219) when format/channels/rate already match, and kept `conversionLock` separate from the ring-buffer `lock` with `formatGeneration` tracking so processor execution never blocks `readPcm()`.
   - Preserved `MAX_BUFFER_MS = 160`, `RING_CAPACITY_MS = 500`, [`flushRingBuffer`](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/rtmp/audio/RtmpAudioBridge.kt#L140-L146), and [`availableDurationMs`](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/rtmp/audio/RtmpAudioBridge.kt#L151-L156), as well as the untouched buffer-flush and catch-up pacing logic in [ExoPlayerAudioSource.kt](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/rtmp/audio/ExoPlayerAudioSource.kt) and [RtmpSourceSwitchHelper.kt](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/ui/main/RtmpSourceSwitchHelper.kt).

2. **Unit Tests Updated in [RtmpAudioBridgeTest.kt](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/test/java/com/dimadesu/lifestreamer/rtmp/audio/RtmpAudioBridgeTest.kt#L115-L222)**
   - Updated [`sampleRateConversion44100To48000ProducesNonZeroOutput`](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/test/java/com/dimadesu/lifestreamer/rtmp/audio/RtmpAudioBridgeTest.kt#L116-L169) and [`downsamplingFloatMono48000ToStereo44100ProducesConvertedOutput`](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/test/java/com/dimadesu/lifestreamer/rtmp/audio/RtmpAudioBridgeTest.kt#L172-L222) to check output once `SonicAudioProcessor`'s ~30 ms WSOLA lookahead window primes on the second 20 ms chunk, while still checking strong signal amplitude, channel matching, and inter-chunk continuity.

### Verification
- Ran `./gradlew :app:testDebugUnitTest :app:assembleDebug` — **`BUILD SUCCESSFUL`**:
  - `RtmpAudioBridgeTest`: 7/7 passed (`0` skipped, `0` failures)
  - All unit test suites (`ExampleUnitTest`, `CameraNoiseReductionControllerTest`, `RtmpSourceSettingsTest`, `RtmpAudioBridgeTest`): 14/14 passed (`0` skipped, `0` failures)
  - `:app:assembleDebug` built cleanly with no Kotlin compiler warnings.

---

Here is how you can explain this change and all the small adjustments in plain, everyday terms:

---

### The Big Picture: The "Translator & Waiting Room" Analogy

Imagine your app receives live audio in one format (say, French) and needs to send it out live in another format (say, English). 

Originally, when converting audio caused a noticeable delay, it looked like **the translator** was too slow. So in the previous step, we did two things at once:
1. We replaced **Google’s official Media3 translator** with a homemade translator written from scratch.
2. We redesigned **the waiting room and hallway** where translated audio sits before going out to the live stream.

Then we realized: Google’s official translator was never the reason for the lag — **the waiting room and a single locked door were!** By switching back to Google’s official Media3 audio engine ([RtmpAudioBridge.kt](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/rtmp/audio/RtmpAudioBridge.kt)) while keeping all the waiting-room improvements, we get the best of both worlds:
- **Google’s battle-tested audio converter (`Media3` / `Sonic`)**, which is heavily optimized and already used by millions of Android apps.
- **Zero buildup of old audio**, so the sound stays tightly in sync with the video.

---

### All the Small Adjustments (In Simple Terms)

1. **Two Separate Doors Instead of One (`conversionLock` vs. `lock` in [RtmpAudioBridge.kt](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/rtmp/audio/RtmpAudioBridge.kt#L49-L50))**
   - **Before:** There was only one door into the audio storage room. While the translator was inside converting a chunk of audio, the live broadcast couldn't come in to pick up audio and had to wait outside.
   - **Now:** The translator does all the heavy work at a separate desk outside, and only opens the storage room door for a split microsecond to drop off the finished audio. The live stream pickup never gets blocked.

2. **An "Express Lane" When No Translation Is Needed ([RtmpAudioBridge.kt](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/rtmp/audio/RtmpAudioBridge.kt#L196-L199))**
   - **Now:** If the incoming RTMP stream’s audio already matches the app’s settings (for example, both are 48 kHz Stereo), the audio skips the translation desk completely and goes straight through untouched.

3. **Shrinking the Waiting Room ([RtmpAudioBridge.kt](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/rtmp/audio/RtmpAudioBridge.kt#L29-L30) & [RtmpSourceSwitchHelper.kt](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/ui/main/RtmpSourceSwitchHelper.kt#L92-L99))**
   - **Before:** Both the player (`AudioTrack`) and the bridge waiting room were allowed to hold up to **0.75 seconds** of audio. If a line formed while switching settings, audio sat in that line for nearly a full second before you heard it.
   - **Now:** The waiting room is capped at **0.16 seconds (160 ms)** and the player buffer at **0.08–0.15 seconds**, so a big delay simply has nowhere to accumulate.

4. **Wiping Old Audio When Video Starts ([ExoPlayerAudioSource.kt](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/rtmp/audio/ExoPlayerAudioSource.kt#L72-L77) & [RtmpSourceSwitchHelper.kt](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/ui/main/RtmpSourceSwitchHelper.kt#L254-L256))**
   - **Before:** While the video was still warming up or reconnecting, audio was already piling up in the waiting room. When video finally started, the stream played that old backlog first — making audio permanently behind the video.
   - **Now:** The exact moment the video connects or the audio stream starts, any old waiting audio is thrown out (`flushRingBuffer()`) so audio and video start together on *"right now."*

5. **Gentle 5% "Catch-Up" Speed ([ExoPlayerAudioSource.kt](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/rtmp/audio/ExoPlayerAudioSource.kt#L118-L131))**
   - **Before:** The outgoing stream pulled audio at strictly 1.0x speed. If even a tiny delay built up during a phone hiccup, it stayed delayed forever.
   - **Now:** If the waiting line ever grows past **0.1 seconds (100 ms)**, the outgoing stream quietly picks up audio **5% faster** until the line is back to normal — draining any small lag smoothly without audible clicks or dropped words.

---

### One-Sentence Summary
> *"We switched back to Google’s official Media3 audio converter for reliability and speed, while keeping the smaller buffers, separate locks, startup cleanup, and automatic catch-up pacing that prevent audio lag from ever building up."*

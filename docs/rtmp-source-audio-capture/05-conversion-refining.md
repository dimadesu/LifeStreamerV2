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

Analyzed RtmpAudioBridge.kt:L1-564
Analyzed RtmpAudioBridgeTest.kt:L1-280
Used tool: exit_plan_mode
Analyzed RtmpAudioBridge.kt:L1-564
Analyzed RtmpAudioBridgeTest.kt:L1-280
Ran inspect previous Media3 processor usage
Analyzed output.txt:L120-310
Ran check original RtmpAudioBridgeTest with SonicAudioProcessor
Edited RtmpAudioBridge.kt
Analyzed RtmpAudioBridge.kt:L165-495
Edited RtmpAudioBridge.kt
Edited RtmpAudioBridgeTest.kt
Ran run unit tests and debug APK build
Analyzed output.txt:L200-277
Ran check test result XML details
Ran inspect all test suite XML summaries and git diff
Analyzed RtmpAudioBridge.kt:L170-390

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
Implemented `RtmpAudioBridge`, `ExoPlayerAudioSource`, and integrated direct ExoPlayer PCM capture across `RtmpSourceSwitchHelper`, `PreviewViewModel`, `PreviewFragment`, `BluetoothAudioManager`, `CameraStreamerService`, `AudioDrawerController`, and `AudioDebugInfoReader`, along with unit tests in `RtmpAudioBridgeTest`.

Running `./gradlew :app:testDebugUnitTest :app:assembleDebug` now and waiting for completion.

Edited RtmpAudioBridge.kt
Edited RtmpAudioBridge.kt
Analyzed RtmpAudioBridge.kt:L1-130
Ran re-run unit tests and debug build

Fixed the `androidx.media3.common.Format.NO_VALUE` import in [RtmpAudioBridge.kt](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/rtmp/audio/RtmpAudioBridge.kt) and re-ran `./gradlew :app:testDebugUnitTest :app:assembleDebug`. Waiting for the build and tests to finish.

Used tool: enter_plan_mode
Analyzed TEST-com.dimadesu.lifestreamer.rtmp.audio.RtmpAudioBridgeTest.xml:L1-12
Ran check git status

Direct ExoPlayer PCM audio capture (`RtmpAudioBridge` + `ExoPlayerAudioSource`) has been implemented and verified, replacing `MediaProjection` for RTMP/SRT sources while keeping `MediaProjection` intact for Screen Recording and Camera `SYS AUDIO`.

### What Was Implemented

1. **ExoPlayer Audio Tap & Conversion Pipeline ([RtmpAudioBridge.kt](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/rtmp/audio/RtmpAudioBridge.kt))**
   - `TapAudioProcessor` (`BaseAudioProcessor` subclass) intercepts decoded PCM inside `DefaultAudioSink` before `AudioTrack` volume is applied and forwards the untouched buffer downstream to `AudioTrack` (so `exoPlayer.volume = 0f` mutes the phone speaker while captured stream audio stays at full level, and toggling **Audio Monitor** sets `exoPlayer.volume = 1f`).
   - Automatically converts incoming ExoPlayer PCM to match the app's configured `AudioSourceConfig`:
     - **Sample format:** `ToInt16PcmAudioProcessor` converts float/24-bit/32-bit/8-bit PCM to 16-bit little-endian PCM.
     - **Channel layout:** `Pcm16ChannelMixingProcessor` converts Mono $\leftrightarrow$ Stereo (and downmixes multi-channel inputs).
     - **Sample rate:** `SonicAudioProcessor` resamples incoming audio (e.g., $44.1\text{ kHz} \leftrightarrow 48\text{ kHz}$) to the encoder's target sample rate.
   - **Frame-aligned circular buffer & `ByteBuffer` contract:** Buffers up to $1000\text{ ms}$ (capped at $750\text{ ms}$ fill level so initial `AudioTrack` fill bursts preserve A/V sync without unbounded latency), pads underflows with silence (`0`), and resets `dest.position(0)` and `dest.limit(bytesToFill)` (`ByteOrder.LITTLE_ENDIAN`) as expected by StreamPack's `RawFramePullPush`, `MuteEffect`, `VuMeterEffect`, and `MediaCodecEncoder`.

2. **Wall-Clock Paced StreamPack Audio Source ([ExoPlayerAudioSource.kt](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/rtmp/audio/ExoPlayerAudioSource.kt))**
   - Implements `IAudioSourceInternal`, `IAudioFrameSourceInternal`, and `Releasable`.
   - Paces `fillAudioFrame(buffer)` at $20\text{ ms}$ intervals using `System.nanoTime()` and `LockSupport.parkNanos` (with a $200\text{ ms}$ drift resync threshold) so `RawFramePullPush` never busy-spins or starves `MediaCodec` when ExoPlayer is buffering, reconnecting, or in bitmap fallback.
   - `ExoPlayerAudioSourceFactory.isSourceEquals` returns `source is ExoPlayerAudioSource` so hot-swapping between RTMP playback and the RTMP bitmap fallback reuses the same audio source without encoder restarts.

3. **Pipeline & UI Wiring**
   - [RtmpSourceSwitchHelper.kt](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/ui/main/RtmpSourceSwitchHelper.kt): Attaches `RtmpAudioBridge.createTapAudioProcessor()` to `DefaultAudioSink` via a custom `DefaultRenderersFactory` in `createExoPlayer()`, and sets `ExoPlayerAudioSourceFactory()` in `switchToRtmpSource()` and `switchToBitmapFallback()`.
   - [PreviewViewModel.kt](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/ui/main/PreviewViewModel.kt) & [PreviewFragment.kt](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/ui/main/PreviewFragment.kt): Removed the `MediaProjection` permission prompt and explanation dialog when switching to RTMP/SRT sources or starting a stream from an RTMP source.
   - [BluetoothAudioManager.kt](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/audio/BluetoothAudioManager.kt), [CameraStreamerService.kt](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/services/CameraStreamerService.kt), & [AudioDrawerController.kt](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/ui/main/audio/AudioDrawerController.kt): Treated `ExoPlayerAudioSource` alongside `IMediaProjectionSource` as a non-microphone source so Bluetooth SCO / mic preferences do not override it.
   - [AudioDebugInfoReader.kt](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/ui/main/audio/AudioDebugInfoReader.kt): Reports `"EXOPLAYER"` and the live incoming stream format (`ExoPlayer (<rate>Hz Mono/Stereo)`) in the Audio Drawer's **Actual System State** card.

4. **Unit Tests ([RtmpAudioBridgeTest.kt](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/test/java/com/dimadesu/lifestreamer/rtmp/audio/RtmpAudioBridgeTest.kt))**
   - Verified underflow silence padding and StreamPack `ByteBuffer` `position`/`limit` contract, passthrough, Mono $\rightarrow$ Stereo and Stereo $\rightarrow$ Mono mixing, $44.1\text{ kHz} \rightarrow 48\text{ kHz}$ resampling via `SonicAudioProcessor`, and $750\text{ ms}$ ring buffer overflow capping (`6/6` tests passing, `./gradlew :app:testDebugUnitTest :app:assembleDebug` succeeded).
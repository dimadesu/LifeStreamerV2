Here is an analysis of how RTMP/SRT playback and fallback work today, why video gaps happen when the incoming stream is disrupted, and how we can both **detect disruptions earlier** and **eliminate frame gaps** with a frame-timeout fallback.

---

### 1. How Stream Disruption Detection Works Today

Detection is currently handled in [`PreviewViewModel.monitorRtmpConnection()`](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/ui/main/PreviewViewModel.kt#L2993-L3075) via a `Player.Listener` attached after `ExoPlayer` connects:
- **`onPlayerError`** $\rightarrow$ immediately calls [`handleRtmpDisconnection()`](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/ui/main/PreviewViewModel.kt#L3083-L3116).
- **`STATE_ENDED` / `STATE_IDLE`** $\rightarrow$ immediately calls `handleRtmpDisconnection()`.
- **`STATE_BUFFERING`** $\rightarrow$ starts `bufferingCheckJob`, waiting **`2_000 ms`** (`maxBufferingDuration`) before calling `handleRtmpDisconnection()`.
- **`onIsPlayingChanged(false)` while `STATE_READY`** $\rightarrow$ only logs a debug message ([line 3066](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/ui/main/PreviewViewModel.kt#L3066-L3069)) and takes no action.

---

### 2. Why Detection Is Delayed & Why Video Gaps Occur

#### A. The 2-second `STATE_BUFFERING` wait happens *after* the buffer is already empty
When an upstream RTMP/SRT publisher stops sending data without a clean close:
1. `ExoPlayer` first plays through its buffered media (`bufferForPlaybackMs`, default `2500 ms`) while remaining in `STATE_READY`.
2. It transitions to `STATE_BUFFERING` **only when the sample queue is completely empty**—meaning video frame rendering has *already stopped*.
3. Waiting another `2,000 ms` in `STATE_BUFFERING` before triggering fallback guarantees at least a **2-second gap of zero video frames**.

#### B. `DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS` (5,000 ms) vs. 2,000 ms timeout
In [`RtmpSourceSwitchHelper.createExoPlayer()`](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/ui/main/RtmpSourceSwitchHelper.kt#L50-L57):
```kotlin
val loadControl = DefaultLoadControl.Builder()
    .setBufferDurationsMs(
        DefaultLoadControl.DEFAULT_MIN_BUFFER_MS, // 50s
        DefaultLoadControl.DEFAULT_MAX_BUFFER_MS, // 50s
        bufferForPlaybackMs,
        DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS // 5,000 ms!
    )
    .build()
```
Once `ExoPlayer` enters `STATE_BUFFERING` (even from a 100 ms network hiccup), `DefaultLoadControl` requires **5,000 ms** of buffered media before transitioning back to `STATE_READY`. Because a live stream arrives at `1x` real-time speed, accumulating 5 seconds of buffer takes 5 seconds of wall-clock time—so the `2,000 ms` `bufferingCheckJob` **always** kills the player before it is allowed to recover, while producing no frames for those 2 seconds.

#### C. Internal Media3 retries & blocking `DataSource` reads hide transport errors
- **Media3 `DefaultLoadErrorHandlingPolicy`**: [`ProgressiveMediaSource.Factory`](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/ui/main/RtmpSourceSwitchHelper.kt#L87-L99) uses Media3's default error policy, which retries `DataSource` load errors **3 times** with backoff (~1s, 2s, …) before surfacing `onPlayerError`.
- **`SrtDataSource` read behavior**: In [`SrtDataSource.read()`](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/player/SrtDataSource.kt#L80-L111), `it.recv(PAYLOAD_SIZE)` blocks until SRT's peer idle timeout (~5s), and if `recv` returns an empty array, `read()` returns `0` instead of throwing an `IOException` or returning `C.RESULT_END_OF_INPUT`.
- **`RtmpClient` socket timeout**: In [`RtmpClient.java`](file:///Users/dmytroantonov/git/LifeStreamerV2/inspiration/LibRtmp-Client-for-Android/rtmp-client/src/main/java/io/antmedia/rtmp_client/RtmpClient.java#L25-L31), `receiveTimeoutInMs` defaults to `10,000 ms`.

#### D. No video-frame watchdog (silent freezes in `STATE_READY`)
`monitorRtmpConnection` only monitors `ExoPlayer` state, not whether video frames are actually being rendered. If the upstream encoder stops sending video packets while audio continues, or the hardware decoder wedges on corrupt H.264/HEVC data, `ExoPlayer` stays in `STATE_READY` indefinitely while **zero video frames** are rendered.

#### E. Purely event-driven GL pipeline starves the video encoder during stalls
- Both [`RTMPVideoSource`](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/rtmp/video/RTMPVideoSource.kt#L605) and [`VideoInput`](file:///Users/dmytroantonov/git/LifeStreamerV2/StreamPack/core/src/main/java/io/github/thibaultbee/streampack/core/pipelines/StreamerPipeline.kt#L94) use [`DefaultSurfaceProcessor`](file:///Users/dmytroantonov/git/LifeStreamerV2/StreamPack/core/src/main/java/io/github/thibaultbee/streampack/core/elements/processing/video/DefaultSurfaceProcessor.kt#L48-L52) with `cfrFps = 0` (event-driven: it only renders to the encoder `Surface` inside [`onFrameAvailable()`](file:///Users/dmytroantonov/git/LifeStreamerV2/StreamPack/core/src/main/java/io/github/thibaultbee/streampack/core/elements/processing/video/DefaultSurfaceProcessor.kt#L442-L476)).
- Meanwhile, [`ExoPlayerAudioSource`](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/rtmp/audio/ExoPlayerAudioSource.kt#L152-L167) continuously pushes 20 ms audio frames (padding with silence when [`RtmpAudioBridge`](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/rtmp/audio/RtmpAudioBridge.kt) is empty).
- Whenever `ExoPlayer` stops rendering frames, `MediaCodec` receives no input surfaces and outputs **0 video packets** while audio packets keep flowing—causing timestamp gaps, bitrate drops, and downstream player/OBS stalls.
- Additionally, in [`RTMPVideoSource.addSurfacesToProcessor()`](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/rtmp/video/RTMPVideoSource.kt#L660-L666), if `videoFormat` dimensions aren't cached yet when `setOutput` is called, `outputSurfaceOutput` creation is deferred, and [`DefaultSurfaceProcessor.TRANSITION_TIMEOUT_MS`](file:///Users/dmytroantonov/git/LifeStreamerV2/StreamPack/core/src/main/java/io/github/thibaultbee/streampack/core/elements/processing/video/DefaultSurfaceProcessor.kt#L663) stops rendering transition black frames after 3 seconds if no frame has arrived.

---

### 3. Proposed Improvements

We can address this at two complementary levels: **gapless frame generation on timeout** (so the encoder never starves) and **faster disruption detection** (so fallback/reconnect starts much sooner).

#### 1. Frame-Timeout Watchdog $\rightarrow$ Render Black Frames (or Repeat Last Frame)
In [`DefaultSurfaceProcessor.kt`](file:///Users/dmytroantonov/git/LifeStreamerV2/StreamPack/core/src/main/java/io/github/thibaultbee/streampack/core/elements/processing/video/DefaultSurfaceProcessor.kt) (which already has a GL render loop and [`renderBlackFrame()`](file:///Users/dmytroantonov/git/LifeStreamerV2/StreamPack/core/src/main/java/io/github/thibaultbee/streampack/core/elements/processing/video/DefaultSurfaceProcessor.kt#L526-L541) for source-switch transitions):
- Add a **frame-stall timeout** (e.g., if no `onFrameAvailable` arrives for **`150–250 ms`** while streaming is active).
- When the timeout triggers, automatically start the GL render loop at the configured stream `fps` (e.g., 30 fps) to push **black frames** (with monotonic `System.nanoTime()` presentation timestamps) to the encoder output surface until either:
  - A new frame arrives on `onFrameAvailable` (which immediately resumes normal frame rendering), or
  - The disconnect detector switches the source to `BitmapSource` (or restarts the stream).
- Also remove or extend the 3-second cap on `transitionTimeoutRunnable` while waiting for a new source's first frame so transitions never drop back to zero frames.

#### 2. Early Disruption Detection on `ExoPlayer`
1. **Video Frame Delivery Watchdog (`VideoFrameMetadataListener`)**:
   - Register a `VideoFrameMetadataListener` on `ExoPlayer` (called on every decoded frame about to be rendered) or track frame arrival in `RTMPVideoSource`.
   - If the player is in `STATE_READY` (or `STATE_BUFFERING`) and **no video frame is rendered for e.g. `1,000 ms`**, treat it as a disrupted stream and trigger `handleRtmpDisconnection()`. This catches video-only freezes, decoder stalls, and `STATE_BUFFERING` stalls with a single unified timer.
2. **Fix `DefaultLoadControl` Rebuffer Threshold**:
   - In [`RtmpSourceSwitchHelper.createExoPlayer()`](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/ui/main/RtmpSourceSwitchHelper.kt#L50-L57), change `bufferForPlaybackAfterRebufferMs` from `5,000 ms` to e.g. **`500 ms`** (or `minOf(bufferForPlaybackMs, 500)`), and reduce `maxBufferingDuration` from `2,000 ms` to **`1,000 ms`**.
3. **Fail-Fast `LoadErrorHandlingPolicy` (`0` internal retries)**:
   - Configure `ProgressiveMediaSource.Factory` with `DefaultLoadErrorHandlingPolicy(/* minimumLoadableRetryCount = */ 0)` so socket/EOF/read errors immediately fire `onPlayerError` instead of silently retrying 3 times inside `ExoPlayer`.
4. **Harden `SrtDataSource.read()`**:
   - In [`SrtDataSource.kt`](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/player/SrtDataSource.kt#L80-L111), configure a shorter `SRTO_RCVTIMEO` (e.g., `1500–2000 ms`) on the `SrtSocket` and throw `IOException` if `recv()` returns `<= 0` or the socket disconnects, rather than returning `0` bytes.
5. **Immediate Bitmap Fallback When Switching to RTMP**:
   - In [`RtmpSourceSwitchHelper.switchToRtmpSource()`](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/ui/main/RtmpSourceSwitchHelper.kt#L204-L249), if the user switches from Camera to an RTMP source that isn't immediately ready, switch to `BitmapSource` (or rely on the black-frame timeout) *before* waiting up to `bufferForPlaybackMs + 5000 ms` in `awaitReady()`, matching what the KDoc intended.

---

### Questions Before We Plan the Changes
1. **Fallback frame content during a short stall (before BitmapSource takes over)**: When frames stop arriving for > ~200 ms, do you prefer rendering **black frames** during that window until the disconnect threshold (~1s) switches to `BitmapSource`, or repeating the **last good video frame** briefly before switching to black/bitmap?
2. **Disruption threshold for switching to BitmapSource**: Does **~1,000 ms** without any rendered video frames (down from 2,000 ms of `STATE_BUFFERING` + internal retries) sound like the right threshold to declare the stream disrupted and switch to `BitmapSource` / restart?

---
---
---
---
---

Ran find branch base and commit history
Ran inspect full branch diff against main
Analyzed output.txt:L1-800
Analyzed output.txt:L801-1249
Ran check all RTMP player lifecycle sites in PreviewViewModel
Analyzed PreviewViewModel.kt:L2415-2470
Analyzed PreviewViewModel.kt:L2630-2675
Analyzed PreviewViewModel.kt:L4800-4865
Analyzed PreviewViewModel.kt:L3570-3665
Analyzed PreviewViewModel.kt:L3530-3575
Ran check activeRtmpIndex and bitrate regulator helpers
Analyzed PreviewViewModel.kt:L775-825
Analyzed PreviewViewModel.kt:L3870-3925
Analyzed PreviewViewModel.kt:L4030-4240
Ran check service rebind sync in PreviewViewModel
Analyzed PreviewViewModel.kt:L1185-1315
Analyzed PreviewViewModel.kt:L1316-1390

**Mostly good to ship once you run an on-device pass**, with a few known gaps worth checking before merging `better-rtmp-src-disconnect-detection` (`e25da0da`) into `main`.

### What's Solid
- **ExoPlayer lifecycle in background & stop/start** ([RTMPVideoSource.kt](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/rtmp/video/RTMPVideoSource.kt#L178-L185), [#L429-L446](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/rtmp/video/RTMPVideoSource.kt#L429-L446)): removing delayed `exoPlayer.stop()` from `stopPreview()`/`stopStream()` keeps the source hot for background Start/Stop and eliminates spurious `STATE_IDLE` disconnects.
- **Unified 1s frame watchdog** ([PreviewViewModel.kt](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/ui/main/PreviewViewModel.kt#L3027-L3128)): catches silent video freezes in `STATE_READY` as well as stalled `STATE_BUFFERING`, while pausing when `playWhenReady == false`.
- **Manual-stop & stop-race guards + regulator restore + clean cancellation** ([PreviewViewModel.kt](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/ui/main/PreviewViewModel.kt#L3150-L3410), [#L3595-L3653](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/ui/main/PreviewViewModel.kt#L3595-L3653); [RtmpSourceSwitchHelper.kt](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/ui/main/RtmpSourceSwitchHelper.kt#L117-L230)): prevents `handleRtmpDisconnectionWithRestart()` from restarting a manually stopped broadcast and restores the bitrate regulator on first-attempt bitmap fallback.
- **Buffer & fail-fast IO alignment** ([RtmpSourceSwitchHelper.kt](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/ui/main/RtmpSourceSwitchHelper.kt#L54-L133), [RtmpSourceSwitchHelperTest.kt](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/test/java/com/dimadesu/lifestreamer/rtmp/RtmpSourceSwitchHelperTest.kt)): honors `bufferForPlaybackMs` on rebuffer and disables slow internal Media3 IO retries (`19/19` JVM unit tests passing).

---

### Real Gaps & Trade-offs in `main..HEAD`

1. **No on-device verification yet (biggest gap before shipping)**
   - Everything past `662b378e` is verified only by JVM unit tests (`19` tests) and `assembleDebug`, not on a real device/emulator through backgrounding + notification controls + RTMP kill/restart.

2. **Switching away from RTMP via UVC / Screen / another RTMP index leaves the old monitor/player active**
   - Toggling **UVC** ([PreviewViewModel.kt](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/ui/main/PreviewViewModel.kt#L3910), [#L4043](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/ui/main/PreviewViewModel.kt#L4043)) or **Screen** ([PreviewViewModel.kt](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/ui/main/PreviewViewModel.kt#L4150)) clears `_activeRtmpIndex` **without** cancelling `rtmpRetryJob`, calling `stopRtmpConnectionMonitoring()`, or stopping `currentRtmpPlayer`. With the 1s watchdog + `RTMPVideoSource.release()` stopping the old player, the old player's listener/watchdog can still trigger `handleRtmpDisconnection()` after switching to UVC or Screen.
   - Switching directly from **RTMP 1 → RTMP 2** in `toggleVideoSource()` ([PreviewViewModel.kt](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/ui/main/PreviewViewModel.kt#L3595)) likewise cancels `rtmpRetryJob` without calling `stopRtmpConnectionMonitoring()` first before preparing RTMP 2.
   - *(This is the main code fix I'd consider adding before merging to `main` if users switch between RTMP and UVC/Screen/RTMP-2.)*

3. **If the OS/user destroys the UI (`onCleared()`) while `CameraStreamerService` stays alive**
   - `onCleared()` stops `currentRtmpPlayer` and detaches `monitorRtmpConnection()` ([PreviewViewModel.kt](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/ui/main/PreviewViewModel.kt#L4825-L4836)).
   - Normal backgrounding (`Home` button) keeps `PreviewViewModel` alive, so background notification Start/Stop works fine there. **However**, if the Activity itself is killed and the user taps **Start** from the notification afterward, `RTMPVideoSource.startStream()` re-prepares ExoPlayer unmonitored (no disconnect detection), and `onServiceConnected()` ([PreviewViewModel.kt](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/ui/main/PreviewViewModel.kt#L1185-L1366)) does not re-attach `monitorRtmpConnection()` if the Activity is later recreated while streaming.

4. **Known intentional/deferred limitations (fine to ship as follow-up if expected)**
   - **~1s zero-video-frame window on stall:** `RTMPVideoSource` still uses `DefaultSurfaceProcessorFactory()` with `cfrFps = 0` ([RTMPVideoSource.kt](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/rtmp/video/RTMPVideoSource.kt#L493)), so no GL keepalive frames are sent during the ~1s before watchdog failover.
   - **Initial switch wait on silent server:** If an RTMP/SRT server accepts the socket without sending frames (no `IOException`), `switchToRtmpSource()` waits up to `bufferForPlaybackMs + 5000 ms` ([RtmpSourceSwitchHelper.kt](file:///Users/dmytroantonov/git/LifeStreamerV2/app/src/main/java/com/dimadesu/lifestreamer/ui/main/RtmpSourceSwitchHelper.kt#L273)) before switching to bitmap fallback (`ECONNREFUSED`/socket errors fail immediately).
   - **`1 fps` floor assumption:** `frameWatchdogTimeoutMs = 1_000L` assumes incoming video delivers at least 1 frame/sec (even when `requireAudioTrack = false`).
   - **Docs stale:** [rtmp-source-disconnect-detection.md](file:///Users/dmytroantonov/git/LifeStreamerV2/docs/rtmp-source-disconnect-detection.md) still reflects the pre-implementation proposal (mentions `500 ms` rebuffer, unimplemented `DefaultSurfaceProcessor` changes, and unresolved questions at the bottom).

### Recommendation before merging to `main`
1. **Run your device test pass** (foreground RTMP → background → notification Start → kill RTMP source → verify bitmap fallback + bitrate → restore RTMP source → notification Stop → foreground resume).
2. *(Optional, small fix)* Call `stopRtmpConnectionMonitoring()` + `rtmpRetryJob?.cancelAndJoin()` + release `currentRtmpPlayer` when switching from RTMP to **UVC**, **Screen**, or a **different RTMP index**.
3. *(Optional)* Update or remove [rtmp-source-disconnect-detection.md](file:///Users/dmytroantonov/git/LifeStreamerV2/docs/rtmp-source-disconnect-detection.md) so the docs match what was actually built.

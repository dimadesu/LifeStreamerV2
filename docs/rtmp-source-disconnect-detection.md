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
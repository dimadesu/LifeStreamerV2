# Smooth Video Source & Camera Switching Plan

## Problem Statement

Switching video camera sources (e.g., front <-> back camera, single camera <-> dual camera PiP, or camera <-> external/screen sources) currently exhibits three visual artifacts on both the local preview and the encoded stream:

1. **Shutdown Stutter & Redundant Session Reconfiguration**:
   - When tearing down a camera source in `VideoInput.setSource()`, calling `previousVideoSource.stopStream()` invokes `CameraController.removeTarget("stream")`, which submits a new repeating request for the remaining `"preview"` target to the camera that is about to be released.
   - Immediately after, `previousVideoSource.resetOutput()` calls `CameraController.removeOutput("stream")`, which calls `restartSessionUnsafe()` and attempts to **create a brand-new `CameraCaptureSession`** for `"preview"` right before `previousVideoSource.release()` closes the camera!
   - When `CameraSessionController.close()` closes the capture session, it calls `captureSession.close()` without first calling `captureSession.abortCaptures()`, causing the camera HAL to drain in-flight capture requests and stutter before shutting down.
2. **Double `CameraCaptureSession` Creation on New Camera Startup**:
   - During `VideoInput.setSource()`, when streaming is active, `newVideoSource.startStream()` is called *before* `sourceInternalFlow.emit(newVideoSource)`.
   - This opens the new `CameraDevice` and creates **CaptureSession #1** with only the `"stream"` surface.
   - Milliseconds later, `PreviewView` observes `sourceFlow`, requests a viewfinder surface, and calls `newVideoSource.setPreview(surface)` -> `CameraController.addOutput("preview")` -> `restartSessionUnsafe()`, which tears down CaptureSession #1 and creates **CaptureSession #2** with `["stream", "preview"]`.
3. **Preview Freeze on Last Frame**:
   - `PreviewView` only shows its black `coverView` overlay when switching to a non-previewable source. During camera-to-camera switches, `PreviewView` keeps displaying the frozen last frame of the previous camera until the new camera session is configured and rendering.
   - `coverView` is also hidden as soon as `startPreview()` returns, before the new camera has delivered its first frame.
4. **Encoded Stream Freeze on Source Switch**:
   - The `DefaultSurfaceProcessor` CFR render loop is currently only active for `MediaProjectionVideoSource`. During camera switches, no frames are pushed to the encoder while the old camera closes and the new camera initializes, causing the stream to freeze on the last frame until the new source produces frames.

---

## Implementation Plan

### Step 1: Clean & Immediate Old Camera Teardown
**Goal**: Stop the outgoing camera immediately without draining in-flight captures, submitting redundant repeating requests, or recreating capture sessions during teardown.

1. **Abort Captures Before Closing Session (`CameraSessionController.kt`)**:
   - In `CameraSessionController.close()`, call `captureSession.abortCaptures()` (wrapped in a `try/catch` for `CameraAccessException` / `IllegalStateException`) immediately before `captureSession.close()`.
2. **Skip Redundant Repeating Requests & Session Restarts on Teardown (`VideoInput.kt` & `CameraSource.kt` / `ConcurrentDualCameraSource.kt`)**:
   - In `VideoInput.setSource()`, when tearing down `previousVideoSource` directly (when switching between camera/processor-aware sources), release the source before detaching its surface from `processor`, and ensure `CameraSource.release()` closes the `CameraController` first so subsequent cleanup (`resetOutput()`) is a no-op on the camera session rather than triggering `removeTarget` / `restartSessionUnsafe()`.
   - In `ConcurrentDualCameraSource.release()`, release `primaryCameraSource` and `secondaryCameraSource` without first calling `stopStream()` + `resetOutput()` while their sessions are still active, and remove the redundant `delay(200)` in `ConcurrentDualCameraSource.release()` where `CameraDeviceController.close()` already awaits `CameraDevice.StateCallback.onClosed`.
3. **Remove Artificial Pre-Switch Delay (`PreviewViewModel.kt`)**:
   - Remove the hardcoded `delay(300)` in `PreviewViewModel.toggleCamera()` before `currentStreamer.setNextCameraId(application)` so camera switching starts immediately on user tap.

---

### Step 2: Single `CameraCaptureSession` Creation on Camera Switch (Option A)
**Goal**: Keep `CameraViewfinder` for direct preview rendering, but coordinate `VideoInput` and `PreviewView` so the new camera's preview surface is configured *before* `newVideoSource.startStream()` creates the `CameraCaptureSession`.

1. **Pre-Start Preview Preparation Hook**:
   - Introduce a suspend callback/hook or two-phase source transition on `VideoInput` (or `IPreviewableSource` binder) that allows `PreviewView` to stop the old preview, request the new viewfinder `Surface` for `newVideoSource`, and call `newVideoSource.setPreview(surface)` **before** `VideoInput.setSource()` calls `newVideoSource.startStream()`.
2. **Unified Session Startup**:
   - When `newVideoSource` starts, both `"stream"` and `"preview"` outputs are already registered in `CameraController.outputs`, and both targets are added before the capture session is created—resulting in a single `CameraCaptureSession` creation with `["stream", "preview"]` and zero session restarts.

---

### Step 3: Clean Black Preview Transition (`PreviewView`)
**Goal**: Immediately transition the preview to black when a source switch begins, and reveal the preview only after the new source produces its first frame.

1. **Show `coverView` Before Closing Outgoing Source**:
   - Notify `PreviewView` at the very beginning of `VideoInput.setSource()` (before stopping/releasing `previousVideoSource`) so `coverView.visibility = View.VISIBLE` immediately covers the outgoing camera's last frame with clean black.
2. **Reveal Preview on First Frame**:
   - Keep `coverView` visible across viewfinder surface creation and `startPreview()`, and hide `coverView` only when the first frame from the new source is captured/rendered (e.g., via a first-frame callback from `CameraSessionController` / `SurfaceProcessor`).

---

### Step 4: On-Demand Black Frame CFR Loop During Source Switch (`DefaultSurfaceProcessor` & `VideoInput`)
**Goal**: Without enabling the CFR loop permanently for normal camera streaming, temporarily run the CFR loop rendering black frames during a video source switch so the encoder receives a smooth, continuous frame cadence.

1. **Reset First-Frame State & Enable Transition Black Frames (`DefaultSurfaceProcessor.kt`)**:
   - Add a `setSourceSwitching(isSwitching: Boolean, fps: Int)` (or equivalent transition state) on `ISurfaceProcessorInternal` / `DefaultSurfaceProcessor`.
   - When `isSwitching = true` (at the start of `VideoInput.setSource()` while streaming):
     - Reset `hasFirstFrame = false` when the old input surface is removed so stale textures are never re-rendered.
     - Start the CFR render loop at the stream's configured FPS (if `cfrFps == 0` normally) and have `renderLatestFrame()` render black frames (`renderer.render(..., isMuted = true)`) with monotonically advancing timestamps while `!hasFirstFrame`.
   - When the new video source delivers its first frame to `DefaultSurfaceProcessor` (`onFrameAvailable` sets `hasFirstFrame = true`):
     - Automatically stop the temporary switch CFR loop (unless the new source explicitly configured persistent `cfrFps > 0`, such as `MediaProjectionVideoSource`) and resume normal event-driven camera frame rendering.


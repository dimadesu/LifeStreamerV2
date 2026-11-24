# Video Source Transition: Bitrate Drop & Post-Blur Blockiness Analysis & Plan

## 1. Problem Statement

When switching video sources (e.g., Camera $\leftrightarrow$ Camera, Camera $\leftrightarrow$ Bitmap, Camera $\leftrightarrow$ UVC, Camera $\leftrightarrow$ RTMP) while streaming, the OpenGL Gaussian blur transition smoothly hides intermediate teardown/setup glitches. However, during testing two side effects were observed:
1. **Bitrate Regulator Drop**: The adaptive bitrate regulator lowers the video bitrate during or immediately after a video source switch.
2. **Post-Transition Blockiness**: Right when the blur transition finishes and the sharp incoming video source appears, the encoded stream image looks noticeably blocky (macroblock/CTU quantization artifacts) for ~0.5–1.5 seconds before clearing up.

---

## 2. Root Cause Analysis

### 2.1 Why the Bitrate Regulator Lowers Bitrate During/After the Blur Transition

#### A. Low-Entropy Blurred Frames Starve `TransportBitrateEstimator`
During the blur transition (`300ms` minimum hold up to `1.5s+` when opening a new camera, UVC, or RTMP source), `DefaultSurfaceProcessor` feeds the video encoder a **heavily blurred, nearly static image**:
* Gaussian blur removes all high-frequency spatial detail, and inter-frame motion is near zero.
* Hardware H.264/HEVC encoders (especially in Android's default `BITRATE_MODE_VBR`) compress smooth, static blurred P-frames down to **100–400 kbps**, even when the encoder target bitrate is configured to 4–8 Mbps.
* `TransportBitrateEstimator` computes a time-scaled exponential moving average (EMA, `0.7` per-second retention) of actual `byteSentTotal` deltas on every regulator poll (every 200ms in Moblin SrtFight / RTMP, every 20ms in BelaBox).
* While the encoder outputs tiny blurred frames, `transportBitrateBps` decays downward and triggers the transport-bitrate ceiling clamp (`max(transportBitrate + min, 1.7 * transportBitrate)`):
  * `MoblinSrtFightBitrateRegulator` clamps `currentMaximumBitrate` unconditionally on every 200ms tick.
  * `BelaboxSrtBelaRegulator` clamps `curBitrate` on every tick.
  * `RtmpSendDurationBitrateRegulator` calls `capByTransportBitrate()` whenever `smoothFillRatio <= FILL_RATIO_INCREASE_THRESHOLD` (which is true during blur because very few bytes are written) or on any decrease branch.
* Once `1.7 * transportBitrateBps` drops below the running bitrate, the regulator pulls `currentMaximumBitrate` down, requiring several seconds to climb back up after the switch finishes.

#### B. Post-Transition Scene-Cut Packet Burst Triggers a False Congestion Cut
* While tiny blurred frames are being sent, packets-in-flight (`pktFlightSize` / `smoothPif` in SRT) and socket send duration (`smoothFillRatio` in RTMP) decay to near zero.
* The instant `stopTransitionInternal()` exits the blur and renders the sharp new video source (plus an IDR keyframe or large scene-cut frame), the encoder outputs a sudden burst of packets.
* Because `smoothPif` / `sendBufferSizeAvg` / `smoothFillRatio` decayed to a very low baseline during the quiet blur transition, this normal return-to-full-bitrate burst produces a transient spike (`fastPif - smoothPif` in SrtFight, `sendBufferSize > sendBufferSizeTh2` in BelaBox, or `fastFillRatio - smoothFillRatio` in RTMP), causing the regulator to cut the bitrate right as the new source appears.

---

### 2.2 Why the Encoded Image Looks Blocky Right When the Blur Ends

Even without a bitrate regulator cut, the hardware `MediaCodec` encoder produces a burst of blockiness on the first unblurred frames of the new source:

1. **Mid-GOP Scene Cut Encoded as a P-Frame**:
   * In `VideoCodecConfig`, the periodic I-frame interval (`gopDurationInS`) is typically `1.0s` or `2.0s`.
   * When `DefaultSurfaceProcessor.stopTransitionInternal()` switches from the blurred transition FBO to the sharp new video source, no sync frame (`MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME`) is requested from `MediaCodecEncoder`.
   * Unless a scheduled periodic I-frame lands on that exact frame, Android's hardware `MediaCodec` encodes the brand-new sharp scene as a **P-frame referencing the blurred frame**.
2. **Hardware Rate-Controller QP Spike**:
   * A sharp frame from a new video source shares zero motion vectors and zero high-frequency detail with a 360p Gaussian-blurred frame from the previous source. Every macroblock/CTU falls back to intra coding inside a **P-frame's much smaller bit budget**.
   * To stay within its P-frame bit budget, the hardware rate controller spikes the **Quantization Parameter (QP)** sky-high on that frame (and subsequent P-frames referencing it), resulting in heavy blockiness until the next scheduled I-frame arrives.
3. **Over-Aggressive Initial Blur Ramp**:
   * `OpenGlRenderer.captureTransitionFrame()` was running 3 initial ping-pong blur passes (`INITIAL_BLUR_PING_PONG_PASSES = 3`) on a 360p FBO and then running another 2-pass ping-pong blur on every single rendered transition frame (`renderTransitionFrame`). Within 250ms, the effective blur radius exceeded 75px (at 1080p equivalent), wiping out all spatial gradients immediately even on fast 300ms switches.

---

## 3. Comparison with Moblin (iOS)

Inspecting Moblin's git history (`inspiration/moblin`) confirmed that Moblin encountered the exact same issues after introducing `blurSceneSwitch`:

1. **Commit `27ff1418a` (*"Bitrate drop fix ideas, but not that great."*) & Commit `89027609e`**:
   * Moblin's author noticed the encoder output bitrate dropping during blurred scene switches and experimented with a gentler blur curve (`calcBlurRadius()`) and continuous zoom (`Blur & zoom`) plus higher `VideoToolbox` data rate limits.
   * For standard `.blur`, Moblin starts at a moderate blur radius (`15` at 1080p, i.e. `~2.8px` at 360p) and ramps linearly to `45` (at 1080p, `~8.4px` at 360p) over `2.0s`:
     ```swift
     private func calcBlurRadius() -> Float {
         if let latestSampleBufferTime {
             let offset = ContinuousClock.now - latestSampleBufferTime
             return 15 + min(Float(offset.seconds), 2) * 15
         } else {
             return 25
         }
     }
     ```
     On a fast `0.3s` switch, the blur radius only reaches `~19.5` at 1080p (`~3.6px` at 360p), retaining enough mid/low-frequency structure so the frame doesn't collapse to a uniform flat color immediately.
2. **Commit `9b58166a7` (*"Relaxed bitrate experiment for BELABOX algorithm."*)**:
   * Moblin added a 3-second `relaxedBitrate` window after camera attach (`Model.swift` / `AdaptiveBitrateSrtBelabox.swift`) that doubles `sendBufferSizeTh2` (`sendBufferSizeTh2 *= 2`) so the post-switch packet burst doesn't trigger a fast bitrate cut.
3. **Commit `390f5eb8d` & `Media.swift`**:
   * Moblin only updates `srtTransportBitrate` once per second (`handle1sTimer`), and disables `limitByTransportBitrate` when using CBR (`limitAdaptiveBitrateByTransportBitrate: stream.rateControl != .cbr`).
4. **Why iOS `VideoToolbox` Had Less Post-Blur Blockiness Than Android `MediaCodec`**:
   * Apple's `VTCompressionSession` automatically detects scene cuts and inserts an I-frame when the blur ends. Android hardware `MediaCodec` encoders do not auto-insert keyframes on scene cuts unless `PARAMETER_KEY_REQUEST_SYNC_FRAME` (`requestKeyFrame()`) is explicitly called.

---

## 4. Implementation Plan

### Step 1: Match Moblin's Gentler Progressive Blur Curve in `OpenGlRenderer` & `DefaultSurfaceProcessor`
* In `OpenGlRenderer.captureTransitionFrame()`, reduce the initial blur to a single gentle pass (`INITIAL_BLUR_PING_PONG_PASSES = 1` with radius `1.0f`) matching Moblin's starting radius of `15` at 1080p (`~2.8px` at 360p).
* In `DefaultSurfaceProcessor.renderTransitionFrame()`, step the iterative FBO blur at ~10 Hz (every 100ms, up to 2.0s) with gentle increments (`0.45f..0.85f`) instead of every single 33ms frame, so fast 300ms switches stay moderately blurred (retaining spatial gradients) while longer 1–2s switches progressively reach a deep blur matching Moblin's `15 -> 45` curve.

### Step 2: Request an Encoder Keyframe (`requestKeyFrame()`) When the Blur Transition Ends
* Add an `onRequestKeyFrame: () -> Unit = {}` callback to `SurfaceDescriptor` and `SurfaceOutput` (wired from `EncodingPipelineOutput` to `videoEncoder.requestKeyFrame()`, and wrapped in `MediaCodecEncoder.requestKeyFrame()` with safe exception handling if the codec is not in `STARTED` state).
* In `DefaultSurfaceProcessor.onFrameAvailable()`, when `isTransitioning` is true and the first unblurred frame of the incoming primary source is rendered (`stopTransitionInternal()` followed by `renderLatestFrame()`), invoke `requestKeyFrame()` on all active streaming encoder `SurfaceOutput`s.
* This forces Android `MediaCodec` to encode the first sharp frame of the new source as a clean IDR/I-frame with a full keyframe bit budget instead of a high-QP P-frame predicted from the blurred frame.

### Step 3: Add Source-Switch Grace Period to Bitrate Regulators (`TransportBitrateEstimator` + SRT/RTMP Regulators)
* Add `isTransitioningFlow: StateFlow<Boolean>` to `ISurfaceProcessor` / `DefaultSurfaceProcessor` (true while the blur transition loop is active, false when `stopTransitionInternal()` runs).
* When `isTransitioningFlow` is true, or for a short grace window (`2.0s` / `SOURCE_SWITCH_GRACE_NS`) after a transition starts/ends:
  1. **Prevent `TransportBitrateEstimator` downward decay**: Add `update(byteSentTotal: Long, allowDecrease: Boolean = true)` to `TransportBitrateEstimator`. During the grace window (`allowDecrease = false`), `previousByteSentTotal` and `previousTransportUpdateTimeNs` still advance on every tick (so no stale byte delta accumulates), and upward estimates are accepted, but `bitrateBps` is not allowed to decay downward when the encoder outputs low-bitrate blurred frames.
  2. **Relax transient spike cuts during the post-switch keyframe burst**:
     * In `BelaboxSrtBelaRegulator`, double `sendBufferSizeTh2` (`sendBufferSizeTh2 *= 2.0`, matching Moblin's `if stats.relaxed { sendBufferSizeTh2 *= 2 }`) and `sendBufferSizeTh1 *= 1.5` during the grace window.
     * In `MoblinSrtFightBitrateRegulator`, relax the transient `pifSpikeDiff` lazy/proportional cut threshold during the grace window so the clean I-frame on transition end does not cause a false PIF dip.
     * In `RtmpSendDurationBitrateRegulator`, skip the `fastFillRatio - smoothFillRatio` lazy ceiling cut and transient spike throttle during the grace window (while still respecting hard congestion / dropped messages / emergency stall).

### Step 4: Unit Tests & Verification
* Add unit tests in `BitrateRegulatorTest.kt` and `DynamicBitrateRegulatorControllerTest.kt` verifying that:
  * Low byte-sent deltas during a source-switch grace window do not pull `TransportBitrateEstimator` or the SRT/RTMP regulator ceilings down.
  * A post-transition keyframe burst during the grace window does not trigger a false lazy decrease.
* Add/update unit tests in `StreamPack` (`VideoInputTest.kt`) verifying that transition end triggers keyframe requests on encoder outputs.

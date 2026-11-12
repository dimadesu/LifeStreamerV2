package com.dimadesu.lifestreamer.camera

import android.graphics.RectF

/**
 * Defines the layout configuration for compositing multiple video input streams into a single frame.
 */
enum class CompositionMode(val displayName: String) {
    /**
     * Single input source (full screen).
     */
    SINGLE("Single Camera"),

    /**
     * Picture-in-Picture: Primary camera full screen, secondary camera in top-right overlay.
     */
    PIP_TOP_RIGHT("PiP (Top Right)"),

    /**
     * Picture-in-Picture: Primary camera full screen, secondary camera in top-left overlay.
     */
    PIP_TOP_LEFT("PiP (Top Left)"),

    /**
     * Picture-in-Picture: Primary camera full screen, secondary camera in bottom-right overlay.
     */
    PIP_BOTTOM_RIGHT("PiP (Bottom Right)"),

    /**
     * Picture-in-Picture: Primary camera full screen, secondary camera in bottom-left overlay.
     */
    PIP_BOTTOM_LEFT("PiP (Bottom Left)"),

    /**
     * Split screen: Primary camera on left 50%, secondary camera on right 50%.
     */
    SIDE_BY_SIDE("Split (Side-by-Side)"),

    /**
     * Split screen: Primary camera on top 50%, secondary camera on bottom 50%.
     */
    TOP_BOTTOM("Split (Top-Bottom)");

    /**
     * Returns normalized target rectangles [left, top, right, bottom] in range [0..1]
     * for each input given the total count of inputs.
     */
    fun getNormalizedRects(inputCount: Int): List<RectF> {
        if (inputCount <= 1 || this == SINGLE) {
            return List(inputCount) { RectF(0f, 0f, 1f, 1f) }
        }

        return when (this) {
            PIP_TOP_RIGHT -> listOf(
                RectF(0f, 0f, 1f, 1f),
                RectF(0.65f, 0.05f, 0.95f, 0.35f)
            )
            PIP_TOP_LEFT -> listOf(
                RectF(0f, 0f, 1f, 1f),
                RectF(0.05f, 0.05f, 0.35f, 0.35f)
            )
            PIP_BOTTOM_RIGHT -> listOf(
                RectF(0f, 0f, 1f, 1f),
                RectF(0.65f, 0.65f, 0.95f, 0.95f)
            )
            PIP_BOTTOM_LEFT -> listOf(
                RectF(0f, 0f, 1f, 1f),
                RectF(0.05f, 0.65f, 0.35f, 0.95f)
            )
            SIDE_BY_SIDE -> listOf(
                RectF(0f, 0f, 0.5f, 1f),
                RectF(0.5f, 0f, 1f, 1f)
            )
            TOP_BOTTOM -> listOf(
                RectF(0f, 0f, 1f, 0.5f),
                RectF(0f, 0.5f, 1f, 1f)
            )
        }
    }
}


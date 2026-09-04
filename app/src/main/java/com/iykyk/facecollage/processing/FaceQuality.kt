package com.iykyk.facecollage.processing

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.sqrt

object FaceQuality {

    /**
     * Calculates an approximate sharpness score.
     *
     * Higher = sharper face.
     */
    fun calculateSharpness(
        bitmap: Bitmap
    ): Float {

        if (
            bitmap.width < 3 ||
            bitmap.height < 3
        ) {
            return 0f
        }

        val width = bitmap.width
        val height = bitmap.height

        val pixels =
            IntArray(width * height)

        bitmap.getPixels(
            pixels,
            0,
            width,
            0,
            0,
            width,
            height
        )

        var sum = 0.0
        var sumSquared = 0.0
        var count = 0

        /*
         * Simple edge-strength calculation.
         *
         * Strong edges generally mean a sharper image.
         */

        for (y in 1 until height - 1 step 2) {

            for (x in 1 until width - 1 step 2) {

                val center =
                    grayscale(
                        pixels[y * width + x]
                    )

                val left =
                    grayscale(
                        pixels[y * width + (x - 1)]
                    )

                val right =
                    grayscale(
                        pixels[y * width + (x + 1)]
                    )

                val top =
                    grayscale(
                        pixels[(y - 1) * width + x]
                    )

                val bottom =
                    grayscale(
                        pixels[(y + 1) * width + x]
                    )

                val laplacian =
                    4f * center -
                            left -
                            right -
                            top -
                            bottom

                sum += laplacian
                sumSquared +=
                    laplacian * laplacian

                count++
            }
        }

        if (count == 0) {
            return 0f
        }

        val mean =
            sum / count

        val variance =
            (sumSquared / count) -
                    (mean * mean)

        return sqrt(
            variance.coerceAtLeast(0.0)
        ).toFloat()
    }

    private fun grayscale(
        color: Int
    ): Float {

        val r =
            (color shr 16) and 0xFF

        val g =
            (color shr 8) and 0xFF

        val b =
            color and 0xFF

        return (
                0.299f * r +
                        0.587f * g +
                        0.114f * b
                )
    }

    /**
     * 1.0 = almost frontal
     * 0.0 = extreme angle
     */
    fun angleScore(
        yaw: Float,
        pitch: Float,
        roll: Float,
        settings: FaceProcessingSettings = FaceProcessingSettings()
    ): Float {

        val yawScore =
            1f -
                    (
                            abs(yaw) / settings.maxYawForScore
                            ).coerceIn(0f, 1f)

        val pitchScore =
            1f -
                    (
                            abs(pitch) / settings.maxPitchForScore
                            ).coerceIn(0f, 1f)

        val rollScore =
            1f -
                    (
                            abs(roll) / settings.maxRollForScore
                            ).coerceIn(0f, 1f)

        return (
                yawScore +
                        pitchScore +
                        rollScore
                ) / 3f
    }

    /**
     * Prefers faces away from the extreme edges
     * of the video.
     */
    fun centerScore(
        centerX: Float,
        centerY: Float,
        frameWidth: Int,
        frameHeight: Int
    ): Float {

        val frameCenterX =
            frameWidth / 2f

        val frameCenterY =
            frameHeight / 2f

        val dx =
            abs(centerX - frameCenterX) /
                    frameCenterX.coerceAtLeast(1f)

        val dy =
            abs(centerY - frameCenterY) /
                    frameCenterY.coerceAtLeast(1f)

        return (
                1f -
                        ((dx + dy) / 2f)
                ).coerceIn(0f, 1f)
    }
}
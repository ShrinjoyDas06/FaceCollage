package com.iykyk.facecollage.processing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark

/**
 * Converts an ML Kit face box into a consistent 112x112 face image.
 *
 * MobileFaceNet is much happier when the eyes/nose occupy approximately the
 * same locations in every input. We use three landmarks for an affine warp.
 */
object FaceAlignment {

    private const val OUTPUT_SIZE = 112

    fun align(
        frame: Bitmap,
        face: Face,
        settings: FaceProcessingSettings
    ): Bitmap? {
        val sourceLeftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
        val sourceRightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position
        val sourceNose = face.getLandmark(FaceLandmark.NOSE_BASE)?.position

        if (sourceLeftEye == null || sourceRightEye == null || sourceNose == null) {
            return fallbackCrop(frame, face)
        }

        val srcLeft = sourceLeftEye.x
        val srcRight = sourceRightEye.x
        val srcNoseX = sourceNose.x
        val srcNoseY = sourceNose.y

        // We keep the transform anchored around landmarks, while the detector
        // box supplies a little tolerance around the face for hair/chin motion.
        if (!isUsable(face.boundingBox, frame.width, frame.height, srcLeft, srcRight, srcNoseX, srcNoseY)) {
            return fallbackCrop(frame, face)
        }

        val matrix = Matrix()
        val source = floatArrayOf(
            srcLeft, sourceLeftEye.y,
            srcRight, sourceRightEye.y,
            srcNoseX, srcNoseY
        )

        val destination = floatArrayOf(
            settings.alignmentLeftEyeX, settings.alignmentEyeY,
            settings.alignmentRightEyeX, settings.alignmentEyeY,
            settings.alignmentNoseX, settings.alignmentNoseY
        )

        if (!matrix.setPolyToPoly(source, 0, destination, 0, 3)) {
            return fallbackCrop(frame, face)
        }

        val output = Bitmap.createBitmap(
            OUTPUT_SIZE,
            OUTPUT_SIZE,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(frame, matrix, paint)
        return output
    }

    private fun fallbackCrop(
        frame: Bitmap,
        face: Face
    ): Bitmap? {
        val bounds = face.boundingBox
        val left = bounds.left.coerceAtLeast(0)
        val top = bounds.top.coerceAtLeast(0)
        val right = bounds.right.coerceAtMost(frame.width)
        val bottom = bounds.bottom.coerceAtMost(frame.height)
        val width = right - left
        val height = bottom - top

        if (width <= 1 || height <= 1) return null

        val crop = Bitmap.createBitmap(frame, left, top, width, height)
        return Bitmap.createScaledBitmap(crop, OUTPUT_SIZE, OUTPUT_SIZE, true).also {
            if (it !== crop) crop.recycle()
        }
    }

    private fun isUsable(
        bounds: android.graphics.Rect,
        frameWidth: Int,
        frameHeight: Int,
        leftEyeX: Float,
        rightEyeX: Float,
        noseX: Float,
        noseY: Float
    ): Boolean {
        return bounds.width() > 1 &&
                bounds.height() > 1 &&
                leftEyeX in 0f..frameWidth.toFloat() &&
                rightEyeX in 0f..frameWidth.toFloat() &&
                noseX in 0f..frameWidth.toFloat() &&
                noseY in 0f..frameHeight.toFloat()
    }
}

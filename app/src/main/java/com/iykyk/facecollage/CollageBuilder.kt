package com.iykyk.facecollage

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import kotlin.math.ceil
import kotlin.math.sqrt

object CollageBuilder {

    fun createGridCollage(
        faces: List<Bitmap>,
        cellSize: Int = 300
    ): Bitmap {

        if (faces.isEmpty()) {
            throw IllegalArgumentException(
                "Cannot create collage with no faces."
            )
        }

        val columns =
            ceil(
                sqrt(faces.size.toDouble())
            ).toInt()

        val rows =
            ceil(
                faces.size.toDouble() /
                        columns
            ).toInt()

        val result =
            Bitmap.createBitmap(
                columns * cellSize,
                rows * cellSize,
                Bitmap.Config.ARGB_8888
            )

        val canvas =
            Canvas(result)

        canvas.drawColor(Color.WHITE)

        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG)

        for (
            (index, face)
            in faces.withIndex()
        ) {

            val column =
                index % columns

            val row =
                index / columns

            val destination =
                Rect(
                    column * cellSize,
                    row * cellSize,
                    (column + 1) * cellSize,
                    (row + 1) * cellSize
                )

            canvas.drawBitmap(
                face,
                null,
                destination,
                paint
            )
        }

        return result
    }
}
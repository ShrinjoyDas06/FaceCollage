package com.iykyk.facecollage

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sqrt

object CollageBuilder {

    fun createGridCollage(
        faces: List<Bitmap>,
        cellSize: Int = 300
    ): Bitmap {

        if (faces.isEmpty()) {

            return Bitmap.createBitmap(
                1,
                1,
                Bitmap.Config.ARGB_8888
            )
        }

        val columns =
            ceil(
                sqrt(
                    faces.size.toDouble()
                )
            ).toInt()

        val rows =
            ceil(
                faces.size.toDouble() /
                        columns
            ).toInt()

        val width =
            columns * cellSize

        val height =
            rows * cellSize

        val collage =
            Bitmap.createBitmap(
                width,
                height,
                Bitmap.Config.ARGB_8888
            )

        val canvas =
            Canvas(collage)

        canvas.drawColor(
            Color.WHITE
        )

        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG)

        for ((index, face) in faces.withIndex()) {

            val column =
                index % columns

            val row =
                index / columns

            val left =
                column * cellSize

            val top =
                row * cellSize

            // Preserve aspect ratio instead of stretching
            // every face into a square.
            val scale =
                min(
                    cellSize.toFloat() /
                            face.width,

                    cellSize.toFloat() /
                            face.height
                )

            val scaledWidth =
                (face.width * scale)
                    .toInt()

            val scaledHeight =
                (face.height * scale)
                    .toInt()

            val scaled =
                Bitmap.createScaledBitmap(
                    face,
                    scaledWidth,
                    scaledHeight,
                    true
                )

            val x =
                left +
                        (cellSize - scaledWidth) / 2

            val y =
                top +
                        (cellSize - scaledHeight) / 2

            canvas.drawBitmap(
                scaled,
                x.toFloat(),
                y.toFloat(),
                paint
            )

            scaled.recycle()
        }

        return collage
    }
}
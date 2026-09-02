package com.iykyk.facecollage

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.ceil
import kotlin.math.sqrt

object CollageBuilder {
    fun createGridCollage(faces: List<Bitmap>, cellSize: Int = 300): Bitmap {
        if (faces.isEmpty()) return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

        val columns = ceil(sqrt(faces.size.toDouble())).toInt()
        val rows = ceil(faces.size.toDouble() / columns).toInt()

        val collageWidth = columns * cellSize
        val collageHeight = rows * cellSize

        val result = Bitmap.createBitmap(collageWidth, collageHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)

        val paint = Paint().apply { isAntiAlias = true }

        for ((index, face) in faces.withIndex()) {
            val col = index % columns
            val row = index / columns

            val scaled = Bitmap.createScaledBitmap(face, cellSize, cellSize, true)
            val left = (col * cellSize).toFloat()
            val top = (row * cellSize).toFloat()

            canvas.drawBitmap(scaled, left, top, paint)
        }

        return result
    }
}
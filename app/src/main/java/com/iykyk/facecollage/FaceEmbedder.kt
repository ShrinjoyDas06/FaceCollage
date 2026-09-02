package com.iykyk.facecollage

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class FaceEmbedder(context: Context) {
    private var interpreter: Interpreter? = null

    init {
        val assetFileDescriptor = context.assets.openFd("mobilefacenet.tflite")
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        interpreter = Interpreter(modelBuffer)
    }

    fun getEmbedding(faceBitmap: Bitmap): FloatArray {
        val resized = Bitmap.createScaledBitmap(faceBitmap, 112, 112, true)
        val imgData = ByteBuffer.allocateDirect(1 * 112 * 112 * 3 * 4)
        imgData.order(ByteOrder.nativeOrder())

        val intValues = IntArray(112 * 112)
        resized.getPixels(intValues, 0, resized.width, 0, 0, resized.width, resized.height)

        for (pixel in intValues) {
            val r = (pixel shr 16 and 0xFF)
            val g = (pixel shr 8 and 0xFF)
            val b = (pixel and 0xFF)
            
            // Normalize pixels to [-1.0, 1.0]
            imgData.putFloat((r - 127.5f) / 128.0f)
            imgData.putFloat((g - 127.5f) / 128.0f)
            imgData.putFloat((b - 127.5f) / 128.0f)
        }

        val output = Array(1) { FloatArray(128) }
        interpreter?.run(imgData, output)
        return output[0]
    }
}
package com.iykyk.facecollage

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.sqrt

class FaceEmbedder(context: Context) {

    private val interpreter: Interpreter

    init {

        val assetFileDescriptor =
            context.assets.openFd(
                "mobilefacenet.tflite"
            )

        val inputStream =
            FileInputStream(
                assetFileDescriptor.fileDescriptor
            )

        val fileChannel =
            inputStream.channel

        val modelBuffer =
            fileChannel.map(
                FileChannel.MapMode.READ_ONLY,
                assetFileDescriptor.startOffset,
                assetFileDescriptor.declaredLength
            )

        interpreter =
            Interpreter(modelBuffer)

        inputStream.close()
    }

    fun getEmbedding(faceBitmap: Bitmap): FloatArray {

        val resized =
            Bitmap.createScaledBitmap(
                faceBitmap,
                112,
                112,
                true
            )

        val inputBuffer =
            ByteBuffer.allocateDirect(
                112 * 112 * 3 * 4
            ).apply {
                order(ByteOrder.nativeOrder())
            }

        val pixels =
            IntArray(112 * 112)

        resized.getPixels(
            pixels,
            0,
            112,
            0,
            0,
            112,
            112
        )

        for (pixel in pixels) {

            val r =
                (pixel shr 16) and 0xFF

            val g =
                (pixel shr 8) and 0xFF

            val b =
                pixel and 0xFF

            // MobileFaceNet models commonly use [-1, 1].
            inputBuffer.putFloat(
                (r - 127.5f) / 127.5f
            )

            inputBuffer.putFloat(
                (g - 127.5f) / 127.5f
            )

            inputBuffer.putFloat(
                (b - 127.5f) / 127.5f
            )
        }

        inputBuffer.rewind()

        val output =
            Array(1) {
                FloatArray(128)
            }

        interpreter.run(
            inputBuffer,
            output
        )

        // L2 normalize the embedding.
        return normalize(output[0])
    }

    private fun normalize(
        vector: FloatArray
    ): FloatArray {

        var sum = 0f

        for (value in vector) {
            sum += value * value
        }

        val magnitude =
            sqrt(sum)

        if (magnitude < 1e-6f) {
            return vector
        }

        return FloatArray(vector.size) { index ->
            vector[index] / magnitude
        }
    }

    fun close() {
        interpreter.close()
    }
}
package com.iykyk.facecollage.processing

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.sqrt

class FaceEmbedder(
    context: Context
) : AutoCloseable {

    private val interpreter: Interpreter

    init {

        val descriptor =
            context.assets.openFd("mobilefacenet.tflite")

        FileInputStream(
            descriptor.fileDescriptor
        ).use { input ->

            val channel = input.channel

            val buffer = channel.map(
                FileChannel.MapMode.READ_ONLY,
                descriptor.startOffset,
                descriptor.declaredLength
            )

            interpreter = Interpreter(buffer)
        }
    }

    fun getEmbedding(
        faceBitmap: Bitmap
    ): FloatArray {

        val resized =
            Bitmap.createScaledBitmap(
                faceBitmap,
                112,
                112,
                true
            )

        val input = ByteBuffer.allocateDirect(
            112 * 112 * 3 * 4
        )

        input.order(ByteOrder.nativeOrder())

        val pixels = IntArray(112 * 112)

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

            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF

            input.putFloat(
                (r - 127.5f) / 128f
            )

            input.putFloat(
                (g - 127.5f) / 128f
            )

            input.putFloat(
                (b - 127.5f) / 128f
            )
        }

        input.rewind()

        val output =
            Array(1) {
                FloatArray(192)
            }

        interpreter.run(
            input,
            output
        )

        return normalize(output[0])
    }

    private fun normalize(
        vector: FloatArray
    ): FloatArray {

        var sum = 0f

        for (value in vector) {
            sum += value * value
        }

        val magnitude = sqrt(sum)

        if (magnitude == 0f) {
            return vector
        }

        return FloatArray(vector.size) { index ->
            vector[index] / magnitude
        }
    }

    override fun close() {
        interpreter.close()
    }
}
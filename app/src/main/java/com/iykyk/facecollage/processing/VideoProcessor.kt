package com.iykyk.facecollage.processing

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.iykyk.facecollage.CollageBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class VideoProcessor(
    private val context: Context
) : AutoCloseable {

    private val embedder =
        FaceEmbedder(context)

    private val detector =
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(
                    FaceDetectorOptions.PERFORMANCE_MODE_FAST
                )
                .build()
        )

    suspend fun processVideo(
        file: File,
        onProgress: (Int) -> Unit,
        onLog: (String) -> Unit
    ): Bitmap = withContext(Dispatchers.Default) {

        val retriever =
            MediaMetadataRetriever()

        try {

            retriever.setDataSource(
                file.absolutePath
            )

            val durationMs =
                retriever
                    .extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_DURATION
                    )
                    ?.toLong()
                    ?: 0L

            if (durationMs <= 0L) {
                throw IllegalStateException(
                    "Could not determine video duration."
                )
            }

            onLog(
                "Video duration: ${durationMs}ms"
            )

            // ~2 frames/second
            val intervalUs = 500_000L

            val frameTimes =
                (0L until durationMs * 1000L step intervalUs)
                    .toList()

            onLog(
                "Sampling ${frameTimes.size} frames."
            )

            val detectedFaces =
                mutableListOf<FaceSample>()

            for (
                (index, timeUs)
                in frameTimes.withIndex()
            ) {

                val frame =
                    retriever.getFrameAtTime(
                        timeUs,
                        MediaMetadataRetriever
                            .OPTION_CLOSEST
                    )

                if (frame == null) {
                    onLog(
                        "Frame $index: unavailable"
                    )
                    continue
                }

                try {

                    val image =
                        InputImage.fromBitmap(
                            frame,
                            0
                        )

                    val faces =
                        Tasks.await(
                            detector.process(image)
                        )

                    onLog(
                        "Frame $index: ${faces.size} face(s)"
                    )

                    for (face in faces) {

                        val bounds =
                            face.boundingBox

                        val left =
                            bounds.left.coerceAtLeast(0)

                        val top =
                            bounds.top.coerceAtLeast(0)

                        val right =
                            bounds.right
                                .coerceAtMost(frame.width)

                        val bottom =
                            bounds.bottom
                                .coerceAtMost(frame.height)

                        val width =
                            right - left

                        val height =
                            bottom - top

                        if (
                            width <= 0 ||
                            height <= 0
                        ) {
                            continue
                        }

                        val crop =
                            Bitmap.createBitmap(
                                frame,
                                left,
                                top,
                                width,
                                height
                            )

                        val embedding =
                            embedder.getEmbedding(
                                crop
                            )

                        detectedFaces.add(
                            FaceSample(
                                bitmap = crop,
                                area = width * height,
                                embedding = embedding
                            )
                        )
                    }

                } finally {
                    frame.recycle()
                }

                onProgress(
                    ((index + 1) * 80) /
                            frameTimes.size
                )
            }

            onLog(
                "Detected ${detectedFaces.size} face crops."
            )

            if (detectedFaces.isEmpty()) {
                throw IllegalStateException(
                    "No faces were detected in the video."
                )
            }

            onProgress(85)

            val uniqueFaces =
                FaceClusterer(
                    similarityThreshold = 0.65f
                ).clusterFaces(
                    detectedFaces
                )

            onLog(
                "Unique people: ${uniqueFaces.size}"
            )

            onProgress(90)

            val collage =
                CollageBuilder.createGridCollage(
                    uniqueFaces
                )

            onProgress(100)

            collage

        } finally {

            retriever.release()
        }
    }

    override fun close() {
        detector.close()
        embedder.close()
    }
}
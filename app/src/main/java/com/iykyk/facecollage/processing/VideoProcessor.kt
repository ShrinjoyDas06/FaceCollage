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
                    FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE
                )
                .setLandmarkMode(
                    FaceDetectorOptions.LANDMARK_MODE_ALL
                )
                .setClassificationMode(
                    FaceDetectorOptions.CLASSIFICATION_MODE_ALL
                )
                .enableTracking()
                .setMinFaceSize(0.08f)
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

            if (!file.exists()) {
                throw IllegalArgumentException(
                    "Video file does not exist: ${file.absolutePath}"
                )
            }

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

            /*
             * Sample approximately 3 frames per second.
             *
             * 20 second video ≈ 60 frames.
             *
             * This gives us enough appearances of each person
             * to recognise the same person repeatedly.
             */

            val intervalUs =
                333_333L

            val frameTimes =
                (0L until durationMs * 1000L step intervalUs)
                    .toList()

            onLog(
                "Sampling ${frameTimes.size} frames."
            )

            val detectedFaces =
                mutableListOf<FaceSample>()

            var totalRawFaces = 0
            var rejectedSmall = 0
            var rejectedEdge = 0
            var rejectedQuality = 0

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

                    totalRawFaces += faces.size

                    onLog(
                        "Frame $index: ${faces.size} face(s)"
                    )

                    for (
                        (faceIndex, face)
                        in faces.withIndex()
                    ) {

                        val bounds =
                            face.boundingBox

                        val left =
                            bounds.left
                                .coerceAtLeast(0)

                        val top =
                            bounds.top
                                .coerceAtLeast(0)

                        val right =
                            bounds.right
                                .coerceAtMost(
                                    frame.width
                                )

                        val bottom =
                            bounds.bottom
                                .coerceAtMost(
                                    frame.height
                                )

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

                        val area =
                            width * height

                        val frameArea =
                            frame.width *
                                    frame.height

                        val relativeArea =
                            area.toFloat() /
                                    frameArea.toFloat()

                        /*
                         * Reject extremely tiny faces.
                         *
                         * This is one of the main protections
                         * against background faces.
                         */

                        if (
                            relativeArea < 0.008f
                        ) {

                            rejectedSmall++

                            onLog(
                                "  Face $faceIndex rejected: too small (${(relativeArea * 100).toInt()}%)"
                            )

                            continue
                        }

                        /*
                         * Reject faces touching the extreme
                         * edges of the frame.
                         *
                         * These are often partial/background faces.
                         */

                        val edgeMarginX =
                            frame.width * 0.01f

                        val edgeMarginY =
                            frame.height * 0.01f

                        if (
                            bounds.left <= edgeMarginX ||
                            bounds.top <= edgeMarginY ||
                            bounds.right >=
                            frame.width - edgeMarginX ||
                            bounds.bottom >=
                            frame.height - edgeMarginY
                        ) {

                            rejectedEdge++

                            onLog(
                                "  Face $faceIndex rejected: too close to edge"
                            )

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

                        /*
                         * Calculate image quality BEFORE embedding.
                         */

                        val sharpness =
                            FaceQuality.calculateSharpness(
                                crop
                            )

                        /*
                         * Reject extremely blurry faces.
                         *
                         * We keep the threshold relatively low
                         * because phone cameras can produce
                         * different sharpness values.
                         */

                        if (
                            sharpness < 25f
                        ) {

                            rejectedQuality++

                            onLog(
                                "  Face $faceIndex rejected: blurry (${
                                    sharpness.toInt()
                                })"
                            )

                            crop.recycle()

                            continue
                        }

                        /*
                         * Generate MobileFaceNet embedding.
                         *
                         * Your model outputs 192 dimensions.
                         */

                        val embedding =
                            embedder.getEmbedding(
                                crop
                            )

                        val centerX =
                            bounds.centerX().toFloat()

                        val centerY =
                            bounds.centerY().toFloat()

                        val trackingId =
                            face.trackingId

                        onLog(
                            "  Face $faceIndex accepted: " +
                                    "size=${(relativeArea * 100).toInt()}%, " +
                                    "sharp=${sharpness.toInt()}, " +
                                    "yaw=${face.headEulerAngleY.toInt()}, " +
                                    "track=$trackingId"
                        )

                        detectedFaces.add(
                            FaceSample(
                                bitmap = crop,
                                area = area,
                                frameWidth = frame.width,
                                frameHeight = frame.height,
                                embedding = embedding,
                                yaw = face.headEulerAngleY,
                                pitch = face.headEulerAngleX,
                                roll = face.headEulerAngleZ,
                                sharpness = sharpness,
                                centerX = centerX,
                                centerY = centerY,
                                timestampUs = timeUs,
                                leftEyeOpen =
                                    face.leftEyeOpenProbability,
                                rightEyeOpen =
                                    face.rightEyeOpenProbability
                            )
                        )
                    }

                } finally {

                    frame.recycle()
                }

                onProgress(
                    ((index + 1) * 75) /
                            frameTimes.size
                )
            }

            onLog(
                "Raw detected faces: $totalRawFaces"
            )

            onLog(
                "Rejected tiny faces: $rejectedSmall"
            )

            onLog(
                "Rejected edge faces: $rejectedEdge"
            )

            onLog(
                "Rejected blurry faces: $rejectedQuality"
            )

            onLog(
                "Usable face samples: ${detectedFaces.size}"
            )

            if (detectedFaces.isEmpty()) {

                throw IllegalStateException(
                    "No usable faces were detected in the video."
                )
            }

            onProgress(80)

            /*
             * Now comes the important part:
             *
             * ALL appearances of the same person are grouped
             * into ONE PersonCluster.
             */

            onLog(
                "Recognising repeated people..."
            )

            val uniqueFaces =
                FaceClusterer(
                    similarityThreshold = 0.65f
                ).clusterFaces(
                    detectedFaces
                )

            onLog(
                "Unique people: ${uniqueFaces.size}"
            )

            /*
             * This is the actual desired behaviour:
             *
             * If you appear 20 times,
             * only ONE of your best frames goes to the collage.
             */

            onLog(
                "Selecting one best frame per person..."
            )

            uniqueFaces.forEachIndexed { index, _ ->

                onLog(
                    "Person ${index + 1}: best frame selected"
                )
            }

            onProgress(90)

            if (uniqueFaces.isEmpty()) {

                throw IllegalStateException(
                    "No unique people could be identified."
                )
            }

            onLog(
                "Generating collage from ${uniqueFaces.size} people..."
            )

            val collage =
                CollageBuilder.createGridCollage(
                    uniqueFaces
                )

            onProgress(100)

            onLog(
                "Collage contains ${uniqueFaces.size} unique people."
            )

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
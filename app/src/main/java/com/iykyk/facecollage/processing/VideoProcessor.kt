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
import kotlin.math.abs

class VideoProcessor(
    private val context: Context
) : AutoCloseable {

    private val embedder = FaceEmbedder(context)

    suspend fun processVideo(
        file: File,
        settings: FaceProcessingSettings = FaceProcessingSettings(),
        onProgress: (Int) -> Unit,
        onLog: (String) -> Unit
    ): Bitmap = withContext(Dispatchers.Default) {
        val currentSettings = settings.normalized()
        val detector = createDetector(currentSettings)
        val retriever = MediaMetadataRetriever()

        try {
            if (!file.exists()) {
                throw IllegalArgumentException(
                    "Video file does not exist: ${file.absolutePath}"
                )
            }

            retriever.setDataSource(file.absolutePath)

            val durationMs =
                retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION
                )?.toLong() ?: 0L

            if (durationMs <= 0L) {
                throw IllegalStateException("Could not determine video duration.")
            }

            val intervalUs = 1_000_000L / currentSettings.samplingFps
            val frameTimes = (0L until durationMs * 1000L step intervalUs).toList()

            onLog("Video duration: ${durationMs}ms")
            onLog("Sampling ${frameTimes.size} frames (${currentSettings.samplingFps} FPS).")
            onLog("Settings: area >= ${format(currentSettings.minFaceAreaPercent)}%, " +
                    "edge margin ${format(currentSettings.edgeMarginPercent)}%, " +
                    "max vertical angle ${format(currentSettings.maxVerticalAngle)}°, " +
                    "max horizontal angle ${format(currentSettings.maxHorizontalAngle)}°, " +
                    "sharpness >= ${format(currentSettings.minSharpness)}, " +
                    "similarity >= ${format(currentSettings.similarityThreshold)}")

            val detectedFaces = mutableListOf<FaceSample>()
            var totalRawFaces = 0
            var rejectedSmall = 0
            var rejectedEdge = 0
            var rejectedVerticalAngle = 0
            var rejectedHorizontalAngle = 0
            var rejectedQuality = 0
            var rejectedInvalid = 0
            var alignedFaces = 0
            var fallbackFaces = 0

            for ((index, timeUs) in frameTimes.withIndex()) {
                val frame = retriever.getFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST
                )

                if (frame == null) {
                    onLog("Frame $index: unavailable")
                    continue
                }

                try {
                    val faces = Tasks.await(
                        detector.process(InputImage.fromBitmap(frame, 0))
                    )

                    totalRawFaces += faces.size
                    onLog("Frame $index: ${faces.size} face(s)")

                    for ((faceIndex, face) in faces.withIndex()) {
                        val bounds = face.boundingBox
                        val left = bounds.left.coerceAtLeast(0)
                        val top = bounds.top.coerceAtLeast(0)
                        val right = bounds.right.coerceAtMost(frame.width)
                        val bottom = bounds.bottom.coerceAtMost(frame.height)
                        val width = right - left
                        val height = bottom - top

                        if (width <= 1 || height <= 1) {
                            rejectedInvalid++
                            continue
                        }

                        val area = width * height
                        val frameArea = frame.width * frame.height
                        val relativeArea = area.toFloat() / frameArea.toFloat()
                        val areaPercent = relativeArea * 100f

                        if (areaPercent < currentSettings.minFaceAreaPercent) {
                            rejectedSmall++
                            onLog("  Face $faceIndex rejected: too small (${format(areaPercent)}%)")
                            continue
                        }

                        val edgeMarginX = frame.width * currentSettings.edgeMarginPercent / 100f
                        val edgeMarginY = frame.height * currentSettings.edgeMarginPercent / 100f
                        val tooCloseToEdge =
                            currentSettings.edgeMarginPercent > 0f &&
                                    (bounds.left <= edgeMarginX ||
                                            bounds.top <= edgeMarginY ||
                                            bounds.right >= frame.width - edgeMarginX ||
                                            bounds.bottom >= frame.height - edgeMarginY)

                        if (tooCloseToEdge) {
                            rejectedEdge++
                            onLog("  Face $faceIndex rejected: too close to edge")
                            continue
                        }

                        // Reject excessive looking up/down before expensive operations (crop/align/embedding)
                        val verticalAngle = abs(face.headEulerAngleX)
                        if (verticalAngle > currentSettings.maxVerticalAngle) {
                            rejectedVerticalAngle++
                            onLog("  Face $faceIndex rejected: vertical angle too steep (${format(verticalAngle)}°)")
                            continue
                        }

                        // Reject excessive looking left/right before expensive operations (crop/align/embedding)
                        val horizontalAngle = abs(face.headEulerAngleY)
                        if (horizontalAngle > currentSettings.maxHorizontalAngle) {
                            rejectedHorizontalAngle++
                            onLog("  Face $faceIndex rejected: horizontal angle too steep (${format(horizontalAngle)}°)")
                            continue
                        }

                        val cropForQuality = Bitmap.createBitmap(
                            frame,
                            left,
                            top,
                            width,
                            height
                        )

                        val sharpness = try {
                            FaceQuality.calculateSharpness(cropForQuality)
                        } finally {
                            cropForQuality.recycle()
                        }

                        // Quality is now an optional cutoff, defaulting to 0.
                        // Even without this cutoff, sharpness is still used later
                        // to choose the best representative frame.
                        if (sharpness < currentSettings.minSharpness) {
                            rejectedQuality++
                            onLog("  Face $faceIndex rejected: low sharpness (${format(sharpness)})")
                            continue
                        }

                        val aligned = FaceAlignment.align(frame, face, currentSettings)
                        if (aligned == null) {
                            rejectedInvalid++
                            onLog("  Face $faceIndex rejected: alignment failed")
                            continue
                        }

                        val landmarksPresent =
                            face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.LEFT_EYE) != null &&
                                    face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.RIGHT_EYE) != null &&
                                    face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.NOSE_BASE) != null

                        if (landmarksPresent) alignedFaces++ else fallbackFaces++

                        val embedding = try {
                            embedder.getEmbedding(aligned)
                        } catch (e: Exception) {
                            aligned.recycle()
                            throw e
                        }

                        onLog(
                            "  Face $faceIndex accepted: size=${format(areaPercent)}%, " +
                                    "sharp=${format(sharpness)}, yaw=${face.headEulerAngleY.toInt()}, " +
                                    "pitch=${face.headEulerAngleX.toInt()}, " +
                                    "track=${face.trackingId}, " +
                                    if (landmarksPresent) "aligned" else "fallback"
                        )

                        detectedFaces.add(
                            FaceSample(
                                bitmap = aligned,
                                area = area,
                                frameWidth = frame.width,
                                frameHeight = frame.height,
                                embedding = embedding,
                                yaw = face.headEulerAngleY,
                                pitch = face.headEulerAngleX,
                                roll = face.headEulerAngleZ,
                                sharpness = sharpness,
                                centerX = bounds.centerX().toFloat(),
                                centerY = bounds.centerY().toFloat(),
                                timestampUs = timeUs,
                                leftEyeOpen = face.leftEyeOpenProbability,
                                rightEyeOpen = face.rightEyeOpenProbability
                            )
                        )
                    }
                } finally {
                    frame.recycle()
                }

                onProgress(
                    if (frameTimes.isEmpty()) 0
                    else ((index + 1) * 75) / frameTimes.size
                )
            }

            onLog("Raw detected faces: $totalRawFaces")
            onLog("Rejected tiny faces: $rejectedSmall")
            onLog("Rejected edge faces: $rejectedEdge")
            onLog("Rejected extreme vertical angle faces: $rejectedVerticalAngle")
            onLog("Rejected extreme horizontal angle faces: $rejectedHorizontalAngle")
            onLog("Rejected blurry/low-quality faces: $rejectedQuality")
            onLog("Rejected invalid/alignment faces: $rejectedInvalid")
            onLog("Aligned samples: $alignedFaces; fallback crops: $fallbackFaces")
            onLog("Usable face samples: ${detectedFaces.size}")

            if (detectedFaces.isEmpty()) {
                throw IllegalStateException("No usable faces were detected in the video.")
            }

            onProgress(80)
            onLog("Recognising repeated people...")

            val uniqueFaces = FaceClusterer(currentSettings).clusterFaces(detectedFaces)
            onLog("Unique people: ${uniqueFaces.size}")
            onLog("Selecting one best frame per person...")

            uniqueFaces.forEachIndexed { index, _ ->
                onLog("Person ${index + 1}: best frame selected")
            }

            onProgress(90)

            if (uniqueFaces.isEmpty()) {
                throw IllegalStateException("No unique people could be identified.")
            }

            onLog("Building collage...")
            val collage = CollageBuilder.createGridCollage(uniqueFaces)
            onProgress(100)
            onLog("Collage complete.")
            collage
        } finally {
            detector.close()
            retriever.release()
        }
    }

    private fun createDetector(settings: FaceProcessingSettings) =
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .enableTracking()
                .setMinFaceSize(settings.minDetectorFaceSize)
                .build()
        )

    private fun format(value: Float): String =
        "%.2f".format(java.util.Locale.US, value)

    override fun close() {
        embedder.close()
    }
}
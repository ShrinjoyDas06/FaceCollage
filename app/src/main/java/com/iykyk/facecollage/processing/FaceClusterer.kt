package com.iykyk.facecollage.processing

import android.graphics.Bitmap
import kotlin.math.sqrt

data class FaceSample(
    val bitmap: Bitmap,
    val area: Int,
    val frameWidth: Int,
    val frameHeight: Int,
    val embedding: FloatArray,
    val yaw: Float,
    val pitch: Float,
    val roll: Float,
    val sharpness: Float,
    val centerX: Float,
    val centerY: Float,
    val timestampUs: Long,
    val leftEyeOpen: Float?,
    val rightEyeOpen: Float?
)

private data class PersonCluster(
    val samples: MutableList<FaceSample> = mutableListOf()
) {
    fun bestSample(settings: FaceProcessingSettings): FaceSample =
        samples.maxBy { representativeScore(it, settings) }

    private fun representativeScore(
        sample: FaceSample,
        settings: FaceProcessingSettings
    ): Float {
        val frameArea =
            (sample.frameWidth * sample.frameHeight)
                .toFloat()
                .coerceAtLeast(1f)

        val relativeFaceSize = sample.area.toFloat() / frameArea

        val sizeScore =
            (relativeFaceSize * 100f / settings.bestFrameSizeTargetPercent)
                .coerceIn(0f, 1f)

        val sharpnessScore =
            (sample.sharpness / settings.bestFrameSharpnessSaturation)
                .coerceIn(0f, 1f)

        val angleScore =
            FaceQuality.angleScore(
                yaw = sample.yaw,
                pitch = sample.pitch,
                roll = sample.roll,
                settings = settings
            )

        val centerScore =
            FaceQuality.centerScore(
                centerX = sample.centerX,
                centerY = sample.centerY,
                frameWidth = sample.frameWidth,
                frameHeight = sample.frameHeight
            )

        val eyeScore =
            if (sample.leftEyeOpen != null && sample.rightEyeOpen != null) {
                ((sample.leftEyeOpen + sample.rightEyeOpen) / 2f).coerceIn(0f, 1f)
            } else {
                0.5f
            }

        return (
                sizeScore * settings.sizeWeight +
                        sharpnessScore * settings.sharpnessWeight +
                        angleScore * settings.angleWeight +
                        centerScore * settings.centerWeight +
                        eyeScore * settings.eyeWeight
                )
    }
}

class FaceClusterer(
    private val settings: FaceProcessingSettings = FaceProcessingSettings()
) {
    private val normalizedSettings = settings.normalized()

    fun clusterFaces(faces: List<FaceSample>): List<Bitmap> {
        if (faces.isEmpty()) return emptyList()

        val clusters = mutableListOf<PersonCluster>()

        for (face in faces) {
            var bestCluster: PersonCluster? = null
            var bestSimilarity = -1f

            for (cluster in clusters) {
                val similarity = cluster.samples.maxOfOrNull { sample ->
                    cosineSimilarity(face.embedding, sample.embedding)
                } ?: 0f

                if (similarity > bestSimilarity) {
                    bestSimilarity = similarity
                    bestCluster = cluster
                }
            }

            if (bestCluster != null && bestSimilarity >= normalizedSettings.similarityThreshold) {
                bestCluster.samples.add(face)
            } else {
                clusters.add(PersonCluster(mutableListOf(face)))
            }
        }

        return clusters
            .sortedByDescending { cluster -> clusterScore(cluster) }
            .map { cluster -> cluster.bestSample(normalizedSettings).bitmap }
    }

    private fun clusterScore(cluster: PersonCluster): Float {
        val best = cluster.bestSample(normalizedSettings)

        val frameArea =
            (best.frameWidth * best.frameHeight).toFloat().coerceAtLeast(1f)

        val sizeScore =
            (best.area.toFloat() / frameArea * 100f /
                    normalizedSettings.bestFrameSizeTargetPercent)
                .coerceIn(0f, 1f)

        val qualityScore =
            (best.sharpness / normalizedSettings.bestFrameSharpnessSaturation)
                .coerceIn(0f, 1f)

        val appearanceScore =
            (cluster.samples.size.toFloat() /
                    normalizedSettings.clusterAppearanceSaturation)
                .coerceIn(0f, 1f)

        return (
                appearanceScore * normalizedSettings.clusterAppearanceWeight +
                        sizeScore * normalizedSettings.clusterSizeWeight +
                        qualityScore * normalizedSettings.clusterSharpnessWeight
                )
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f

        var dot = 0f
        var normA = 0f
        var normB = 0f

        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        if (normA == 0f || normB == 0f) return 0f

        return dot / (sqrt(normA) * sqrt(normB))
    }
}

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

    val bestSample: FaceSample
        get() = samples.maxBy { sample ->
            representativeScore(sample)
        }

    private fun representativeScore(
        sample: FaceSample
    ): Float {

        val frameArea =
            (sample.frameWidth * sample.frameHeight)
                .toFloat()
                .coerceAtLeast(1f)

        val relativeFaceSize =
            sample.area.toFloat() / frameArea

        /*
         * Prefer a face that is:
         *
         * 1. reasonably large
         * 2. sharp
         * 3. frontal
         * 4. close to the center
         * 5. has open eyes
         */

        val sizeScore =
            (relativeFaceSize / 0.20f)
                .coerceIn(0f, 1f)

        val sharpnessScore =
            (sample.sharpness / 800f)
                .coerceIn(0f, 1f)

        val angleScore =
            FaceQuality.angleScore(
                yaw = sample.yaw,
                pitch = sample.pitch,
                roll = sample.roll
            )

        val centerScore =
            FaceQuality.centerScore(
                centerX = sample.centerX,
                centerY = sample.centerY,
                frameWidth = sample.frameWidth,
                frameHeight = sample.frameHeight
            )

        val eyeScore =
            if (
                sample.leftEyeOpen != null &&
                sample.rightEyeOpen != null
            ) {
                (
                    sample.leftEyeOpen +
                            sample.rightEyeOpen
                ) / 2f
            } else {
                0.5f
            }

        return (
                sizeScore * 0.35f +
                        sharpnessScore * 0.25f +
                        angleScore * 0.20f +
                        centerScore * 0.10f +
                        eyeScore * 0.10f
                )
    }
}

class FaceClusterer(
    private val similarityThreshold: Float = 0.65f
) {

    fun clusterFaces(
        faces: List<FaceSample>
    ): List<Bitmap> {

        if (faces.isEmpty()) {
            return emptyList()
        }

        val clusters =
            mutableListOf<PersonCluster>()

        /*
         * Process every detected face.
         *
         * If it looks like an already-known person,
         * put it in that person's cluster.
         *
         * Otherwise create a new person.
         */

        for (face in faces) {

            var bestCluster: PersonCluster? = null
            var bestSimilarity = -1f

            for (cluster in clusters) {

                /*
                 * Compare against ALL samples of the cluster.
                 *
                 * This is important because the same person
                 * can look different from different angles.
                 */

                val similarity =
                    cluster.samples.maxOfOrNull { sample ->
                        cosineSimilarity(
                            face.embedding,
                            sample.embedding
                        )
                    } ?: 0f

                if (similarity > bestSimilarity) {
                    bestSimilarity = similarity
                    bestCluster = cluster
                }
            }

            if (
                bestCluster != null &&
                bestSimilarity >= similarityThreshold
            ) {

                bestCluster.samples.add(face)

            } else {

                clusters.add(
                    PersonCluster(
                        mutableListOf(face)
                    )
                )
            }
        }

        /*
         * Sort people by importance.
         *
         * People who appear many times and have good
         * quality faces should come first.
         */

        val sortedClusters =
            clusters.sortedByDescending { cluster ->

                clusterScore(cluster)
            }

        return sortedClusters.map { cluster ->

            cluster.bestSample.bitmap
        }
    }

    private fun clusterScore(
        cluster: PersonCluster
    ): Float {

        val appearanceScore =
            (
                    cluster.samples.size.toFloat() / 10f
                    ).coerceIn(0f, 1f)

        val best =
            cluster.bestSample

        val frameArea =
            (
                    best.frameWidth *
                            best.frameHeight
                    ).toFloat()
                    .coerceAtLeast(1f)

        val sizeScore =
            (
                    best.area.toFloat() /
                            frameArea /
                            0.20f
                    ).coerceIn(0f, 1f)

        val qualityScore =
            (
                    best.sharpness / 800f
                    ).coerceIn(0f, 1f)

        return (
                appearanceScore * 0.30f +
                        sizeScore * 0.40f +
                        qualityScore * 0.30f
                )
    }

    private fun cosineSimilarity(
        a: FloatArray,
        b: FloatArray
    ): Float {

        if (a.size != b.size) {
            return 0f
        }

        var dot = 0f
        var normA = 0f
        var normB = 0f

        for (i in a.indices) {

            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        if (
            normA == 0f ||
            normB == 0f
        ) {
            return 0f
        }

        return dot /
                (sqrt(normA) * sqrt(normB))
    }
}
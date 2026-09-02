package com.iykyk.facecollage

import android.graphics.Bitmap
import kotlin.math.sqrt

class FaceClusterer(
    private val similarityThreshold: Float = 0.65f
) {

    private data class Cluster(
        var representative: Bitmap,
        var embedding: FloatArray,
        var largestArea: Int
    )

    fun clusterFaces(
        detectedFaces: List<DetectedFace>
    ): List<Bitmap> {

        val clusters =
            mutableListOf<Cluster>()

        for (face in detectedFaces) {

            var bestCluster: Cluster? = null
            var bestSimilarity = -1f

            for (cluster in clusters) {

                val similarity =
                    cosineSimilarity(
                        face.embedding,
                        cluster.embedding
                    )

                if (similarity > bestSimilarity) {

                    bestSimilarity =
                        similarity

                    bestCluster =
                        cluster
                }
            }

            if (
                bestCluster != null &&
                bestSimilarity >= similarityThreshold
            ) {

                // Same person.
                //
                // Keep the largest/highest-quality
                // detected face as the representative.
                if (face.area > bestCluster.largestArea) {

                    bestCluster.representative =
                        face.bitmap

                    bestCluster.embedding =
                        face.embedding

                    bestCluster.largestArea =
                        face.area
                }

            } else {

                // New person.
                clusters.add(
                    Cluster(
                        representative = face.bitmap,
                        embedding = face.embedding,
                        largestArea = face.area
                    )
                )
            }
        }

        return clusters.map {
            it.representative
        }
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

            dot +=
                a[i] * b[i]

            normA +=
                a[i] * a[i]

            normB +=
                b[i] * b[i]
        }

        if (
            normA <= 0f ||
            normB <= 0f
        ) {
            return 0f
        }

        return dot /
                (sqrt(normA) * sqrt(normB))
    }
}
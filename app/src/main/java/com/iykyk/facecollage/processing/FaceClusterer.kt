package com.iykyk.facecollage.processing

import android.graphics.Bitmap
import kotlin.math.sqrt

data class FaceSample(
    val bitmap: Bitmap,
    val area: Int,
    val embedding: FloatArray
)

private data class PersonCluster(
    var representativeFace: Bitmap,
    var representativeArea: Int,
    var embedding: FloatArray
)

class FaceClusterer(
    private val similarityThreshold: Float = 0.65f
) {

    fun clusterFaces(
        faces: List<FaceSample>
    ): List<Bitmap> {

        val clusters =
            mutableListOf<PersonCluster>()

        for (face in faces) {

            var bestCluster: PersonCluster? = null
            var bestSimilarity = -1f

            for (cluster in clusters) {

                val similarity =
                    cosineSimilarity(
                        face.embedding,
                        cluster.embedding
                    )

                if (similarity > bestSimilarity) {
                    bestSimilarity = similarity
                    bestCluster = cluster
                }
            }

            if (
                bestCluster != null &&
                bestSimilarity >= similarityThreshold
            ) {

                if (
                    face.area >
                    bestCluster.representativeArea
                ) {

                    bestCluster.representativeFace =
                        face.bitmap

                    bestCluster.representativeArea =
                        face.area
                }

            } else {

                clusters.add(
                    PersonCluster(
                        representativeFace = face.bitmap,
                        representativeArea = face.area,
                        embedding = face.embedding
                    )
                )
            }
        }

        return clusters.map {
            it.representativeFace
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
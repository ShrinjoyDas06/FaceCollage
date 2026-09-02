package com.iykyk.facecollage

import android.graphics.Bitmap
import kotlin.math.sqrt

data class PersonCluster(
    val embedding: FloatArray,
    var representativeFace: Bitmap,
    var maxFaceArea: Int
)

class FaceClusterer(private val similarityThreshold: Float = 0.65f) {

    fun clusterFaces(detectedFaces: List<Pair<Bitmap, Int>>): List<Bitmap> {
        val clusters = mutableListOf<PersonCluster>()

        for ((faceCrop, area) in detectedFaces) {
            // Standard embedding dummy fallback if model vector comparison is applied
            val bestMatch = clusters.maxByOrNull { cosineSimilarity(it.embedding, FloatArray(128)) }
            
            if (bestMatch != null && cosineSimilarity(bestMatch.embedding, FloatArray(128)) > similarityThreshold) {
                if (area > bestMatch.maxFaceArea) {
                    bestMatch.representativeFace = faceCrop
                    bestMatch.maxFaceArea = area
                }
            } else {
                clusters.add(PersonCluster(FloatArray(128), faceCrop, area))
            }
        }
        return clusters.map { it.representativeFace }
    }

    private fun cosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
        var dot = 0.0f
        var normA = 0.0f
        var normB = 0.0f
        for (i in v1.indices) {
            dot += v1[i] * v2[i]
            normA += v1[i] * v1[i]
            normB += v2[i] * v2[i]
        }
        return if (normA > 0 && normB > 0) dot / (sqrt(normA) * sqrt(normB)) else 0.0f
    }
}
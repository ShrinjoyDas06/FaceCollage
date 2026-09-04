package com.iykyk.facecollage.processing

/** Runtime-tunable face processing parameters. */
data class FaceProcessingSettings(
    val samplingFps: Int = 3,
    val minDetectorFaceSize: Float = 0.05f,
    val minFaceAreaPercent: Float = 0.30f,
    val edgeMarginPercent: Float = 0.0f,
    val minSharpness: Float = 0f,
    val similarityThreshold: Float = 0.58f,
    val maxVerticalAngle: Float = 25f,
    val maxHorizontalAngle: Float = 30f,
    val maxYawForScore: Float = 45f,
    val maxPitchForScore: Float = 35f,
    val maxRollForScore: Float = 45f,
    val alignmentEyeY: Float = 43f,
    val alignmentLeftEyeX: Float = 35f,
    val alignmentRightEyeX: Float = 77f,
    val alignmentNoseX: Float = 56f,
    val alignmentNoseY: Float = 70f,
    val bestFrameSizeTargetPercent: Float = 20f,
    val bestFrameSharpnessSaturation: Float = 800f,
    val clusterAppearanceSaturation: Float = 10f,
    val sizeWeight: Float = 0.35f,
    val sharpnessWeight: Float = 0.25f,
    val angleWeight: Float = 0.20f,
    val centerWeight: Float = 0.10f,
    val eyeWeight: Float = 0.10f,
    val clusterAppearanceWeight: Float = 0.30f,
    val clusterSizeWeight: Float = 0.40f,
    val clusterSharpnessWeight: Float = 0.30f,
) {
    fun normalized(): FaceProcessingSettings {
        val sum = sizeWeight + sharpnessWeight + angleWeight + centerWeight + eyeWeight
        val clusterSum = clusterAppearanceWeight + clusterSizeWeight + clusterSharpnessWeight
        return copy(
            samplingFps = samplingFps.coerceIn(1, 6),
            minDetectorFaceSize = minDetectorFaceSize.coerceIn(0.02f, 0.20f),
            minFaceAreaPercent = minFaceAreaPercent.coerceIn(0.05f, 5f),
            edgeMarginPercent = edgeMarginPercent.coerceIn(0f, 5f),
            minSharpness = minSharpness.coerceIn(0f, 150f),
            similarityThreshold = similarityThreshold.coerceIn(0.40f, 0.90f),
            maxYawForScore = maxYawForScore.coerceIn(5f, 90f),
            maxPitchForScore = maxPitchForScore.coerceIn(5f, 90f),
            maxRollForScore = maxRollForScore.coerceIn(5f, 90f),
            bestFrameSizeTargetPercent = bestFrameSizeTargetPercent.coerceIn(1f, 50f),
            bestFrameSharpnessSaturation = bestFrameSharpnessSaturation.coerceIn(50f, 3000f),
            clusterAppearanceSaturation = clusterAppearanceSaturation.coerceIn(1f, 50f),
            sizeWeight = sizeWeight / sum.coerceAtLeast(0.0001f),
            sharpnessWeight = sharpnessWeight / sum.coerceAtLeast(0.0001f),
            angleWeight = angleWeight / sum.coerceAtLeast(0.0001f),
            centerWeight = centerWeight / sum.coerceAtLeast(0.0001f),
            eyeWeight = eyeWeight / sum.coerceAtLeast(0.0001f),
            clusterAppearanceWeight = clusterAppearanceWeight / clusterSum.coerceAtLeast(0.0001f),
            clusterSizeWeight = clusterSizeWeight / clusterSum.coerceAtLeast(0.0001f),
            clusterSharpnessWeight = clusterSharpnessWeight / clusterSum.coerceAtLeast(0.0001f)
        )
    }

    companion object {
        fun preset(preset: FaceProcessingPreset): FaceProcessingSettings = when (preset) {
            FaceProcessingPreset.NORMAL -> FaceProcessingSettings()
            FaceProcessingPreset.AGGRESSIVE -> FaceProcessingSettings(
                samplingFps = 3,
                minDetectorFaceSize = 0.07f,
                minFaceAreaPercent = 0.60f,
                edgeMarginPercent = 0.5f,
                minSharpness = 19f,
                similarityThreshold = 0.63f,
                maxVerticalAngle = 20f,
                maxHorizontalAngle = 25f,
            )
            FaceProcessingPreset.LENIENT -> FaceProcessingSettings(
                samplingFps = 4,
                minDetectorFaceSize = 0.035f,
                minFaceAreaPercent = 0.15f,
                edgeMarginPercent = 0f,
                minSharpness = 0f,
                similarityThreshold = 0.54f,
                maxVerticalAngle = 35f,
                maxHorizontalAngle = 45f,
            )
        }
    }
}

enum class FaceProcessingPreset {
    NORMAL,
    AGGRESSIVE,
    LENIENT
}

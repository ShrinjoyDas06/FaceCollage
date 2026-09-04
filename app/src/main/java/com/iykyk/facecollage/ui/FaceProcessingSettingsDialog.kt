package com.iykyk.facecollage.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.iykyk.facecollage.processing.FaceProcessingPreset
import com.iykyk.facecollage.processing.FaceProcessingSettings
import kotlin.math.roundToInt

@Composable
fun FaceProcessingSettingsDialog(
    initialSettings: FaceProcessingSettings,
    onApply: (FaceProcessingSettings) -> Unit,
    onDismiss: () -> Unit
) {
    var settings by remember(initialSettings) { mutableStateOf(initialSettings) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Text(
                    text = "Face Processing Settings",
                    style = MaterialTheme.typography.headlineSmall
                )

                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Use a preset first, then fine-tune individual values.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(Modifier.height(18.dp))

                Text("Presets", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PresetButton(
                        label = "Normal",
                        modifier = Modifier.weight(1f),
                        selected = settings == FaceProcessingSettings.preset(FaceProcessingPreset.NORMAL),
                        onClick = { settings = FaceProcessingSettings.preset(FaceProcessingPreset.NORMAL) }
                    )
                    PresetButton(
                        label = "Aggressive",
                        modifier = Modifier.weight(1f),
                        selected = settings == FaceProcessingSettings.preset(FaceProcessingPreset.AGGRESSIVE),
                        onClick = { settings = FaceProcessingSettings.preset(FaceProcessingPreset.AGGRESSIVE) }
                    )
                    PresetButton(
                        label = "Lenient",
                        modifier = Modifier.weight(1f),
                        selected = settings == FaceProcessingSettings.preset(FaceProcessingPreset.LENIENT),
                        onClick = { settings = FaceProcessingSettings.preset(FaceProcessingPreset.LENIENT) }
                    )
                }

                Spacer(Modifier.height(20.dp))

                Text("Detection & filtering", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))

                SettingSlider(
                    label = "Sampling rate",
                    valueText = "${settings.samplingFps} fps",
                    value = settings.samplingFps.toFloat(),
                    range = 1f..6f,
                    steps = 4,
                    onValueChange = { settings = settings.copy(samplingFps = it.roundToInt()) }
                )

                SettingSlider(
                    label = "Detector minimum face size",
                    valueText = "${percent(settings.minDetectorFaceSize)}% of image width",
                    value = settings.minDetectorFaceSize * 100f,
                    range = 2f..20f,
                    steps = 17,
                    onValueChange = { settings = settings.copy(minDetectorFaceSize = it / 100f) }
                )

                SettingSlider(
                    label = "Minimum face area",
                    valueText = "${format(settings.minFaceAreaPercent)}% of frame",
                    value = settings.minFaceAreaPercent,
                    range = 0.05f..2f,
                    steps = 38,
                    onValueChange = { settings = settings.copy(minFaceAreaPercent = it) }
                )

                SettingSlider(
                    label = "Edge margin",
                    valueText = "${format(settings.edgeMarginPercent)}%",
                    value = settings.edgeMarginPercent,
                    range = 0f..5f,
                    steps = 50,
                    onValueChange = { settings = settings.copy(edgeMarginPercent = it) }
                )

                SettingSlider(
                    label = "Minimum sharpness",
                    valueText = if (settings.minSharpness <= 0f) "Off" else format(settings.minSharpness),
                    value = settings.minSharpness,
                    range = 0f..150f,
                    steps = 30,
                    onValueChange = { settings = settings.copy(minSharpness = it) }
                )

                Spacer(Modifier.height(10.dp))
                Text("Alignment", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "These advanced sliders control the landmark target used before MobileFaceNet. Leave them at the default values unless you are tuning the model input.",
                    style = MaterialTheme.typography.bodySmall
                )

                SettingSlider("Left-eye X", format(settings.alignmentLeftEyeX), settings.alignmentLeftEyeX, 20f..50f, 29) {
                    settings = settings.copy(alignmentLeftEyeX = it)
                }
                SettingSlider("Right-eye X", format(settings.alignmentRightEyeX), settings.alignmentRightEyeX, 62f..92f, 29) {
                    settings = settings.copy(alignmentRightEyeX = it)
                }
                SettingSlider("Eye Y", format(settings.alignmentEyeY), settings.alignmentEyeY, 25f..60f, 34) {
                    settings = settings.copy(alignmentEyeY = it)
                }
                SettingSlider("Nose X", format(settings.alignmentNoseX), settings.alignmentNoseX, 45f..67f, 21) {
                    settings = settings.copy(alignmentNoseX = it)
                }
                SettingSlider("Nose Y", format(settings.alignmentNoseY), settings.alignmentNoseY, 55f..90f, 34) {
                    settings = settings.copy(alignmentNoseY = it)
                }

                Spacer(Modifier.height(10.dp))
                Text("Recognition", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))

                SettingSlider("Yaw score limit", "${format(settings.maxYawForScore)}°", settings.maxYawForScore, 5f..90f, 85) {
                    settings = settings.copy(maxYawForScore = it)
                }
                SettingSlider("Pitch score limit", "${format(settings.maxPitchForScore)}°", settings.maxPitchForScore, 5f..90f, 85) {
                    settings = settings.copy(maxPitchForScore = it)
                }
                SettingSlider("Roll score limit", "${format(settings.maxRollForScore)}°", settings.maxRollForScore, 5f..90f, 85) {
                    settings = settings.copy(maxRollForScore = it)
                }

                SettingSlider(
                    label = "Same-person similarity threshold",
                    valueText = format(settings.similarityThreshold),
                    value = settings.similarityThreshold,
                    range = 0.40f..0.90f,
                    steps = 49,
                    onValueChange = { settings = settings.copy(similarityThreshold = it) }
                )

                SettingSlider(
                    label = "Representative face size target",
                    valueText = "${format(settings.bestFrameSizeTargetPercent)}% of frame",
                    value = settings.bestFrameSizeTargetPercent,
                    range = 1f..50f,
                    steps = 48,
                    onValueChange = { settings = settings.copy(bestFrameSizeTargetPercent = it) }
                )

                SettingSlider(
                    label = "Sharpness score saturation",
                    valueText = format(settings.bestFrameSharpnessSaturation),
                    value = settings.bestFrameSharpnessSaturation,
                    range = 50f..3000f,
                    steps = 59,
                    onValueChange = { settings = settings.copy(bestFrameSharpnessSaturation = it) }
                )

                SettingSlider(
                    label = "Appearance-count saturation",
                    valueText = "${format(settings.clusterAppearanceSaturation)} samples",
                    value = settings.clusterAppearanceSaturation,
                    range = 1f..50f,
                    steps = 48,
                    onValueChange = { settings = settings.copy(clusterAppearanceSaturation = it) }
                )

                Spacer(Modifier.height(10.dp))
                Text("Best-frame weights", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "These weights only decide which accepted frame represents each person. They do not reject faces.",
                    style = MaterialTheme.typography.bodySmall
                )

                SettingSlider("Size weight", format(settings.sizeWeight), settings.sizeWeight, 0f..1f) {
                    settings = settings.copy(sizeWeight = it)
                }
                SettingSlider("Sharpness weight", format(settings.sharpnessWeight), settings.sharpnessWeight, 0f..1f) {
                    settings = settings.copy(sharpnessWeight = it)
                }
                SettingSlider("Angle weight", format(settings.angleWeight), settings.angleWeight, 0f..1f) {
                    settings = settings.copy(angleWeight = it)
                }
                SettingSlider("Center weight", format(settings.centerWeight), settings.centerWeight, 0f..1f) {
                    settings = settings.copy(centerWeight = it)
                }
                SettingSlider("Eye-open weight", format(settings.eyeWeight), settings.eyeWeight, 0f..1f) {
                    settings = settings.copy(eyeWeight = it)
                }

                Spacer(Modifier.height(10.dp))
                Text("Cluster ranking weights", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))

                SettingSlider("Appearance weight", format(settings.clusterAppearanceWeight), settings.clusterAppearanceWeight, 0f..1f) {
                    settings = settings.copy(clusterAppearanceWeight = it)
                }
                SettingSlider("Size weight", format(settings.clusterSizeWeight), settings.clusterSizeWeight, 0f..1f) {
                    settings = settings.copy(clusterSizeWeight = it)
                }
                SettingSlider("Sharpness weight", format(settings.clusterSharpnessWeight), settings.clusterSharpnessWeight, 0f..1f) {
                    settings = settings.copy(clusterSharpnessWeight = it)
                }

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            onApply(settings.normalized())
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Apply")
                    }
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun PresetButton(
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(label) }
    }
}

@Composable
private fun SettingSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(label, modifier = Modifier.weight(1f))
            Text(valueText)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(4.dp))
    }
}

private fun format(value: Float): String = "%.2f".format(java.util.Locale.US, value)
private fun percent(value: Float): String = "%.1f".format(java.util.Locale.US, value * 100f)

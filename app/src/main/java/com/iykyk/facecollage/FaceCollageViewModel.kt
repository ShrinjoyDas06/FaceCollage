package com.iykyk.facecollage

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.iykyk.facecollage.processing.FaceProcessingPreset
import com.iykyk.facecollage.processing.FaceProcessingSettings
import com.iykyk.facecollage.processing.VideoProcessor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

enum class AppState {
    READY,
    RECORDING,
    PROCESSING,
    RESULT
}

enum class ProcessingOutcome {
    NONE,
    PASSED,
    FAILED
}

class FaceCollageViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val _appState = MutableStateFlow(AppState.READY)
    val appState: StateFlow<AppState> = _appState.asStateFlow()

    private val _cameraFlipped = MutableStateFlow(false)
    val cameraFlipped: StateFlow<Boolean> = _cameraFlipped.asStateFlow()

    private val _remainingSeconds = MutableStateFlow(20)
    val remainingSeconds: StateFlow<Int> = _remainingSeconds.asStateFlow()

    private val _processingOutcome = MutableStateFlow(ProcessingOutcome.NONE)
    val processingOutcome: StateFlow<ProcessingOutcome> = _processingOutcome.asStateFlow()

    private val _progress = MutableStateFlow(0)
    val progress: StateFlow<Int> = _progress.asStateFlow()

    private val _collage = MutableStateFlow<Bitmap?>(null)
    val collage: StateFlow<Bitmap?> = _collage.asStateFlow()

    private val _console = MutableStateFlow(
        "Face Collage Console\n" +
                "---------------------\n" +
                "Ready."
    )
    val console: StateFlow<String> = _console.asStateFlow()

    private val _processingSettings = MutableStateFlow(
        FaceProcessingSettings.preset(FaceProcessingPreset.AGGRESSIVE)
    )
    val processingSettings: StateFlow<FaceProcessingSettings> =
        _processingSettings.asStateFlow()

    private val processor = VideoProcessor(application)

    fun updateProcessingSettings(settings: FaceProcessingSettings) {
        _processingSettings.value = settings
    }

    fun applyPreset(preset: FaceProcessingPreset) {
        _processingSettings.value = FaceProcessingSettings.preset(preset)
        log("Processing preset: ${presetLabel(preset)}")
    }

    fun log(message: String) {
        _console.value += "\n$message"
    }

    fun clearConsole() {
        _console.value = "Console cleared."
    }

    fun setState(state: AppState) {
        _appState.value = state
    }

    fun setRemainingSeconds(seconds: Int) {
        _remainingSeconds.value = seconds
    }

    fun setProgress(value: Int) {
        _progress.value = value.coerceIn(0, 100)
    }

    fun flipCamera() {
        if (_appState.value != AppState.RECORDING) {
            _cameraFlipped.value = !_cameraFlipped.value
        }
    }

    fun processVideo(file: File) {
        _appState.value = AppState.PROCESSING
        _processingOutcome.value = ProcessingOutcome.NONE
        _progress.value = 0

        // Freeze the settings for this processing run. Slider changes made while
        // processing cannot mutate the detector/clustering configuration midway.
        val settingsForRun = _processingSettings.value.normalized()

        viewModelScope.launch {
            try {
                log("Processing: ${file.name}")

                val result = processor.processVideo(
                    file = file,
                    settings = settingsForRun,
                    onProgress = { progress ->
                        _progress.value = progress
                    },
                    onLog = { message ->
                        log(message)
                    }
                )

                _collage.value = result
                _processingOutcome.value = ProcessingOutcome.PASSED
                _appState.value = AppState.READY
                log("Processing complete. Faces successfully processed.")
            } catch (e: Exception) {
                log("ERROR: ${e.javaClass.simpleName}")
                log("ERROR: ${e.message ?: "Unknown error"}")
                _processingOutcome.value = ProcessingOutcome.FAILED
                _appState.value = AppState.READY
            }
        }
    }

    fun resetResult() {
        val oldCollage = _collage.value
        _collage.value = null
        oldCollage?.recycleIfSafe()
        _progress.value = 0
        _processingOutcome.value = ProcessingOutcome.NONE
        _appState.value = AppState.READY
    }

    override fun onCleared() {
        processor.close()
        _collage.value?.recycleIfSafe()
        super.onCleared()
    }

    private fun presetLabel(preset: FaceProcessingPreset): String = when (preset) {
        FaceProcessingPreset.NORMAL -> "Normal"
        FaceProcessingPreset.AGGRESSIVE -> "Aggressive"
        FaceProcessingPreset.LENIENT -> "Lenient"
    }

    private fun Bitmap.recycleIfSafe() {
        if (!isRecycled) recycle()
    }
}

package com.iykyk.facecollage

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

class FaceCollageViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val _appState = MutableStateFlow(AppState.READY)
    val appState: StateFlow<AppState> = _appState.asStateFlow()

    private val _cameraFlipped = MutableStateFlow(false)
    val cameraFlipped: StateFlow<Boolean> = _cameraFlipped.asStateFlow()

    private val _remainingSeconds = MutableStateFlow(20)
    val remainingSeconds: StateFlow<Int> = _remainingSeconds.asStateFlow()

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

    private val processor = VideoProcessor(application)

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
        _progress.value = 0

        viewModelScope.launch {
            try {
                log("Processing: ${file.name}")

                val result = processor.processVideo(
                    file = file,
                    onProgress = { progress ->
                        _progress.value = progress
                    },
                    onLog = { message ->
                        log(message)
                    }
                )

                _collage.value = result

                log("Processing completed.")
                log("Collage generated successfully.")

                _appState.value = AppState.RESULT

            } catch (e: Exception) {
                log("ERROR: ${e.javaClass.simpleName}")
                log("ERROR: ${e.message ?: "Unknown error"}")
                _appState.value = AppState.READY
            }
        }
    }

    override fun onCleared() {
        processor.close()
        super.onCleared()
    }
}
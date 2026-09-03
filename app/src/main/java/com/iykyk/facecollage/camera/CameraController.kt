package com.iykyk.facecollage.camera

import android.content.Context
import android.os.CountDownTimer
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File

class CameraController(
    private val context: Context
) {

    private var previewView: PreviewView? = null

    private var videoCapture:
            VideoCapture<Recorder>? = null

    private var activeRecording:
            Recording? = null

    private var timer:
            CountDownTimer? = null

    private var wasCancelled =
        false

    fun setPreviewView(
        view: PreviewView
    ) {
        previewView = view
    }

    fun bindCamera(
        frontCamera: Boolean
    ) {

        val previewView =
            previewView ?: return

        val providerFuture =
            ProcessCameraProvider.getInstance(
                context
            )

        providerFuture.addListener({

            val provider =
                providerFuture.get()

            val preview =
                Preview.Builder()
                    .build()
                    .also {

                        it.setSurfaceProvider(
                            previewView.surfaceProvider
                        )
                    }

            val recorder =
                Recorder.Builder()
                    .setQualitySelector(
                        QualitySelector.from(
                            Quality.SD
                        )
                    )
                    .build()

            videoCapture =
                VideoCapture.withOutput(
                    recorder
                )

            val selector =
                if (frontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }

            try {

                provider.unbindAll()

                provider.bindToLifecycle(
                    context as LifecycleOwner,
                    selector,
                    preview,
                    videoCapture
                )

            } catch (e: Exception) {

                e.printStackTrace()
            }

        }, ContextCompat.getMainExecutor(context))
    }

    fun startRecording(
        onStarted: () -> Unit,
        onTick: (Int) -> Unit,
        onFinished: (File?) -> Unit,
        onCancelled: () -> Unit
    ): File? {

        val capture =
            videoCapture ?: return null

        val outputFile =
            File(
                context.cacheDir,
                "capture_${System.currentTimeMillis()}.mp4"
            )

        val outputOptions =
            FileOutputOptions.Builder(
                outputFile
            ).build()

        wasCancelled = false

        activeRecording =
            capture.output
                .prepareRecording(
                    context,
                    outputOptions
                )
                .start(
                    ContextCompat.getMainExecutor(
                        context
                    )
                ) { event ->

                    when (event) {

                        is VideoRecordEvent.Start -> {

                            onStarted()
                        }

                        is VideoRecordEvent.Finalize -> {

                            timer?.cancel()
                            timer = null

                            activeRecording = null

                            if (wasCancelled) {

                                /*
                                 * CameraX has FINISHED writing
                                 * the file now.
                                 *
                                 * Only delete it here.
                                 */

                                if (outputFile.exists()) {
                                    outputFile.delete()
                                }

                                wasCancelled = false

                                onCancelled()

                            } else {

                                if (
                                    event.hasError()
                                ) {

                                    onFinished(null)

                                } else {

                                    if (
                                        outputFile.exists()
                                    ) {

                                        onFinished(
                                            outputFile
                                        )

                                    } else {

                                        onFinished(null)
                                    }
                                }
                            }
                        }
                    }
                }

        /*
         * Maximum recording length = 20 seconds.
         */

        timer =
            object : CountDownTimer(
                20_000L,
                1_000L
            ) {

                override fun onTick(
                    millisUntilFinished: Long
                ) {

                    onTick(
                        (
                                millisUntilFinished /
                                        1000L
                                ).toInt()
                    )
                }

                override fun onFinish() {

                    /*
                     * Automatic 20-second finish.
                     *
                     * This is NOT a cancellation.
                     */

                    wasCancelled = false

                    activeRecording?.stop()
                }

            }.start()

        return outputFile
    }

    fun stopRecording(
        cancelled: Boolean
    ) {

        timer?.cancel()
        timer = null

        /*
         * IMPORTANT:
         *
         * Do NOT set activeRecording = null here.
         *
         * CameraX still has to send Finalize.
         */

        wasCancelled =
            cancelled

        activeRecording?.stop()
    }

    fun release() {

        timer?.cancel()

        timer = null

        activeRecording?.stop()

        activeRecording = null
    }
}
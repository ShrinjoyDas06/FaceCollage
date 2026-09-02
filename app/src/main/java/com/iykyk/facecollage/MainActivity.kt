package com.iykyk.facecollage

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
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
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.iykyk.facecollage.databinding.ActivityMainBinding
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var countdownTimer: android.os.CountDownTimer? = null

    private lateinit var processingExecutor: ExecutorService

    private val mainHandler = Handler(Looper.getMainLooper())

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (granted) {
                startCamera()
            } else {
                Toast.makeText(
                    this,
                    "Camera permission is required.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        processingExecutor = Executors.newSingleThreadExecutor()

        binding.btnRecord.setOnClickListener {
            startRecording()
        }

        binding.btnCancel.setOnClickListener {
            cancelRecording()
        }

        binding.btnShare.setOnClickListener {
            shareCurrentCollage()
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // ---------------------------------------------------------
    // CAMERA
    // ---------------------------------------------------------

    private fun startCamera() {

        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({

            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            val recorder = Recorder.Builder()
                .setQualitySelector(
                    QualitySelector.from(Quality.SD)
                )
                .build()

            videoCapture = VideoCapture.withOutput(recorder)

            try {

                cameraProvider.unbindAll()

                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    videoCapture
                )

            } catch (e: Exception) {

                Toast.makeText(
                    this,
                    "Unable to start camera: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    // ---------------------------------------------------------
    // RECORDING
    // ---------------------------------------------------------

    private fun startRecording() {

        val capture = videoCapture

        if (capture == null) {
            Toast.makeText(
                this,
                "Camera is not ready.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (activeRecording != null) {
            return
        }

        val outputFile = File(
            externalCacheDir,
            "capture_${System.currentTimeMillis()}.mp4"
        )

        val outputOptions =
            FileOutputOptions.Builder(outputFile).build()

        binding.btnRecord.visibility = View.GONE
        binding.btnCancel.visibility = View.VISIBLE
        binding.btnShare.visibility = View.GONE
        binding.tvTimer.visibility = View.VISIBLE
        binding.imgCollage.visibility = View.GONE

        binding.tvTimer.text = "20"

        activeRecording = capture.output
            .prepareRecording(
                this,
                outputOptions
            )
            .start(
                ContextCompat.getMainExecutor(this)
            ) { event ->

                when (event) {

                    is VideoRecordEvent.Start -> {
                        binding.tvTimer.text = "20"
                    }

                    is VideoRecordEvent.Finalize -> {

                        countdownTimer?.cancel()
                        countdownTimer = null

                        activeRecording = null

                        binding.tvTimer.visibility = View.GONE
                        binding.btnCancel.visibility = View.GONE
                        binding.btnRecord.visibility = View.VISIBLE

                        if (!event.hasError()) {

                            processVideo(outputFile)

                        } else {

                            Toast.makeText(
                                this,
                                "Recording failed: ${event.error}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }

        startCountdown()
    }

    private fun startCountdown() {

        countdownTimer =
            object : android.os.CountDownTimer(20_000, 1_000) {

                override fun onTick(millisUntilFinished: Long) {

                    val seconds =
                        (millisUntilFinished + 999) / 1000

                    binding.tvTimer.text =
                        seconds.toString()
                }

                override fun onFinish() {

                    binding.tvTimer.text = "0"

                    activeRecording?.stop()
                }

            }.start()
    }

    private fun cancelRecording() {

        countdownTimer?.cancel()
        countdownTimer = null

        activeRecording?.stop()
        activeRecording = null

        binding.tvTimer.visibility = View.GONE
        binding.btnCancel.visibility = View.GONE
        binding.btnRecord.visibility = View.VISIBLE
    }

    // ---------------------------------------------------------
    // VIDEO PROCESSING
    // ---------------------------------------------------------

    private fun processVideo(videoFile: File) {

        binding.progressLayout.visibility = View.VISIBLE

        processingExecutor.execute {

            try {

                val retriever = MediaMetadataRetriever()

                retriever.setDataSource(
                    videoFile.absolutePath
                )

                val durationMs =
                    retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_DURATION
                    )?.toLong() ?: 0L

                if (durationMs <= 0) {
                    retriever.release()
                    showError("Could not read recorded video.")
                    return@execute
                }

                val detectorOptions =
                    FaceDetectorOptions.Builder()
                        .setPerformanceMode(
                            FaceDetectorOptions.PERFORMANCE_MODE_FAST
                        )
                        .setLandmarkMode(
                            FaceDetectorOptions.LANDMARK_MODE_NONE
                        )
                        .setClassificationMode(
                            FaceDetectorOptions.CLASSIFICATION_MODE_NONE
                        )
                        .build()

                val detector =
                    FaceDetection.getClient(detectorOptions)

                val embedder = FaceEmbedder(this)

                val detectedFaces =
                    mutableListOf<DetectedFace>()

                // Approximately 2 frames per second.
                val frameIntervalUs = 500_000L

                var timeUs = 0L

                while (timeUs < durationMs * 1000L) {

                    val frame =
                        retriever.getFrameAtTime(
                            timeUs,
                            MediaMetadataRetriever.OPTION_CLOSEST
                        )

                    if (frame != null) {

                        try {

                            val inputImage =
                                InputImage.fromBitmap(
                                    frame,
                                    0
                                )

                            // Wait for ML Kit to finish this frame.
                            val faces =
                                com.google.android.gms.tasks.Tasks
                                    .await(
                                        detector.process(inputImage)
                                    )

                            for (face in faces) {

                                val bounds =
                                    face.boundingBox

                                val left =
                                    bounds.left.coerceAtLeast(0)

                                val top =
                                    bounds.top.coerceAtLeast(0)

                                val right =
                                    bounds.right.coerceAtMost(
                                        frame.width
                                    )

                                val bottom =
                                    bounds.bottom.coerceAtMost(
                                        frame.height
                                    )

                                val width =
                                    right - left

                                val height =
                                    bottom - top

                                if (width > 20 && height > 20) {

                                    val crop =
                                        Bitmap.createBitmap(
                                            frame,
                                            left,
                                            top,
                                            width,
                                            height
                                        )

                                    val embedding =
                                        embedder.getEmbedding(
                                            crop
                                        )

                                    detectedFaces.add(
                                        DetectedFace(
                                            crop,
                                            embedding,
                                            width * height
                                        )
                                    )
                                }
                            }

                        } finally {
                            frame.recycle()
                        }
                    }

                    timeUs += frameIntervalUs
                }

                detector.close()
                embedder.close()
                retriever.release()

                if (detectedFaces.isEmpty()) {

                    showError(
                        "No faces were detected. Try recording again with faces clearly visible."
                    )

                    return@execute
                }

                val uniqueFaces =
                    FaceClusterer(
                        similarityThreshold = 0.65f
                    ).clusterFaces(
                        detectedFaces
                    )

                if (uniqueFaces.isEmpty()) {

                    showError(
                        "No unique faces were found."
                    )

                    return@execute
                }

                val collage =
                    CollageBuilder.createGridCollage(
                        uniqueFaces,
                        cellSize = 300
                    )

                mainHandler.post {

                    binding.progressLayout.visibility =
                        View.GONE

                    binding.imgCollage.setImageBitmap(
                        collage
                    )

                    binding.imgCollage.visibility =
                        View.VISIBLE

                    binding.btnShare.visibility =
                        View.VISIBLE

                    binding.btnRecord.visibility =
                        View.VISIBLE

                    Toast.makeText(
                        this,
                        "${uniqueFaces.size} unique face(s) found.",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                // Save the current collage for sharing.
                saveCollageToInternalStorage(collage)

            } catch (e: Exception) {

                e.printStackTrace()

                showError(
                    "Processing failed: ${e.message}"
                )
            }
        }
    }

    // ---------------------------------------------------------
    // COLLAGE STORAGE / SHARING
    // ---------------------------------------------------------

    private var currentCollageFile: File? = null

    private fun saveCollageToInternalStorage(
        bitmap: Bitmap
    ) {

        val file =
            File(
                filesDir,
                "face_collage.png"
            )

        FileOutputStream(file).use { output ->

            bitmap.compress(
                Bitmap.CompressFormat.PNG,
                100,
                output
            )
        }

        currentCollageFile = file
    }

    private fun shareCurrentCollage() {

        val file = currentCollageFile

        if (file == null || !file.exists()) {

            Toast.makeText(
                this,
                "No collage available.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        try {

            val uri =
                FileProvider.getUriForFile(
                    this,
                    "${applicationContext.packageName}.provider",
                    file
                )

            val shareIntent =
                Intent(Intent.ACTION_SEND).apply {

                    type = "image/png"

                    putExtra(
                        Intent.EXTRA_STREAM,
                        uri
                    )

                    addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }

            startActivity(
                Intent.createChooser(
                    shareIntent,
                    "Share Face Collage"
                )
            )

        } catch (e: Exception) {

            Toast.makeText(
                this,
                "Unable to share collage: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // ---------------------------------------------------------
    // UI HELPERS
    // ---------------------------------------------------------

    private fun showError(message: String) {

        mainHandler.post {

            binding.progressLayout.visibility =
                View.GONE

            binding.btnCancel.visibility =
                View.GONE

            binding.btnRecord.visibility =
                View.VISIBLE

            binding.tvTimer.visibility =
                View.GONE

            Toast.makeText(
                this,
                message,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onDestroy() {

        countdownTimer?.cancel()
        activeRecording?.stop()

        processingExecutor.shutdown()

        super.onDestroy()
    }
}

data class DetectedFace(
    val bitmap: Bitmap,
    val embedding: FloatArray,
    val area: Int
)
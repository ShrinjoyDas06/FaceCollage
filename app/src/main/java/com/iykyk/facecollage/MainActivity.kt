package com.iykyk.facecollage

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Environment
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.video.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.iykyk.facecollage.databinding.ActivityMainBinding
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var countdownTimer: CountDownTimer? = null
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, 1001)
        }

        binding.btnRecord.setOnClickListener { startRecording() }
        binding.btnCancel.setOnClickListener { cancelRecording() }
    }

    private fun startCamera() {
        val cameraProviderFuture = androidx.camera.lifecycle.ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = androidx.camera.core.Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.SD))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, videoCapture)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startRecording() {
        val capture = videoCapture ?: return
        val outputFile = File(externalCacheDir, "capture_${System.currentTimeMillis()}.mp4")
        val outputOptions = FileOutputOptions.Builder(outputFile).build()

        binding.btnRecord.visibility = View.GONE
        binding.btnCancel.visibility = View.VISIBLE
        binding.tvTimer.visibility = View.VISIBLE

        activeRecording = capture.output
            .prepareRecording(this, outputOptions)
            .start(ContextCompat.getMainExecutor(this)) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    binding.tvTimer.visibility = View.GONE
                    binding.btnCancel.visibility = View.GONE
                    binding.btnRecord.visibility = View.VISIBLE
                    if (!event.hasError()) {
                        processVideo(outputFile)
                    }
                }
            }

        // 20-second automatic countdown auto-stop
        countdownTimer = object : CountDownTimer(20000, 1000) {
            override fun onTick(ms: Long) {
                binding.tvTimer.text = "Recording: ${ms / 1000}s"
            }
            override fun onFinish() {
                activeRecording?.stop()
                activeRecording = null
            }
        }.start()
    }

    private fun cancelRecording() {
        countdownTimer?.cancel()
        activeRecording?.stop()
        activeRecording = null
        binding.btnCancel.visibility = View.GONE
        binding.tvTimer.visibility = View.GONE
        binding.btnRecord.visibility = View.VISIBLE
    }

    private fun processVideo(file: File) {
        binding.progressLayout.visibility = View.VISIBLE
        executor.execute {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
            
            val detectedFaces = mutableListOf<Pair<Bitmap, Int>>()
            val detector = FaceDetection.getClient(
                FaceDetectorOptions.Builder().setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST).build()
            )

            // Extract ~2 frames per second
            for (timeUs in 0 until durationMs * 1000 step 500000) {
                val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: continue
                val image = InputImage.fromBitmap(frame, 0)
                
                detector.process(image).addOnSuccessListener { faces ->
                    for (face in faces) {
                        val bounds = face.boundingBox
                        if (bounds.left >= 0 && bounds.top >= 0 && 
                            bounds.right <= frame.width && bounds.bottom <= frame.height) {
                            val crop = Bitmap.createBitmap(frame, bounds.left, bounds.top, bounds.width(), bounds.height())
                            detectedFaces.add(Pair(crop, bounds.width() * bounds.height()))
                        }
                    }
                }
            }

            // Cluster unique faces
            val uniqueFaces = FaceClusterer().clusterFaces(detectedFaces)
            val collageBitmap = CollageBuilder.createGridCollage(uniqueFaces)

            runOnUiThread {
                binding.progressLayout.visibility = View.GONE
                binding.imgCollage.setImageBitmap(collageBitmap)
                binding.btnShare.setOnClickListener { shareCollage(collageBitmap) }
                binding.btnShare.visibility = View.VISIBLE
            }
        }
    }

    private fun shareCollage(bitmap: Bitmap) {
        val file = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "face_collage.png")
        val out = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        out.flush()
        out.close()

        val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share Face Collage"))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    }
}
package com.iykyk.facecollage.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iykyk.facecollage.AppState
import com.iykyk.facecollage.FaceCollageViewModel
import com.iykyk.facecollage.camera.CameraController

@androidx.compose.runtime.Composable
fun FaceCollageScreen(
    viewModel: FaceCollageViewModel = viewModel()
) {
    val state by viewModel.appState.collectAsState()
    val flipped by viewModel.cameraFlipped.collectAsState()
    val remaining by viewModel.remainingSeconds.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val collage by viewModel.collage.collectAsState()
    val console by viewModel.console.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current

    val cameraController = remember {
        CameraController(context)
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraController.release()
        }
    }

    LaunchedEffect(flipped) {
        if (state != AppState.RECORDING) {
            cameraController.bindCamera(flipped)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {

            Text(
                text = "Face Collage",
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (state != AppState.RESULT) {

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {

                    AndroidView(
                        factory = {
                            PreviewView(it).apply {
                                scaleType =
                                    PreviewView.ScaleType.FILL_CENTER

                                cameraController.setPreviewView(this)
                                cameraController.bindCamera(flipped)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    if (state == AppState.RECORDING) {
                        Text(
                            text = "● REC  ${remaining}s",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {

                    OutlinedButton(
                        onClick = {
                            if (state != AppState.RECORDING) {
                                viewModel.flipCamera()
                            }
                        },
                        enabled = state != AppState.RECORDING
                    ) {
                        Text("Flip Camera")
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    if (state == AppState.RECORDING) {

                      Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                      ) {
                  
                          Button(
                              onClick = {
                                  cameraController.stopRecording(false)
                              },
                              modifier = Modifier.weight(1f)
                          ) {
                              Text("Done")
                          }
                  
                          Button(
                              onClick = {
                                  cameraController.stopRecording(true)
                              },
                              modifier = Modifier.weight(1f)
                          ) {
                              Text("Cancel")
                          }
                      }

                    } else {

                        Button(
                            onClick = {

                                val file = cameraController.startRecording(
                                    onStarted = {
                                        viewModel.setState(AppState.RECORDING)
                                    },
                                    onTick = { seconds ->
                                        viewModel.setRemainingSeconds(seconds)
                                    },
                                    onFinished = { outputFile ->
                                        viewModel.setRemainingSeconds(0)

                                        if (outputFile != null) {
                                            viewModel.processVideo(outputFile)
                                        } else {
                                            viewModel.setState(AppState.READY)
                                            viewModel.log(
                                                "ERROR: Recording failed."
                                            )
                                        }
                                    }
                                )

                                if (file == null) {
                                    viewModel.log(
                                        "ERROR: Camera is not ready."
                                    )
                                }
                            }
                        ) {
                            Text("Capture")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (state == AppState.PROCESSING) {

                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Processing... $progress%")
                }

            } else {

                collage?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Face collage",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                            .clip(MaterialTheme.shapes.large)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {

                    Button(
                        onClick = {
                            collage?.let {
                                shareBitmap(context, it)
                            }
                        }
                    ) {
                        Text("Share")
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    OutlinedButton(
                        onClick = {
                            viewModel.setState(AppState.READY)
                        }
                    ) {
                        Text("Capture Again")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Console",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(4.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium
            ) {

                Column(
                    modifier = Modifier.padding(10.dp)
                ) {

                    LazyColumn(
                        modifier = Modifier.weight(1f)
                    ) {
                        item {
                            Text(
                                text = console,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedButton(
                        onClick = {
                            viewModel.clearConsole()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Clear Console")
                    }
                }
            }
        }
    }
}

private fun shareBitmap(
    context: android.content.Context,
    bitmap: Bitmap
) {
    try {

        val file = java.io.File(
            context.cacheDir,
            "face_collage.png"
        )

        java.io.FileOutputStream(file).use { output ->
            bitmap.compress(
                Bitmap.CompressFormat.PNG,
                100,
                output
            )
        }

        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(
            Intent.createChooser(
                intent,
                "Share Face Collage"
            )
        )

    } catch (e: Exception) {
        e.printStackTrace()
    }
}
package com.iykyk.facecollage.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.ColorDrawable
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iykyk.facecollage.AppState
import com.iykyk.facecollage.FaceCollageViewModel
import com.iykyk.facecollage.camera.CameraController
import java.io.File
import java.io.FileOutputStream

@Composable
fun FaceCollageScreen(
    viewModel: FaceCollageViewModel = viewModel()
) {

    /*
     * ---------------------------------------------------------
     * STATE
     * ---------------------------------------------------------
     */

    val state by viewModel.appState.collectAsState()
    val flipped by viewModel.cameraFlipped.collectAsState()
    val remaining by viewModel.remainingSeconds.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val collage by viewModel.collage.collectAsState()
    val console by viewModel.console.collectAsState()

    val context = LocalContext.current

    /*
     * ---------------------------------------------------------
     * CAMERA CONTROLLER
     * ---------------------------------------------------------
     */

    val cameraController = remember {
        CameraController(context)
    }

    /*
     * Release camera when this screen leaves composition.
     */

    DisposableEffect(Unit) {

        onDispose {
            cameraController.release()
        }
    }

    /*
     * Re-bind the camera whenever the user flips it.
     *
     * IMPORTANT:
     * We do not allow camera flipping while recording.
     */

    LaunchedEffect(flipped) {

        if (state != AppState.RECORDING) {

            cameraController.bindCamera(
                flipped
            )
        }
    }

    /*
     * ---------------------------------------------------------
     * MAIN UI
     * ---------------------------------------------------------
     */

    Surface(
        modifier = Modifier.fillMaxSize()
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {

            /*
             * -------------------------------------------------
             * TITLE
             * -------------------------------------------------
             */

            Text(
                text = "Face Collage",
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            /*
             * =================================================
             * CAMERA / RESULT AREA
             * =================================================
             */

            if (state != AppState.RESULT) {

                /*
                 * -------------------------------------------------
                 * CAMERA PREVIEW
                 * -------------------------------------------------
                 */

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .clip(
                            MaterialTheme.shapes.large
                        )
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant
                        )
                ) {

                    AndroidView(

                        factory = { viewContext ->

                            PreviewView(
                                viewContext
                            ).apply {

                                scaleType =
                                    PreviewView.ScaleType.FILL_CENTER

                                /*
                                 * Give the PreviewView to the
                                 * CameraController.
                                 */

                                cameraController
                                    .setPreviewView(this)

                                /*
                                 * Start the camera.
                                 */

                                cameraController
                                    .bindCamera(flipped)
                            }
                        },

                        modifier =
                            Modifier.fillMaxSize()
                    )

                    /*
                     * -------------------------------------------------
                     * RECORDING INDICATOR
                     * -------------------------------------------------
                     */

                    if (
                        state ==
                        AppState.RECORDING
                    ) {

                        Surface(
                            modifier = Modifier
                                .align(
                                    Alignment.TopCenter
                                )
                                .padding(10.dp),
                            shape =
                                MaterialTheme.shapes.medium,
                            color =
                                MaterialTheme.colorScheme.surface
                        ) {

                            Text(
                                text =
                                    "● REC  ${remaining}s",

                                color =
                                    MaterialTheme.colorScheme.error,

                                style =
                                    MaterialTheme.typography
                                        .titleMedium,

                                modifier =
                                    Modifier.padding(
                                        horizontal = 12.dp,
                                        vertical = 6.dp
                                    )
                            )
                        }
                    }
                }

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                /*
                 * =================================================
                 * CAMERA CONTROLS
                 * =================================================
                 */

                if (
                    state ==
                    AppState.RECORDING
                ) {

                    /*
                     * ---------------------------------------------
                     * RECORDING CONTROLS
                     * ---------------------------------------------
                     *
                     * Stop & Process:
                     *     immediately ends recording and sends the
                     *     partial video to the face processor.
                     *
                     * Cancel:
                     *     stops recording and discards the video.
                     *
                     * We deliberately do NOT show Flip Camera here.
                     */

                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),

                        horizontalArrangement =
                            Arrangement.spacedBy(12.dp)
                    ) {

                        Button(
                            onClick = {

                                /*
                                 * false = NOT cancelled.
                                 *
                                 * CameraController will wait for
                                 * CameraX Finalize and then call
                                 * onFinished().
                                 */

                                cameraController
                                    .stopRecording(false)
                            },

                            modifier =
                                Modifier.weight(1f)
                        ) {

                            Text(
                                text = "Stop & Process"
                            )
                        }

                        OutlinedButton(
                            onClick = {

                                /*
                                 * true = discard recording.
                                 */

                                cameraController
                                    .stopRecording(true)
                            },

                            modifier =
                                Modifier.weight(1f)
                        ) {

                            Text(
                                text = "Cancel"
                            )
                        }
                    }

                } else {

                    /*
                     * ---------------------------------------------
                     * NORMAL CAMERA CONTROLS
                     * ---------------------------------------------
                     */

                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),

                        horizontalArrangement =
                            Arrangement.spacedBy(12.dp)
                    ) {

                        /*
                         * FLIP CAMERA
                         */

                        OutlinedButton(
                            onClick = {

                                if (
                                    state !=
                                    AppState.RECORDING
                                ) {

                                    viewModel.flipCamera()
                                }
                            },

                            enabled =
                                state !=
                                        AppState.RECORDING,

                            modifier =
                                Modifier.weight(1f)
                        ) {

                            Text(
                                text = "Flip Camera"
                            )
                        }

                        /*
                         * CAPTURE
                         */

                        Button(
                            onClick = {

                                /*
                                 * Start recording.
                                 *
                                 * IMPORTANT:
                                 * Processing does NOT start here.
                                 *
                                 * It starts only after CameraX
                                 * finishes writing the video.
                                 */

                                val file =
                                    cameraController
                                        .startRecording(

                                            /*
                                             * ---------------------
                                             * Recording started
                                             * ---------------------
                                             */

                                            onStarted = {

                                                viewModel.setState(
                                                    AppState.RECORDING
                                                )

                                                viewModel.log(
                                                    "Recording started."
                                                )
                                            },

                                            /*
                                             * ---------------------
                                             * Countdown
                                             * ---------------------
                                             */

                                            onTick = { seconds ->

                                                viewModel
                                                    .setRemainingSeconds(
                                                        seconds
                                                    )
                                            },

                                            /*
                                             * ---------------------
                                             * Recording finished
                                             * ---------------------
                                             */

                                            onFinished = {
                                                    outputFile ->

                                                viewModel
                                                    .setRemainingSeconds(
                                                        0
                                                    )

                                                if (
                                                    outputFile != null &&
                                                    outputFile.exists()
                                                ) {

                                                    viewModel.log(
                                                        "Recording finished."
                                                    )

                                                    viewModel.log(
                                                        "Video: ${outputFile.name}"
                                                    )

                                                    /*
                                                     * Now processing
                                                     * begins.
                                                     */

                                                    viewModel
                                                        .processVideo(
                                                            outputFile
                                                        )

                                                } else {

                                                    viewModel.setState(
                                                        AppState.READY
                                                    )

                                                    viewModel.log(
                                                        "ERROR: Recording failed."
                                                    )
                                                }
                                            },

                                            /*
                                             * ---------------------
                                             * Recording cancelled
                                             * ---------------------
                                             */

                                            onCancelled = {

                                                viewModel
                                                    .setRemainingSeconds(
                                                        20
                                                    )

                                                viewModel.setState(
                                                    AppState.READY
                                                )

                                                viewModel.log(
                                                    "Recording cancelled."
                                                )
                                            }
                                        )

                                /*
                                 * Camera was not ready.
                                 */

                                if (
                                    file == null
                                ) {

                                    viewModel.setState(
                                        AppState.READY
                                    )

                                    viewModel.log(
                                        "ERROR: Camera is not ready."
                                    )
                                }
                            },

                            modifier =
                                Modifier.weight(1f)
                        ) {

                            Text(
                                text = "Capture"
                            )
                        }
                    }
                }

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                /*
                 * =================================================
                 * PROCESSING PROGRESS
                 * =================================================
                 */

                if (
                    state ==
                    AppState.PROCESSING
                ) {

                    LinearProgressIndicator(
                        progress = {
                            progress / 100f
                        },

                        modifier =
                            Modifier.fillMaxWidth()
                    )

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    Text(
                        text =
                            "Processing... $progress%"
                    )

                } else if (
                    state ==
                    AppState.READY
                ) {

                    /*
                     * Nothing special while ready.
                     */

                } else {

                    /*
                     * Optional small loading indicator for any
                     * transitional state.
                     */

                    if (
                        state !=
                        AppState.RECORDING
                    ) {

                        Spacer(
                            modifier =
                                Modifier.height(2.dp)
                        )
                    }
                }

            } else {

                /*
                 * =================================================
                 * RESULT SCREEN
                 * =================================================
                 */

                Text(
                    text = "Your Face Collage",
                    style =
                        MaterialTheme.typography.titleLarge
                )

                Spacer(
                    modifier = Modifier.height(10.dp)
                )

                /*
                 * Show generated collage.
                 */

                collage?.let { bitmap ->

                    Image(
                        bitmap =
                            bitmap.asImageBitmap(),

                        contentDescription =
                            "Face collage",

                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(320.dp)
                                .clip(
                                    MaterialTheme.shapes.large
                                )
                    )

                } ?: run {

                    /*
                     * Safety fallback if RESULT state exists
                     * but bitmap is somehow null.
                     */

                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(320.dp),

                        contentAlignment =
                            Alignment.Center
                    ) {

                        Text(
                            text =
                                "No collage available."
                        )
                    }
                }

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                /*
                 * -------------------------------------------------
                 * RESULT BUTTONS
                 * -------------------------------------------------
                 */

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),

                    horizontalArrangement =
                        Arrangement.spacedBy(12.dp)
                ) {

                    /*
                     * SHARE
                     */

                    Button(
                        onClick = {

                            collage?.let { bitmap ->

                                shareBitmap(
                                    context,
                                    bitmap
                                )
                            }
                        },

                        enabled =
                            collage != null,

                        modifier =
                            Modifier.weight(1f)
                    ) {

                        Text(
                            text = "Share"
                        )
                    }

                    /*
                     * CAPTURE AGAIN
                     */

                    OutlinedButton(
                        onClick = {

                            viewModel.setState(
                                AppState.READY
                            )

                            viewModel.log(
                                "Ready for another capture."
                            )
                        },

                        modifier =
                            Modifier.weight(1f)
                    ) {

                        Text(
                            text = "Capture Again"
                        )
                    }
                }
            }

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            /*
             * =================================================
             * CONSOLE
             * =================================================
             */

            Text(
                text = "Console",
                style =
                    MaterialTheme.typography.titleMedium
            )

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),

                color =
                    MaterialTheme.colorScheme.surfaceVariant,

                shape =
                    MaterialTheme.shapes.medium
            ) {

                Column(
                    modifier =
                        Modifier.padding(10.dp)
                ) {

                    /*
                     * Console log.
                     */

                    LazyColumn(
                        modifier =
                            Modifier.weight(1f)
                    ) {

                        item {

                            Text(
                                text = console,

                                style =
                                    MaterialTheme.typography
                                        .bodySmall
                            )
                        }
                    }

                    Spacer(
                        modifier =
                            Modifier.height(6.dp)
                    )

                    /*
                     * CLEAR CONSOLE
                     */

                    OutlinedButton(
                        onClick = {

                            viewModel.clearConsole()
                        },

                        modifier =
                            Modifier.fillMaxWidth()
                    ) {

                        Text(
                            text = "Clear Console"
                        )
                    }
                }
            }
        }
    }
}


/*
 * =============================================================
 * SHARE COLLAGE
 * =============================================================
 *
 * Saves the generated bitmap into cache and shares it through
 * Android FileProvider.
 *
 * Your AndroidManifest.xml and file_paths.xml must contain the
 * FileProvider configuration for this to work.
 */

private fun shareBitmap(
    context: Context,
    bitmap: Bitmap
) {

    try {

        /*
         * Create temporary PNG file.
         */

        val file =
            File(
                context.cacheDir,
                "face_collage.png"
            )

        FileOutputStream(
            file
        ).use { output ->

            bitmap.compress(
                Bitmap.CompressFormat.PNG,
                100,
                output
            )
        }

        /*
         * Convert local file into content:// URI.
         */

        val uri: Uri =
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )

        /*
         * Create share intent.
         */

        val shareIntent =
            Intent(
                Intent.ACTION_SEND
            ).apply {

                type =
                    "image/png"

                putExtra(
                    Intent.EXTRA_STREAM,
                    uri
                )

                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

        /*
         * Open Android's share sheet.
         */

        context.startActivity(
            Intent.createChooser(
                shareIntent,
                "Share Face Collage"
            )
        )

    } catch (e: Exception) {

        /*
         * Do not crash the application if sharing fails.
         */

        e.printStackTrace()
    }
}
package com.iykyk.facecollage.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iykyk.facecollage.AppState
import com.iykyk.facecollage.FaceCollageViewModel
import com.iykyk.facecollage.ProcessingOutcome
import com.iykyk.facecollage.camera.CameraController
import java.io.File
import java.io.FileOutputStream

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle

@Composable
fun FaceCollageScreen(
    viewModel: FaceCollageViewModel = viewModel()
) {
    val state by viewModel.appState.collectAsState()
    val flipped by viewModel.cameraFlipped.collectAsState()
    val remaining by viewModel.remainingSeconds.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val collage by viewModel.collage.collectAsState()
    val console by viewModel.console.collectAsState()
    val settings by viewModel.processingSettings.collectAsState()
    val outcome by viewModel.processingOutcome.collectAsState()

    var showMenu by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showConsole by remember { mutableStateOf(false) }
    var showPreview by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val cameraController = remember { CameraController(context) }

    DisposableEffect(Unit) {
        onDispose { cameraController.release() }
    }

    LaunchedEffect(flipped, state) {
        if (state != AppState.RECORDING && state != AppState.PROCESSING) {
            cameraController.bindCamera(flipped)
        }
    }

    LaunchedEffect(outcome) {
        when (outcome) {
            ProcessingOutcome.PASSED -> {
                Toast.makeText(context, "Processing passed", Toast.LENGTH_SHORT).show()
            }

            ProcessingOutcome.FAILED ->
                Toast.makeText(context, "Processing failed", Toast.LENGTH_SHORT).show()

            ProcessingOutcome.NONE -> Unit
        }
    }

    BackHandler(enabled = showPreview || showSettings || showConsole) {
        when {
            showPreview -> showPreview = false
            showSettings -> showSettings = false
            showConsole -> showConsole = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { viewContext ->
                PreviewView(viewContext).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    cameraController.setPreviewView(this)
                    cameraController.bindCamera(flipped)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (state == AppState.PROCESSING) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            )
        }

        // Top controls deliberately sit over the camera but inside safe drawing insets.
        if (state != AppState.RECORDING && state != AppState.PROCESSING) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                IconButton(
                    onClick = { viewModel.flipCamera() },
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = Color.White
                    )
                ) {
                    Text("↻", style = MaterialTheme.typography.headlineMedium)
                }

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = Color.White
                        )
                    ) {
                        Text("⋮", style = MaterialTheme.typography.headlineMedium)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Console") },
                            onClick = {
                                showMenu = false
                                showConsole = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            onClick = {
                                showMenu = false
                                showSettings = true
                            }
                        )
                    }
                }
            }
        }

        if (state == AppState.RECORDING) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(top = 8.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f)
            ) {
                Text(
                    text = "● REC  ${remaining}s",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        // Last generated collage thumbnail.
        if (collage != null && state != AppState.RECORDING && state != AppState.PROCESSING) {
            Image(
                bitmap = collage!!.asImageBitmap(),
                contentDescription = "Last collage",
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(start = 16.dp, bottom = 18.dp)
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickableNoRipple { showPreview = true }
            )
        }

        if (state != AppState.PROCESSING && state != AppState.RESULT) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                if (state == AppState.RECORDING) {
                    Box(
                        modifier = Modifier
                            .width(200.dp)
                            .height(82.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Always centered
                        RoundIconButton(
                            symbol = "■",
                            onClick = { cameraController.stopRecording(false) },
                            containerSize = 76.dp,
                            iconStyle = MaterialTheme.typography.headlineLarge
                        )

                        // X sits to the right of the centered stop button
                        RoundIconButton(
                            symbol = "×",
                            onClick = { cameraController.stopRecording(true) },
                            containerSize = 52.dp,
                            modifier = Modifier.align(Alignment.CenterEnd)
                        )
                    }
                } else {
                    TransparentStrokeCircleButton(
                        onClick = {
                            val file = cameraController.startRecording(
                                onStarted = {
                                    viewModel.setState(AppState.RECORDING)
                                    viewModel.log("Recording started.")
                                },
                                onTick = { viewModel.setRemainingSeconds(it) },
                                onFinished = { outputFile ->
                                    viewModel.setRemainingSeconds(0)
                                    if (outputFile != null && outputFile.exists()) {
                                        viewModel.log("Recording finished.")
                                        viewModel.log("Video: ${outputFile.name}")
                                        viewModel.processVideo(outputFile)
                                    } else {
                                        viewModel.setState(AppState.READY)
                                        viewModel.log("ERROR: Recording failed.")
                                    }
                                },
                                onCancelled = {
                                    viewModel.setRemainingSeconds(20)
                                    viewModel.setState(AppState.READY)
                                    viewModel.log("Recording cancelled.")
                                }
                            )
                            if (file == null) {
                                viewModel.setState(AppState.READY)
                                viewModel.log("ERROR: Camera is not ready.")
                            }
                        },
                        containerSize = 82.dp
                    )
                }
            }
        }

        if (state == AppState.PROCESSING) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 22.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f)
            ) {
                Text(
                    "Processing… $progress%",
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                )
            }
        }
    }

    if (showSettings) {
        FaceProcessingSettingsDialog(
            initialSettings = settings,
            onApply = { newSettings ->
                viewModel.updateProcessingSettings(newSettings)
                viewModel.log("Face processing settings updated.")
            },
            onDismiss = { showSettings = false }
        )
    }

    if (showConsole) {
        ConsoleDialog(
            console = console,
            onClear = { viewModel.clearConsole() },
            onCopy = { copyToClipboard(context, console) },
            onDismiss = { showConsole = false }
        )
    }

    if (showPreview && collage != null) {
        CollagePreviewDialog(
            bitmap = collage!!,
            onSave = {
                val saved = saveBitmapToGallery(context, collage!!)
                Toast.makeText(
                    context,
                    if (saved != null) "Collage saved" else "Unable to save collage",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onShare = { shareBitmap(context, collage!!) },
            onDismiss = { showPreview = false }
        )
    }
}

@Composable
private fun RoundIconButton(
    symbol: String,
    onClick: () -> Unit,
    containerSize: Dp,
    modifier: Modifier = Modifier,
    iconStyle: TextStyle = MaterialTheme.typography.headlineSmall,
) {
    Surface(
        modifier = modifier
            .size(containerSize)
            .clip(CircleShape)
            .clickableNoRipple(onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 4.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(symbol, style = iconStyle)
        }
    }
}

@Composable
fun Modifier.clickableNoRipple(
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit
): Modifier = this.then(
    Modifier.clickable(
        interactionSource = interactionSource ?: remember { MutableInteractionSource() },
        indication = null, // Disables the default ripple effect
        onClick = onClick
    )
)

@Composable
fun TransparentStrokeCircleButton(
    onClick: () -> Unit,
    containerSize: Dp,
    modifier: Modifier = Modifier,
    strokeColor: Color = Color.White,
    strokeWidth: Dp = 4.dp,
    pressedScale: Float = 0.90f // Adjust scale intensity here
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1.0f,
        animationSpec = spring(
            dampingRatio = 0.6f, // Adds subtle springiness
            stiffness = 800f
        ),
        label = "press_scale_anim"
    )

    Surface(
        modifier = modifier
            .size(containerSize)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .clickableNoRipple(
                interactionSource = interactionSource,
                onClick = onClick
            ),
        shape = CircleShape,
        color = Color.Transparent,
        tonalElevation = 4.dp
    ) {
        Box(
            modifier = Modifier
                .size(containerSize)
                .border(
                    width = strokeWidth,
                    color = strokeColor,
                    shape = CircleShape
                )
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {}
    }
}

@Composable
private fun ConsoleDialog(
    console: String,
    onClear: () -> Unit,
    onCopy: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Console",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = onDismiss) { Text("Close") }
                }
                Spacer(Modifier.height(12.dp))
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium
                ) {
                    LazyColumn(modifier = Modifier.padding(12.dp)) {
                        item { Text(console, style = MaterialTheme.typography.bodySmall) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onCopy,
                        modifier = Modifier.weight(1f)
                    ) { Text("Copy") }
                    Button(onClick = onClear, modifier = Modifier.weight(1f)) { Text("Clear") }
                }
            }
        }
    }
}

@Composable
private fun CollagePreviewDialog(
    bitmap: Bitmap,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Your Face Collage",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = onDismiss) { Text("Close") }
                }
                Spacer(Modifier.height(14.dp))
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Face collage",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(onClick = onSave, modifier = Modifier.weight(1f)) { Text("Save") }
                    OutlinedButton(
                        onClick = onShare,
                        modifier = Modifier.weight(1f)
                    ) { Text("Share") }
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Face Collage Console", text))
    Toast.makeText(context, "Console copied", Toast.LENGTH_SHORT).show()
}

private fun saveBitmapToGallery(context: Context, bitmap: Bitmap): Uri? {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "face_collage_${System.currentTimeMillis()}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/FaceCollage"
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
    return try {
        resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                null,
                null
            )
        }
        uri
    } catch (e: Exception) {
        resolver.delete(uri, null, null)
        null
    }
}

private fun shareBitmap(context: Context, bitmap: Bitmap) {
    try {
        val file = File(context.cacheDir, "face_collage.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share collage"))
    } catch (e: Exception) {
        Toast.makeText(context, "Unable to share collage", Toast.LENGTH_SHORT).show()
    }
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)

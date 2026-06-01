package com.example.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.ImageCapture
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.PhotoEntity
import com.example.ui.theme.*
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val isAppInitializing by viewModel.isInitializing.collectAsStateWithLifecycle()
    val isAiProcessing by viewModel.isAiProcessing.collectAsStateWithLifecycle()
    val processStatus by viewModel.processStatus.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()

    val lensFacing by viewModel.lensFacing.collectAsStateWithLifecycle()
    val zoom by viewModel.zoom.collectAsStateWithLifecycle()
    val hardwareZoom by viewModel.hardwareZoom.collectAsStateWithLifecycle()
    val showGrid by viewModel.showGrid.collectAsStateWithLifecycle()
    val isFlashing by viewModel.isFlashing.collectAsStateWithLifecycle()
    val flashMode by viewModel.flashMode.collectAsStateWithLifecycle()
    val isNightMode by viewModel.isNightMode.collectAsStateWithLifecycle()
    val isHyperClarity by viewModel.isHyperClarity.collectAsStateWithLifecycle()

    // Android Camera Permissions
    var cameraPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        cameraPermissionGranted = isGranted
    }

    LaunchedEffect(Unit) {
        if (!cameraPermissionGranted) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // CameraX instance bindings
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var activeCameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var activeCameraInfo by remember { mutableStateOf<CameraInfo?>(null) }

    // Detail view state
    var selectedPhotoForDetail by remember { mutableStateOf<PhotoEntity?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Slate950)
    ) {
        if (!cameraPermissionGranted) {
            // Permission Denied View
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.widthIn(max = 380.dp),
                    colors = CardDefaults.cardColors(containerColor = Slate900),
                    border = BorderStroke(1.dp, Color.Red.copy(0.25f))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = "Error",
                            tint = Color.Red,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Camera Permission Required",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "To run Titanium AI pro features, the application needs access to your camera sensor.",
                            color = Gray400,
                            textAlign = TextAlign.Center,
                            fontSize = 14.sp
                        )
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Grant Sensor Access")
                        }
                    }
                }
            }
        } else {
            // Main App Layout
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header (App Title & Top Capsule Actions)
                HeaderSection(
                    showGrid = showGrid,
                    onToggleGrid = { viewModel.toggleGrid() }
                )

                // Split Layout for larger screens (Viewfinder and Gallery Side-by-Side)
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    val isTablet = maxWidth >= 800.dp

                    if (isTablet) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            // Left Side: Viewfinder Core
                            Box(
                                modifier = Modifier
                                    .weight(1.3f)
                                    .fillMaxHeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                ViewfinderSection(
                                    lensFacing = lensFacing,
                                    zoom = zoom,
                                    hardwareZoom = hardwareZoom,
                                    isFlashing = isFlashing,
                                    flashMode = flashMode,
                                    showGrid = showGrid,
                                    onCameraControlReady = { ctrl, info ->
                                        activeCameraControl = ctrl
                                        activeCameraInfo = info
                                    },
                                    imageCaptureInstance = { imageCapture = it }
                                )
                            }

                            // Right Side: Controls and Studio Gallery Panel
                            Column(
                                modifier = Modifier
                                    .width(360.dp)
                                    .fillMaxHeight()
                                    .background(Slate900)
                                    .border(
                                        width = 1.dp,
                                        color = BorderDark,
                                        shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)
                                    )
                                    .clip(RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp))
                            ) {
                                ControlButtonsSection(
                                    zoom = zoom,
                                    isNightMode = isNightMode,
                                    isHyperClarity = isHyperClarity,
                                    flashMode = flashMode,
                                    onZoomChange = { viewModel.setZoom(it) },
                                    onToggleNightMode = { viewModel.toggleNightMode() },
                                    onToggleHyperClarity = { viewModel.toggleHyperClarity() },
                                    onToggleFlash = { viewModel.toggleFlash() }
                                )

                                ShutterBarSection(
                                    latestPhoto = photos.firstOrNull(),
                                    isAiProcessing = isAiProcessing,
                                    scaleMode = zoom >= 5f,
                                    onTakePhoto = {
                                        captureImage(
                                            imageCapture,
                                            ContextCompat.getMainExecutor(context),
                                            onImageCaptured = { bytes ->
                                                viewModel.processCapturedImage(bytes)
                                            },
                                            onError = {
                                                viewModel.showToast("Sensor capture failure.")
                                            }
                                        )
                                    },
                                    onToggleCamera = { viewModel.toggleCamera() },
                                    onGalleryClick = {
                                        if (photos.isNotEmpty()) {
                                            selectedPhotoForDetail = photos.first()
                                        }
                                    }
                                )

                                GalleryPanel(
                                    photos = photos,
                                    onPhotoClick = { selectedPhotoForDetail = it },
                                    onClearAll = { viewModel.clearAllPhotos() }
                                )
                            }
                        }
                    } else {
                        // Standard Portrait Phone Layout
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Upper: Viewfinder
                            Box(
                                modifier = Modifier
                                    .weight(1.3f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                ViewfinderSection(
                                    lensFacing = lensFacing,
                                    zoom = zoom,
                                    hardwareZoom = hardwareZoom,
                                    isFlashing = isFlashing,
                                    flashMode = flashMode,
                                    showGrid = showGrid,
                                    onCameraControlReady = { ctrl, info ->
                                        activeCameraControl = ctrl
                                        activeCameraInfo = info
                                    },
                                    imageCaptureInstance = { imageCapture = it }
                                )
                            }

                            // Lower Controls bar
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Slate900)
                                    .shadow(elevation = 20.dp)
                            ) {
                                ControlButtonsSection(
                                    zoom = zoom,
                                    isNightMode = isNightMode,
                                    isHyperClarity = isHyperClarity,
                                    flashMode = flashMode,
                                    onZoomChange = { viewModel.setZoom(it) },
                                    onToggleNightMode = { viewModel.toggleNightMode() },
                                    onToggleHyperClarity = { viewModel.toggleHyperClarity() },
                                    onToggleFlash = { viewModel.toggleFlash() }
                                )

                                ShutterBarSection(
                                    latestPhoto = photos.firstOrNull(),
                                    isAiProcessing = isAiProcessing,
                                    scaleMode = zoom >= 5f,
                                    onTakePhoto = {
                                        captureImage(
                                            imageCapture,
                                            ContextCompat.getMainExecutor(context),
                                            onImageCaptured = { bytes ->
                                                viewModel.processCapturedImage(bytes)
                                            },
                                            onError = {
                                                viewModel.showToast("Sensor capture failure.")
                                            }
                                        )
                                    },
                                    onToggleCamera = { viewModel.toggleCamera() },
                                    onGalleryClick = {
                                        if (photos.isNotEmpty()) {
                                            selectedPhotoForDetail = photos.first()
                                        }
                                    }
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .background(Slate950)
                                ) {
                                    GalleryPanel(
                                        photos = photos,
                                        onPhotoClick = { selectedPhotoForDetail = it },
                                        onClearAll = { viewModel.clearAllPhotos() }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating Toast
        toastMessage?.let { msg ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 100.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(50.dp),
                    color = Color(0xFF0F172A).copy(0.95f),
                    border = BorderStroke(1.dp, CyanNeon.copy(0.4f)),
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .clickable(
                            onClick = { viewModel.dismissToast() },
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            tint = CyanNeon,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = msg,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            LaunchedEffect(msg) {
                kotlinx.coroutines.delay(3000)
                viewModel.dismissToast()
            }
        }

        // Full Screen AI Reconstructing overlay
        AnimatedVisibility(
            visible = isAiProcessing,
            enter = fadeIn(animationSpec = tween(400)),
            exit = fadeOut(animationSpec = tween(400))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(0.95f)),
                contentAlignment = Alignment.Center
            ) {
                val infiniteTransition = rememberInfiniteTransition(label = "engine_loops")
                val pulseScale by infiniteTransition.animateFloat(
                    initialValue = 0.92f,
                    targetValue = 1.08f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulse"
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Upscaling",
                        tint = CyanNeon,
                        modifier = Modifier
                            .size(64.dp)
                            .scale(pulseScale)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "GEMINI 64K ENGINE",
                        color = Color.White,
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Black,
                            fontSize = 24.sp,
                            letterSpacing = 4.sp
                        ),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .background(CyanNeon.copy(0.12f), RoundedCornerShape(8.dp))
                            .border(1.dp, CyanNeon.copy(0.3f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = processStatus.uppercase(),
                            color = CyanNeon,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    LinearProgressIndicator(
                        color = CyanNeon,
                        trackColor = Color.White.copy(0.08f),
                        modifier = Modifier
                            .width(220.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = if (zoom >= 5f) "RECONSTRUCTING DETAILS VIA AI TELEPHOTO" else "SIMULATING 0.0001MM PITCH • DEEP AI SHARPENING",
                        color = Color.White.copy(0.4f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.5.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Full Screen Image Viewer Modal
        selectedPhotoForDetail?.let { photo ->
            DetailPhotoDialog(
                photo = photo,
                onDismiss = { selectedPhotoForDetail = null },
                onDelete = {
                    viewModel.deletePhoto(photo)
                    selectedPhotoForDetail = null
                    viewModel.showToast("Masterpiece deleted.")
                }
            )
        }
    }
}

@Composable
fun HeaderSection(
    showGrid: Boolean,
    onToggleGrid: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Slate950)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Slate900)
                        .border(1.dp, Color.White.copy(0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = "Sensor",
                        tint = CyanNeon,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "TITANIUM AI",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        letterSpacing = 3.sp
                    )
                    Text(
                        text = "PRO CAM V3",
                        color = Color(0xFF3B82F6),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                }
            }

            // Action capsules
            Row(
                modifier = Modifier
                    .background(Slate900, CircleShape)
                    .border(1.dp, Color.White.copy(0.08f), CircleShape)
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onToggleGrid,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (showGrid) Color.Black else Color.Transparent)
                ) {
                    Icon(
                        imageVector = Icons.Default.Grid3x3,
                        contentDescription = "Toggle Grid",
                        tint = if (showGrid) CyanNeon else Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        HorizontalDivider(color = Color.White.copy(0.05f))
    }
}

@Composable
fun ViewfinderSection(
    lensFacing: Int,
    zoom: Float,
    hardwareZoom: Float,
    isFlashing: Boolean,
    flashMode: Int,
    showGrid: Boolean,
    onCameraControlReady: (CameraControl, CameraInfo) -> Unit,
    imageCaptureInstance: (ImageCapture) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        CameraPreview(
            modifier = Modifier.fillMaxSize(),
            lensFacing = lensFacing,
            zoomRatio = zoom,
            flashMode = flashMode,
            onCameraControlReady = onCameraControlReady,
            imageCaptureInstance = imageCaptureInstance
        )

        // 3x3 Overlay Grid
        if (showGrid) {
            Box(modifier = Modifier.fillMaxSize()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // Verticals
                    drawLine(Color.White.copy(0.25f), Offset(w / 3f, 0f), Offset(w / 3f, h), strokeWidth = 1f)
                    drawLine(Color.White.copy(0.25f), Offset(w * 2 / 3f, 0f), Offset(w * 2 / 3f, h), strokeWidth = 1f)

                    // Horizontals
                    drawLine(Color.White.copy(0.25f), Offset(0f, h / 3f), Offset(w, h / 3f), strokeWidth = 1f)
                    drawLine(Color.White.copy(0.25f), Offset(0f, h * 2 / 3f), Offset(w, h * 2 / 3f), strokeWidth = 1f)
                }
            }
        }

        // Target Brackets in Center
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(72.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val len = 14.dp.toPx()
                val color = if (zoom >= 5f) CyanNeon else Color.White

                // Top Left
                drawLine(color, Offset(0f, 0f), Offset(len, 0f), strokeWidth = 2.dp.toPx())
                drawLine(color, Offset(0f, 0f), Offset(0f, len), strokeWidth = 2.dp.toPx())

                // Top Right
                drawLine(color, Offset(size.width, 0f), Offset(size.width - len, 0f), strokeWidth = 2.dp.toPx())
                drawLine(color, Offset(size.width, 0f), Offset(size.width, len), strokeWidth = 2.dp.toPx())

                // Bottom Left
                drawLine(color, Offset(0f, size.height), Offset(len, size.height), strokeWidth = 2.dp.toPx())
                drawLine(color, Offset(0f, size.height), Offset(0f, size.height - len), strokeWidth = 2.dp.toPx())

                // Bottom Right
                drawLine(color, Offset(size.width, size.height), Offset(size.width - len, size.height), strokeWidth = 2.dp.toPx())
                drawLine(color, Offset(size.width, size.height), Offset(size.width, size.height - len), strokeWidth = 2.dp.toPx())
            }
        }

        // Active AI Telephoto pill
        if (zoom >= 5f) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .background(Color(0xFF0F172A).copy(0.85f), RoundedCornerShape(6.dp))
                    .border(BorderStroke(1.dp, CyanNeon), RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "AI Active",
                        tint = CyanNeon,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "AI TELEPHOTO ACTIVE",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Top-Right Current Zoom Factor
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .background(Color.Black.copy(0.55f), RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = "${String.format("%.1f", zoom)}X ZOOM",
                color = Color.White,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }

        // Capture Flash simulation pane
        if (isFlashing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
            )
        }
    }
}

@Composable
fun ControlButtonsSection(
    zoom: Float,
    isNightMode: Boolean,
    isHyperClarity: Boolean,
    flashMode: Int,
    onZoomChange: (Float) -> Unit,
    onToggleNightMode: () -> Unit,
    onToggleHyperClarity: () -> Unit,
    onToggleFlash: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Zoom control capsule
        val dynamicBorderColor = if (zoom >= 5f) CyanNeon.copy(0.5f) else Color.White.copy(0.08f)
        val dynamicBgColor = if (zoom >= 5f) Color(0xFF0F172A).copy(0.6f) else Slate950.copy(0.8f)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(dynamicBgColor, RoundedCornerShape(42.dp))
                .border(1.dp, dynamicBorderColor, RoundedCornerShape(42.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = { onZoomChange((zoom - 1f).coerceAtLeast(1f)) },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(Icons.Default.Remove, "Zoom out", tint = Color.Gray, modifier = Modifier.size(16.dp))
            }

            Slider(
                value = zoom,
                onValueChange = onZoomChange,
                valueRange = 1f..20f,
                colors = SliderDefaults.colors(
                    thumbColor = if (zoom >= 5f) CyanNeon else Color.White,
                    activeTrackColor = if (zoom >= 5f) CyanNeon else Color.White.copy(0.7f),
                    inactiveTrackColor = Color.White.copy(0.12f)
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )

            IconButton(
                onClick = { onZoomChange((zoom + 1f).coerceAtMost(20f)) },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(Icons.Default.Add, "Zoom in", tint = Color.Gray, modifier = Modifier.size(16.dp))
            }

            Text(
                text = "${String.format("%.1f", zoom)}X",
                color = if (zoom >= 5f) CyanNeon else Color.White,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(42.dp),
                textAlign = TextAlign.End
            )
        }

        // Filter / Flash Mode Toggles Capsule
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Slate950, RoundedCornerShape(16.dp))
                .border(1.dp, Color.White.copy(0.05f), RoundedCornerShape(16.dp))
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Flash Mode trigger button
            val (flashIcon, flashLabel) = when (flashMode) {
                ImageCapture.FLASH_MODE_ON -> Pair(Icons.Default.FlashOn, "ON")
                ImageCapture.FLASH_MODE_AUTO -> Pair(Icons.Default.FlashAuto, "AUTO")
                else -> Pair(Icons.Default.FlashOff, "OFF")
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onToggleFlash() }
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = flashIcon,
                    contentDescription = "Flash Mode",
                    tint = if (flashMode == ImageCapture.FLASH_MODE_OFF) Color.Gray else GoldNite,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = flashLabel,
                    color = if (flashMode == ImageCapture.FLASH_MODE_OFF) Color.Gray else Color.White,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Night Mode toggle button
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onToggleNightMode() }
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Brightness2,
                    contentDescription = "Night Mode",
                    tint = if (isNightMode) GoldNite else Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "NIGHT",
                    color = if (isNightMode) Color.White else Color.Gray,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Hyper Clarity toggle button
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onToggleHyperClarity() }
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoFixHigh,
                    contentDescription = "Hyper Clarity",
                    tint = if (isHyperClarity) CyanNeon else Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "CLARITY",
                    color = if (isHyperClarity) Color.White else Color.Gray,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun ShutterBarSection(
    latestPhoto: PhotoEntity?,
    isAiProcessing: Boolean,
    scaleMode: Boolean,
    onTakePhoto: () -> Unit,
    onToggleCamera: () -> Unit,
    onGalleryClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp, start = 24.dp, end = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail Preview
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(2.dp, Color.White.copy(0.12f), RoundedCornerShape(12.dp))
                .background(Color.Black)
                .clickable { onGalleryClick() },
            contentAlignment = Alignment.Center
        ) {
            if (latestPhoto != null) {
                AsyncImage(
                    model = File(latestPhoto.filePath),
                    contentDescription = "Latest capture",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = "Gallery Empty",
                    tint = Color.DarkGray,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Shutter triggering pad
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .clickable(enabled = !isAiProcessing) { onTakePhoto() }
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color.White, Color.Gray)
                    )
                )
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(Slate950)
                    .padding(4.dp)
            ) {
                val circleColor = if (scaleMode) CyanNeon else Color.White
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(circleColor)
                )
            }
        }

        // Camera Lens Switcher
        IconButton(
            onClick = onToggleCamera,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Slate950)
                .border(1.dp, Color.White.copy(0.08f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.FlipCameraAndroid,
                contentDescription = "Rotate Sensor",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun GalleryPanel(
    photos: List<PhotoEntity>,
    onPhotoClick: (PhotoEntity) -> Unit,
    onClearAll: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 8.dp)
    ) {
        // Gallery Title header card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Studio",
                    tint = CyanNeon,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "AI STUDIO GALLERY",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }

            if (photos.isNotEmpty()) {
                Text(
                    text = "CLEAR ALL",
                    color = Color.Red.copy(0.6f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onClearAll() }
                        .padding(4.dp)
                )
            }
        }

        if (photos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.alpha(0.40f)
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = "Apperture",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "CLICK TO REGENERATE\nWITH AI ENGINE",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(photos, key = { it.id }) { photo ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, Color.White.copy(0.08f), RoundedCornerShape(12.dp))
                            .background(Color.Black)
                            .clickable { onPhotoClick(photo) }
                    ) {
                        AsyncImage(
                            model = File(photo.filePath),
                            contentDescription = "AI Art",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        // 64K Badge tags
                        if (photo.isAiUpscaled) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(4.dp)
                                    .background(Color.Black.copy(0.7f), RoundedCornerShape(4.dp))
                                    .border(BorderStroke(0.5.dp, CyanNeon.copy(0.4f)), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = "Upscaled",
                                        tint = CyanNeon,
                                        modifier = Modifier.size(8.dp)
                                    )
                                    Text(
                                        text = "64K",
                                        color = CyanNeon,
                                        fontSize = 7.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DetailPhotoDialog(
    photo: PhotoEntity,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(0.95f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .clickable(enabled = false) {}
                .padding(16.dp)
                .widthIn(max = 480.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Head Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Close", tint = Color.White)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Share / Download actions
                    IconButton(
                        onClick = {
                            try {
                                val file = File(photo.filePath)
                                val uri: Uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.provider",
                                    file
                                )
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/jpeg"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share Ultra 64K Photo"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error sharing photo.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Share, "Share", tint = CyanNeon)
                    }

                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, "Delete", tint = Color.Red)
                    }
                }
            }

            // Big Photo Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(16.dp))
                    .background(Slate900),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = File(photo.filePath),
                    contentDescription = "High-Res AI Output",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Photo specs information panel card
            Card(
                colors = CardDefaults.cardColors(containerColor = Slate900),
                border = BorderStroke(1.dp, Color.White.copy(0.08f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "SENSOR SPECS METADATA",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )

                    HorizontalDivider(color = Color.White.copy(0.08f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Capture Mode:", color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Text(
                            text = if (photo.isAiUpscaled) "Gemini AI 64K Masterpiece" else "Direct Raw capture",
                            color = if (photo.isAiUpscaled) CyanNeon else Color.White,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Lens Option:", color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Text(
                            text = "${String.format("%.1f", photo.zoom)}X Zoom",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Applied Matrix:", color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        val matrixText = when {
                            photo.isNightMode -> "Night Vision Sensor active"
                            photo.isHyperClarity -> "Hyper Clarity enabled"
                            else -> "Standard Optical Matrix"
                        }
                        Text(
                            text = matrixText,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

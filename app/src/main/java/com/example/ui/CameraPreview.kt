package com.example.ui

import android.content.Context
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalLifecycleOwner
import java.util.concurrent.Executor

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    lensFacing: Int,
    zoomRatio: Float,
    flashMode: Int,
    onCameraControlReady: (CameraControl, CameraInfo) -> Unit,
    imageCaptureInstance: (ImageCapture) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setTargetRotation(previewView.display?.rotation ?: android.view.Surface.ROTATION_0)
            .setFlashMode(flashMode)
            .build()
    }

    LaunchedEffect(flashMode) {
        imageCapture.flashMode = flashMode
    }

    LaunchedEffect(lensFacing) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            
            // Preview Use Case
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            // Selector Setup
            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            try {
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
                
                onCameraControlReady(camera.cameraControl, camera.cameraInfo)
                imageCaptureInstance(imageCapture)
                
                // Track baseline zoom info
                camera.cameraInfo.zoomState.value?.let { state ->
                    camera.cameraControl.setZoomRatio(zoomRatio.coerceIn(state.minZoomRatio, state.maxZoomRatio))
                }
            } catch (_: Exception) {
                // Ignore initialization state issues safely
            }
        }, mainExecutor)
    }

    LaunchedEffect(zoomRatio) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()
                
                // We just want to query active camera zoom bounds
                val camera = cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector)
                camera.cameraInfo.zoomState.value?.let { state ->
                    val finalVal = zoomRatio.coerceIn(state.minZoomRatio, state.maxZoomRatio)
                    camera.cameraControl.setZoomRatio(finalVal)
                }
            } catch (_: Exception) {}
        }, mainExecutor)
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}

fun captureImage(
    imageCapture: ImageCapture?,
    executor: Executor,
    onImageCaptured: (ByteArray) -> Unit,
    onError: (ImageCaptureException) -> Unit
) {
    if (imageCapture == null) {
        onError(ImageCaptureException(ImageCapture.ERROR_UNKNOWN, "Camera preview is not active.", null))
        return
    }

    imageCapture.takePicture(
        executor,
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                image.close()
                onImageCaptured(bytes)
            }

            override fun onError(exception: ImageCaptureException) {
                onError(exception)
            }
        }
    )
}

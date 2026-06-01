package com.example.ui

import android.app.Application
import android.util.Base64
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CameraViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PhotoRepository(application)

    // Base States
    val photos: StateFlow<List<PhotoEntity>> = repository.allPhotos
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _isInitializing = MutableStateFlow(false)
    val isInitializing = _isInitializing.asStateFlow()

    private val _isAiProcessing = MutableStateFlow(false)
    val isAiProcessing = _isAiProcessing.asStateFlow()

    private val _processStatus = MutableStateFlow("")
    val processStatus = _processStatus.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage = _toastMessage.asStateFlow()

    // Camera Lens selection (back vs front)
    private val _lensFacing = MutableStateFlow(CameraSelector.LENS_FACING_BACK)
    val lensFacing = _lensFacing.asStateFlow()

    // Smooth total Zoom (1.0x to 20.0x)
    private val _zoom = MutableStateFlow(1.0f)
    val zoom = _zoom.asStateFlow()

    // Standard applied camera hardware zoom
    private val _hardwareZoom = MutableStateFlow(1.0f)
    val hardwareZoom = _hardwareZoom.asStateFlow()

    // Layout configuration and status
    private val _showGrid = MutableStateFlow(true)
    val showGrid = _showGrid.asStateFlow()

    private val _isFlashing = MutableStateFlow(false)
    val isFlashing = _isFlashing.asStateFlow()

    private val _flashMode = MutableStateFlow(ImageCapture.FLASH_MODE_OFF)
    val flashMode = _flashMode.asStateFlow()

    private val _isNightMode = MutableStateFlow(false)
    val isNightMode = _isNightMode.asStateFlow()

    private val _isHyperClarity = MutableStateFlow(false)
    val isHyperClarity = _isHyperClarity.asStateFlow()

    fun dismissToast() {
        _toastMessage.value = null
    }

    fun showToast(msg: String) {
        viewModelScope.launch {
            _toastMessage.value = msg
        }
    }

    fun setZoom(value: Float) {
        _zoom.value = value.coerceIn(1.0f, 20.0f)
    }

    fun setHardwareZoom(value: Float) {
        _hardwareZoom.value = value
    }

    fun toggleCamera() {
        _lensFacing.value = if (_lensFacing.value == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        // Reset zoom on lens switch
        _zoom.value = 1.0f
        _hardwareZoom.value = 1.0f
    }

    fun toggleGrid() {
        _showGrid.value = !_showGrid.value
    }

    fun toggleNightMode() {
        _isNightMode.value = !_isNightMode.value
        if (_isNightMode.value) {
            _isHyperClarity.value = false
        }
    }

    fun toggleHyperClarity() {
        _isHyperClarity.value = !_isHyperClarity.value
        if (_isHyperClarity.value) {
            _isNightMode.value = false
        }
    }

    fun toggleFlash() {
        _flashMode.value = when (_flashMode.value) {
            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
            ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
            else -> ImageCapture.FLASH_MODE_OFF
        }
    }

    fun deletePhoto(photo: PhotoEntity) {
        viewModelScope.launch {
            repository.delete(photo)
        }
    }

    fun clearAllPhotos() {
        viewModelScope.launch {
            repository.deleteAll()
        }
    }

    fun processCapturedImage(rawBytes: ByteArray) {
        viewModelScope.launch {
            _isFlashing.value = true
            kotlinx.coroutines.delay(100)
            _isFlashing.value = false

            _isAiProcessing.value = true
            _processStatus.value = "Raw image captured..."

            val zoomVal = _zoom.value
            val isNight = _isNightMode.value
            val isClarity = _isHyperClarity.value

            try {
                val apiKey = try { BuildConfig.GEMINI_API_KEY } catch (_: Exception) { "" }

                if (apiKey.isEmpty() || apiKey == "your_gemini_api_key_here") {
                    // Fallback directly
                    kotlinx.coroutines.delay(800)
                    saveFallbackAndInsert(rawBytes, zoomVal, isNight, isClarity, "AI Key Missing: Saved Original")
                    return@launch
                }

                _processStatus.value = "AI Telephoto / 64K Engine Running..."

                // Run Retrofit call to Gemini Client with a retry builder
                val base64Data = Base64.encodeToString(rawBytes, Base64.NO_WRAP)
                
                val promptText = if (zoomVal >= 5f) {
                    "This is a 20x digitally zoomed photo. Use AI Telephoto algorithms to reconstruct missing details, remove blur, and make it incredibly sharp. Output a hyper-realistic, 64k Ultra HDR image with perfect optical clarity."
                } else {
                    "Transform this image into a hyper-realistic, 64k ultra-high-definition masterpiece. Enhance colors to be vibrant Ultra HDR, sharpen every detail to a 0.0001mm dot pitch accuracy. Keep the original scene and subjects exactly the same."
                }

                val request = GenerateContentRequest(
                    contents = listOf(
                        Content(
                            parts = listOf(
                                Part(text = promptText),
                                Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Data))
                            )
                        )
                    ),
                    generationConfig = GenerationConfig(
                        responseModalities = listOf("TEXT", "IMAGE")
                    )
                )

                val response = withContext(Dispatchers.IO) {
                    fetchWithRetry(retries = 3) {
                        RetrofitClient.service.generateContent(
                            model = "gemini-2.5-flash-image",
                            apiKey = apiKey,
                            request = request
                        )
                    }
                }

                _processStatus.value = "Saving 64K AI Masterpiece..."

                val candidateParts = response.candidates?.firstOrNull()?.content?.parts
                val finalImageBase64 = candidateParts?.find { it.inlineData != null }?.inlineData?.data

                if (!finalImageBase64.isNullOrEmpty()) {
                    val enhancedBytes = Base64.decode(finalImageBase64, Base64.DEFAULT)
                    val filename = "ai_64k_${System.currentTimeMillis()}.jpg"
                    val filePath = repository.saveImageToDisk(enhancedBytes, filename)
                    
                    val photoEntity = PhotoEntity(
                        filePath = filePath,
                        zoom = zoomVal,
                        isNightMode = isNight,
                        isHyperClarity = isClarity,
                        isAiUpscaled = true
                    )
                    repository.insert(photoEntity)
                    _toastMessage.value = "64K AI Image Generated!"
                } else {
                    // No image returned, fall back to writing original raw bytes
                    saveFallbackAndInsert(rawBytes, zoomVal, isNight, isClarity, "AI returned text. Captured original.")
                }

            } catch (e: Exception) {
                saveFallbackAndInsert(rawBytes, zoomVal, isNight, isClarity, "AI Timeout: Original Saved.")
            } finally {
                _isAiProcessing.value = false
                _processStatus.value = ""
            }
        }
    }

    private suspend fun saveFallbackAndInsert(
        rawBytes: ByteArray,
        zoomVal: Float,
        isNight: Boolean,
        isClarity: Boolean,
        toastResponse: String
    ) {
        val filename = "raw_${System.currentTimeMillis()}.jpg"
        val filePath = repository.saveImageToDisk(rawBytes, filename)
        val photoEntity = PhotoEntity(
            filePath = filePath,
            zoom = zoomVal,
            isNightMode = isNight,
            isHyperClarity = isClarity,
            isAiUpscaled = false
        )
        repository.insert(photoEntity)
        _toastMessage.value = toastResponse
    }

    private suspend fun <T> fetchWithRetry(retries: Int, block: suspend () -> T): T {
        var exception: Exception? = null
        var delayMs = 1500L
        for (i in 0 until retries) {
            try {
                return block()
            } catch (e: Exception) {
                exception = e
                kotlinx.coroutines.delay(delayMs)
                delayMs *= 2
            }
        }
        throw exception ?: Exception("Gemini request failed")
    }
}

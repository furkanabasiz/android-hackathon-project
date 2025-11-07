package com.hackathon.smilehairclinic.ui.customer

import android.Manifest
import android.R
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.hackathon.smilehairclinic.databinding.FragmentCameraBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs

class CameraFragment : Fragment() {

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!

    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null

    // ML Kit Face Detection
    private val faceDetector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
            .build()
        FaceDetection.getClient(options)
    }

    // Sesli Bildirim
    private var toneGenerator: ToneGenerator? = null
    private var beepJob: Job? = null
    private var currentBeepInterval = 1000L

    // Durum Takibi
    private var isAligned = false
    private var countdownJob: Job? = null
    private var isCapturing = false

    // Firebase
    private val storage = FirebaseStorage.getInstance()
    private val auth = FirebaseAuth.getInstance()

    companion object {
        private const val TAG = "CameraFragment"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Ses üreteci başlat
        toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)

        // Kamera izni kontrolü
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions(REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                binding.tvGuidance.text = "Kamera izni gerekli"
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }

            // Image Capture
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()

            // Image Analysis (Yüz Tespiti için)
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImageProxy(imageProxy)
                    }
                }

            // Arka kamera seç
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()

                camera = cameraProvider.bindToLifecycle(
                    viewLifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture,
                    imageAnalyzer
                )

            } catch (exc: Exception) {
                Log.e(TAG, "Kamera başlatma hatası", exc)
            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }

    @ExperimentalGetImage
    private fun processImageProxy(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null && !isCapturing) {
            val image = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )

            faceDetector.process(image)
                .addOnSuccessListener { faces ->
                    analyzeFaces(faces)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Yüz tespiti hatası", e)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    private fun analyzeFaces(faces: List<Face>) {
        lifecycleScope.launch(Dispatchers.Main) {
            if (faces.isEmpty()) {
                updateGuidance("Yüz bulunamadı", false)
                stopBeeping()
                return@launch
            }

            val face = faces.first()
            val faceBounds = face.boundingBox

            // Ekran boyutları
            val previewWidth = binding.previewView.width.toFloat()
            val previewHeight = binding.previewView.height.toFloat()

            // Hedef oval sınırları
            val targetBounds = binding.faceOverlay.getTargetOvalBounds()

            // Yüz merkezi
            val faceCenterX = faceBounds.exactCenterX()
            val faceCenterY = faceBounds.exactCenterY()

            // Hedef merkezi
            val targetCenterX = targetBounds.centerX()
            val targetCenterY = targetBounds.centerY()

            // Yüz boyutu
            val faceWidth = faceBounds.width().toFloat()
            val faceHeight = faceBounds.height().toFloat()

            // Hedef boyutları
            val targetWidth = targetBounds.width()
            val targetHeight = targetBounds.height()

            // Mesafe hesaplama (merkez arasındaki)
            val distanceX = abs(faceCenterX - targetCenterX)
            val distanceY = abs(faceCenterY - targetCenterY)
            val totalDistance = distanceX + distanceY

            // Boyut uyumu kontrolü
            val widthRatio = faceWidth / targetWidth
            val heightRatio = faceHeight / targetHeight

            // Durum analizi
            when {
                widthRatio < 0.4 || heightRatio < 0.4 -> {
                    updateGuidance("Lütfen yaklaşın", false)
                    adjustBeepFrequency(totalDistance, previewWidth)
                }
                widthRatio > 0.9 || heightRatio > 0.9 -> {
                    updateGuidance("Lütfen uzaklaşın", false)
                    adjustBeepFrequency(totalDistance, previewWidth)
                }
                distanceX > targetWidth * 0.2 || distanceY > targetHeight * 0.2 -> {
                    updateGuidance("Lütfen yüzünüzü hizalayın", false)
                    adjustBeepFrequency(totalDistance, previewWidth)
                }
                else -> {
                    updateGuidance("Hazır!", true)
                    adjustBeepFrequency(totalDistance, previewWidth)

                    if (!isAligned) {
                        isAligned = true
                        startCountdown()
                    }
                }
            }
        }
    }

    private fun updateGuidance(message: String, aligned: Boolean) {
        binding.tvGuidance.text = message
        binding.faceOverlay.isAligned = aligned

        if (!aligned && isAligned) {
            isAligned = false
            cancelCountdown()
        }
    }

    private fun adjustBeepFrequency(distance: Float, screenWidth: Float) {
        // Mesafeye göre bip aralığını ayarla (0.2s - 1s arası)
        val normalizedDistance = (distance / screenWidth).coerceIn(0f, 1f)
        val newInterval = (200 + (800 * normalizedDistance)).toLong()

        if (abs(newInterval - currentBeepInterval) > 50) {
            currentBeepInterval = newInterval
            restartBeeping()
        }
    }

    private fun restartBeeping() {
        beepJob?.cancel()
        beepJob = lifecycleScope.launch {
            while (isActive) {
                playBeep()
                delay(currentBeepInterval)
            }
        }
    }

    private fun playBeep() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
        } catch (e: Exception) {
            Log.e(TAG, "Ses çalma hatası", e)
        }
    }

    private fun stopBeeping() {
        beepJob?.cancel()
        beepJob = null
    }

    private fun startCountdown() {
        cancelCountdown()

        countdownJob = lifecycleScope.launch {
            binding.tvCountdown.visibility = View.VISIBLE
            stopBeeping()

            for (i in 3 downTo 1) {
                if (!isActive) break
                binding.tvCountdown.text = i.toString()
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
                delay(1000)
            }

            if (isActive && isAligned) {
                binding.tvCountdown.text = "📸"
                capturePhoto()
                delay(500)
                binding.tvCountdown.visibility = View.GONE
            }
            // CameraFragment.kt (devamı)

        }
    }

    private fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        binding.tvCountdown.visibility = View.GONE
    }

    private fun capturePhoto() {
        val imageCapture = imageCapture ?: return
        isCapturing = true
        stopBeeping()

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    lifecycleScope.launch {
                        try {
                            // ImageProxy'yi byte array'e çevir
                            val buffer = imageProxy.planes[0].buffer
                            val bytes = ByteArray(buffer.remaining())
                            buffer.get(bytes)

                            // Firebase'e yükle
                            uploadImageToFirebase(bytes)
                        } catch (e: Exception) {
                            Log.e(TAG, "Fotoğraf işleme hatası", e)
                            showUploadStatus("Hata: ${e.message}", false)
                        } finally {
                            imageProxy.close()
                            isCapturing = false
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Fotoğraf çekme hatası", exception)
                    lifecycleScope.launch {
                        showUploadStatus("Çekim hatası", false)
                        isCapturing = false
                    }
                }
            }
        )
    }

    private suspend fun uploadImageToFirebase(imageBytes: ByteArray) = withContext(Dispatchers.IO) {
        try {
            // Kullanıcı kimliği al
            val userId = auth.currentUser?.uid ?: run {
                withContext(Dispatchers.Main) {
                    showUploadStatus("Kullanıcı oturum açmamış", false)
                }
                return@withContext
            }

            // Firebase Storage referansı oluştur
            val timestamp = System.currentTimeMillis()
            val fileName = "capture_$timestamp.jpg"
            val storageRef = storage.reference
                .child("users")
                .child(userId)
                .child("captures")
                .child(fileName)

            // UI güncellemesi
            withContext(Dispatchers.Main) {
                binding.progressBar.visibility = View.VISIBLE
                binding.tvUploadStatus.visibility = View.VISIBLE
                binding.tvUploadStatus.text = "Yükleniyor..."
            }

            // Byte array'i yükle
            val uploadTask = storageRef.putBytes(imageBytes)

            uploadTask.addOnProgressListener { taskSnapshot ->
                val progress =
                    (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount).toInt()
                lifecycleScope.launch(Dispatchers.Main) {
                    binding.tvUploadStatus.text = "Yükleniyor: $progress%"
                }
            }.addOnSuccessListener { taskSnapshot ->
                lifecycleScope.launch(Dispatchers.Main) {
                    // Download URL al
                    storageRef.downloadUrl.addOnSuccessListener { uri ->
                        Log.d(TAG, "Yükleme başarılı: $uri")
                        showUploadStatus("✓ Başarıyla yüklendi!", true)

                        // 2 saniye sonra tekrar çekim için hazır hale gel
                        lifecycleScope.launch {
                            delay(2000)
                            resetForNewCapture()
                        }
                    }
                }
            }.addOnFailureListener { exception ->
                lifecycleScope.launch(Dispatchers.Main) {
                    Log.e(TAG, "Yükleme hatası", exception)
                    showUploadStatus("Yükleme hatası: ${exception.message}", false)

                    // 2 saniye sonra tekrar dene
                    lifecycleScope.launch {
                        delay(2000)
                        resetForNewCapture()
                    }
                }
            }

        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Log.e(TAG, "Firebase yükleme hatası", e)
                showUploadStatus("Hata: ${e.message}", false)
            }
        }
    }

    private fun showUploadStatus(message: String, success: Boolean) {
        binding.tvUploadStatus.visibility = View.VISIBLE
        binding.tvUploadStatus.text = message
        binding.tvUploadStatus.setTextColor(
            if (success)
                ContextCompat.getColor(requireContext(), R.color.holo_green_light)
            else
                ContextCompat.getColor(requireContext(), R.color.holo_red_light)
        )
    }

    private fun resetForNewCapture() {
        binding.progressBar.visibility = View.GONE
        binding.tvUploadStatus.visibility = View.GONE
        binding.tvGuidance.text = "Lütfen yüzünüzü hizalayın"
        binding.tvCountdown.visibility = View.GONE
        isAligned = false
        isCapturing = false
        restartBeeping()
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // Cleanup
        stopBeeping()
        cancelCountdown()
        beepJob?.cancel()

        toneGenerator?.release()
        toneGenerator = null

        cameraExecutor.shutdown()

        faceDetector.close()

        _binding = null
    }
}
package com.hackathon.smilehairclinic.ui.customer

import android.Manifest
import android.app.ProgressDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.hackathon.smilehairclinic.FirebaseUploadManager
import com.hackathon.smilehairclinic.R
import com.hackathon.smilehairclinic.databinding.ActivityCameraCaptureBinding
import com.hackathon.smilehairclinic.model.CaptureMode
import com.hackathon.smilehairclinic.model.CaptureModes
import com.hackathon.smilehairclinic.utils.NetworkUtils
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs

class CameraCaptureActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var binding: ActivityCameraCaptureBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var sensorManager: SensorManager
    private lateinit var vibrator: Vibrator
    private lateinit var toneGenerator: ToneGenerator
    private lateinit var firebaseUploadManager: FirebaseUploadManager
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var imageAnalyzer: ImageAnalysis? = null

    private var currentPitch: Float = 0f

    private val faceDetector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .build()
        FaceDetection.getClient(options)
    }

    private var currentModeIndex = 0
    private val capturedPhotos = mutableMapOf<CaptureMode, File>()
    private var isCapturing = false
    private var isFinished = false
    
    // Timers
    private var autoCaptureTimer: CountDownTimer? = null
    private var manualCaptureButtonTimer: CountDownTimer? = null

    private var isPositionCorrect = false
    private var detectedFace: Face? = null
    private var lastBeepTime: Long = 0

    companion object {
        private const val TAG = "CameraCapture"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraCaptureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!NetworkUtils.isNetworkAvailable(this)) {
            showNoInternetDialog()
            return
        }

        initializeComponents()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        setupClickListeners()
        updateUI()
    }

    private fun showNoInternetDialog() {
        AlertDialog.Builder(this)
            .setTitle("İnternet Bağlantısı Yok")
            .setMessage("Fotoğraf yüklemek için internet bağlantısı gereklidir.")
            .setPositiveButton("Tamam") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun initializeComponents() {
        cameraExecutor = Executors.newSingleThreadExecutor()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        firebaseUploadManager = FirebaseUploadManager()

        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    private fun setupClickListeners() {
        binding.btnManualCapture.setOnClickListener { 
            if (!isCapturing) {
                autoCaptureTimer?.cancel()
                manualCaptureButtonTimer?.cancel()
                capturePhoto()
            }
        }
    }

    private fun getCurrentMode(): CaptureMode? {
        return CaptureModes.getAllModes().getOrNull(currentModeIndex)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases(cameraProvider)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases(cameraProvider: ProcessCameraProvider) {
        val currentMode = getCurrentMode() ?: return

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
        }

        imageCapture = ImageCapture.Builder().build()

        imageAnalyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { it.setAnalyzer(cameraExecutor) { imageProxy -> processImageForFaceDetection(imageProxy) } }

        val cameraSelector = if (currentMode.useFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA

        try {
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, imageAnalyzer)
        } catch (e: Exception) {
            Log.e(TAG, "Use case binding failed", e)
        }
    }

    @ExperimentalGetImage
    private fun processImageForFaceDetection(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: run { imageProxy.close(); return }
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        faceDetector.process(image)
            .addOnSuccessListener { faces ->
                detectedFace = faces.firstOrNull()
                checkPositionAndAutoCapture()
            }
            .addOnFailureListener { e -> Log.e(TAG, "Face detection failed", e) }
            .addOnCompleteListener { imageProxy.close() }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            currentPitch = Math.toDegrees(Math.atan2(event.values[1].toDouble(), event.values[2].toDouble())).toFloat()
            updateAngleDisplay()
            checkPositionAndAutoCapture()
        }
    }

    private fun updateAngleDisplay() {
        val currentMode = getCurrentMode() ?: return
        binding.tvPitchAngle.text = "${currentPitch.toInt()}°"
        val pitchCorrect = abs(currentPitch - currentMode.targetPitch) <= currentMode.toleranceDegrees
        binding.ivPitchStatus.setColorFilter(if (pitchCorrect) ContextCompat.getColor(this, R.color.green) else ContextCompat.getColor(this, R.color.red))
    }

    private fun checkPositionAndAutoCapture() {
        if (isCapturing) return // Do not check or play sounds during countdown/capture
        val currentMode = getCurrentMode() ?: return
        
        val deviation = abs(currentPitch - currentMode.targetPitch)
        val tolerance = currentMode.toleranceDegrees
        val faceCorrect = !currentMode.requiresFaceDetection || (detectedFace != null)
        
        playProximityBeep(deviation, tolerance, faceCorrect) // Play audio feedback based on proximity

        val isNowCorrect = deviation <= tolerance && faceCorrect

        if (isNowCorrect && !isPositionCorrect) {
            // Position has just become correct
            manualCaptureButtonTimer?.cancel()
            binding.btnManualCapture.visibility = View.GONE
            startAutoCapture()
        } else if (!isNowCorrect && isPositionCorrect) {
            // Position has just become incorrect
            autoCaptureTimer?.cancel()
            autoCaptureTimer = null
            startManualCaptureTimer()
        }
        
        isPositionCorrect = isNowCorrect

        updateFeedback(when {
            deviation > tolerance -> "Telefonu ${if (currentPitch < currentMode.targetPitch) "yukarı kaldırın" else "aşağı indirin"}"
            !faceCorrect -> "Yüzünüzü kameraya gösterin"
            else -> "Mükemmel! Sabit tutun..."
        })
    }

    private fun playProximityBeep(deviation: Float, tolerance: Float, faceCorrect: Boolean) {
        val currentTime = System.currentTimeMillis()
        val tone: Int
        val interval: Long

        when {
            // Correct position
            deviation <= tolerance * 1.5 && faceCorrect -> {
                tone = ToneGenerator.TONE_PROP_BEEP2
                interval = 250L // Fastest beep
            }
            // Near position
            deviation <= tolerance * 3 && faceCorrect -> {
                tone = ToneGenerator.TONE_PROP_PROMPT
                interval = 500L // Medium beep
            }
            // Far position
            else -> {
                tone = ToneGenerator.TONE_PROP_BEEP
                interval = 1000L // Slowest beep
            }
        }

        if (currentTime - lastBeepTime > interval) {
            toneGenerator.startTone(tone, 80)
            lastBeepTime = currentTime
        }
    }

    private fun updateFeedback(message: String) {
        runOnUiThread {
            binding.tvPositionFeedback.text = message
            binding.tvPositionFeedback.setBackgroundColor(
                if (isPositionCorrect) ContextCompat.getColor(this, R.color.green_transparent)
                else ContextCompat.getColor(this, R.color.yellow_transparent)
            )
        }
    }

    private fun startAutoCapture() {
        if (autoCaptureTimer != null) return
        isCapturing = true

        runOnUiThread {
            binding.countdownContainer.visibility = View.VISIBLE
            vibrate(100)
            // Stop any guiding beeps and start a continuous tone for the countdown
            toneGenerator.stopTone()
            toneGenerator.startTone(ToneGenerator.TONE_CDMA_ABBR_REORDER, 5000)
        }

        autoCaptureTimer = object : CountDownTimer(3000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                binding.countdownContainer.text = ((millisUntilFinished / 1000) + 1).toString()
            }

            override fun onFinish() {
                capturePhoto()
            }
        }.start()
    }

    private fun startManualCaptureTimer() {
        manualCaptureButtonTimer?.cancel()
        manualCaptureButtonTimer = object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {}

            override fun onFinish() {
                if (!isPositionCorrect) { 
                    binding.btnManualCapture.visibility = View.VISIBLE
                }
            }
        }.start()
    }

    private fun capturePhoto() {
        val imageCapture = imageCapture ?: return
        val currentMode = getCurrentMode() ?: return
        val photoFile = File(externalMediaDirs.firstOrNull(), "${currentMode.id}_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        // Stop all sounds and timers
        toneGenerator.stopTone()
        autoCaptureTimer?.cancel()
        autoCaptureTimer = null
        manualCaptureButtonTimer?.cancel()
        manualCaptureButtonTimer = null

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) {
                Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                isCapturing = false
            }

            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                capturedPhotos[currentMode] = photoFile
                vibrate(100, 50, 100)
                Toast.makeText(this@CameraCaptureActivity, "${currentMode.title} fotoğrafı çekildi!", Toast.LENGTH_SHORT).show()
                moveToNextMode()
                isCapturing = false
            }
        })
        runOnUiThread { binding.countdownContainer.visibility = View.GONE }
    }

    private fun moveToNextMode() {
        currentModeIndex++
        if (currentModeIndex >= CaptureModes.getAllModes().size) {
            finishCapture()
        } else {
            updateUI()
            ProcessCameraProvider.getInstance(this).get().let { bindCameraUseCases(it) }
        }
    }

    private fun updateUI() {
        val currentMode = getCurrentMode() ?: return
        binding.faceOverlay.setProgress(capturedPhotos.size)
        binding.tvModeTitle.text = currentMode.title
        binding.tvModeInstruction.text = currentMode.instruction

        // Reset UI elements for the new mode
        isPositionCorrect = false
        binding.btnManualCapture.visibility = View.GONE
        autoCaptureTimer?.cancel()
        autoCaptureTimer = null
        toneGenerator.stopTone() // Stop any previous tone
        startManualCaptureTimer() // Start the timer for the new mode
    }

    private fun finishCapture() {
        isFinished = true
        if (capturedPhotos.isEmpty()) {
            finish()
            return
        }
        uploadPhotosToFirebase()
    }

    private fun uploadPhotosToFirebase() {
        lifecycleScope.launch {
            val progressDialog = ProgressDialog.show(this@CameraCaptureActivity, "", "Fotoğraflar yükleniyor...", true)
            try {
                val photosMap = capturedPhotos.mapKeys { it.key.id }
                firebaseUploadManager.uploadAllPhotos(photosMap) { current, total ->
                    progressDialog.setMessage("Yükleniyor... ($current/$total)")
                }
                progressDialog.dismiss()
                Toast.makeText(this@CameraCaptureActivity, "Tüm fotoğraflar başarıyla yüklendi!", Toast.LENGTH_LONG).show()
                setResult(RESULT_OK, Intent().putExtra("upload_success", true))
                finish()
            } catch (e: Exception) {
                progressDialog.dismiss()
                showUploadErrorDialog(e.message)
            }
        }
    }
	
    private fun showUploadErrorDialog(errorMessage: String?) {
        AlertDialog.Builder(this)
            .setTitle("Yükleme Hatası")
            .setMessage("Fotoğraflar yüklenemedi: $errorMessage")
            .setPositiveButton("Tekrar Dene") { _, _ -> uploadPhotosToFirebase() }
            .setNegativeButton("İptal") { _, _ -> finish() }
            .show()
    }

    private fun vibrate(vararg timings: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(timings, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(timings.firstOrNull() ?: 0)
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS && allPermissionsGranted()) {
            startCamera()
        } else {
            Toast.makeText(this, "Kamera izni gereklidir.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        autoCaptureTimer?.cancel()
        manualCaptureButtonTimer?.cancel()
        toneGenerator.release()
        sensorManager.unregisterListener(this)
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {}
}
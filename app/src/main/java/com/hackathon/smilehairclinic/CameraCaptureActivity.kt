package com.hackathon.smilehairclinic

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.hackathon.smilehairclinic.R
import com.hackathon.smilehairclinic.databinding.ActivityCameraCaptureBinding
import com.hackathon.smilehairclinic.model.CaptureMode
import com.hackathon.smilehairclinic.model.CaptureModes
import com.hackathon.smilehairclinic.utils.FirebaseUploadManager
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
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalyzer: ImageAnalysis? = null

    // Sensör değerleri
    private var currentPitch: Float = 0f
    private var currentRoll: Float = 0f
    private var currentAzimuth: Float = 0f

    // Yüz algılama
    private val faceDetector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .enableTracking()
            .build()
        FaceDetection.getClient(options)
    }

    // Mevcut mod ve fotoğraflar
    private var currentModeIndex = 0
    private val capturedPhotos = mutableMapOf<CaptureMode, File>()
    private var isCapturing = false
    private var countDownTimer: CountDownTimer? = null

    // Pozisyon kontrol değişkenleri
    private var isPositionCorrect = false
    private var lastBeepTime = 0L
    private val beepInterval = 500L
    private var detectedFace: Face? = null

    companion object {
        private const val TAG = "CameraCapture"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraCaptureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeComponents()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        setupClickListeners()
        updateUI()
    }

    private fun initializeComponents() {
        cameraExecutor = Executors.newSingleThreadExecutor()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        firebaseUploadManager = FirebaseUploadManager()

        // Sensörleri kaydet
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }

        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    private fun setupClickListeners() {
        binding.btnSkip.setOnClickListener {
            skipCurrentMode()
        }

        binding.btnManualCapture.setOnClickListener {
            if (!isCapturing) {
                capturePhoto()
            }
        }
    }

    private fun getCurrentMode(): CaptureMode {
        return CaptureModes.getAllModes()[currentModeIndex]
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: return

        val currentMode = getCurrentMode()

        // Preview
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
        }

        // Image Capture
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        // Image Analysis for face detection
        if (currentMode.requiresFaceDetection) {
            imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImageForFaceDetection(imageProxy)
                    }
                }
        }

        // Camera selector
        val cameraSelector = if (currentMode.useFrontCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        try {
            cameraProvider.unbindAll()

            camera = if (currentMode.requiresFaceDetection && imageAnalyzer != null) {
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalyzer
                )
            } else {
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "Use case binding failed", e)
        }
    }

    private fun processImageForFaceDetection(imageProxy: ImageProxy) {
        val currentMode = getCurrentMode()
        if (!currentMode.requiresFaceDetection) {
            imageProxy.close()
            return
        }

        @androidx.camera.core.ExperimentalGetImage
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            faceDetector.process(image)
                .addOnSuccessListener { faces ->
                    if (faces.isNotEmpty()) {
                        detectedFace = faces[0]
                        checkPositionAndAutoCapture()
                    } else {
                        detectedFace = null
                        updateFeedback("Yüzünüzü kameraya gösterin")
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Face detection failed", e)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]

                // Telefon açılarını hesapla
                currentPitch = Math.toDegrees(Math.atan2(y.toDouble(), z.toDouble())).toFloat()
                currentRoll = Math.toDegrees(Math.atan2(x.toDouble(), z.toDouble())).toFloat()

                updateAngleDisplay()
                checkPositionAndAutoCapture()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Gerekli değil
    }

    private fun updateAngleDisplay() {
        binding.tvPitchAngle.text = "${currentPitch.toInt()}°"
        binding.tvRollAngle.text = "${currentRoll.toInt()}°"

        val currentMode = getCurrentMode()

        // Pitch kontrolü
        val pitchCorrect = abs(currentPitch - currentMode.targetPitch) <= currentMode.toleranceDegrees
        binding.ivPitchStatus.setColorFilter(
            if (pitchCorrect) getColor(R.color.green) else getColor(R.color.red)
        )

        // Roll kontrolü
        val rollCorrect = abs(currentRoll - currentMode.targetRoll) <= currentMode.toleranceDegrees
        binding.ivRollStatus.setColorFilter(
            if (rollCorrect) getColor(R.color.green) else getColor(R.color.red)
        )
    }

    private fun checkPositionAndAutoCapture() {
        val currentMode = getCurrentMode()

        // Telefon açısı kontrolü
        val pitchCorrect = abs(currentPitch - currentMode.targetPitch) <= currentMode.toleranceDegrees
        val rollCorrect = abs(currentRoll - currentMode.targetRoll) <= currentMode.toleranceDegrees

        // Yüz açısı kontrolü (eğer gerekliyse)
        var faceCorrect = true
        if (currentMode.requiresFaceDetection) {
            faceCorrect = detectedFace != null && checkFaceAngle(currentMode.faceAngle)
        }

        isPositionCorrect = pitchCorrect && faceCorrect // &rollCorrect

        // Geri bildirim güncelle
        updateFeedback(when {
            !pitchCorrect -> "Telefonu ${if (currentPitch < currentMode.targetPitch) "yukarı kaldırın" else "aşağı indirin"}"
            !rollCorrect -> "Telefonu ${if (currentRoll < currentMode.targetRoll) "sağa" else "sola"} çevirin"
            !faceCorrect && currentMode.requiresFaceDetection ->
                if (detectedFace == null) "Yüzünüzü gösterin" else "Yüzünüzü ${currentMode.title} pozisyonuna getirin"
            else -> "Mükemmel! Sabit tutun..."
        })



        // Ses geri bildirimi
        if (isPositionCorrect) {
            playHighBeep()
            // Otomatik çekim başlat
            if (!isCapturing && countDownTimer == null) {
                startAutoCapture()
            }
        }
//            else {
//            playLowBeep()
//            // Yanlış pozisyonda timer varsa iptal et
//            countDownTimer?.cancel()
//            countDownTimer = null
//            binding.countdownContainer.visibility = View.GONE
//            }
    }

    private fun checkFaceAngle(targetAngle: Float?): Boolean {
        if (targetAngle == null || detectedFace == null) return true

        val face = detectedFace!!
        val faceAngle = face.headEulerAngleY // Yaw angle for left/right rotation

        return abs(faceAngle - targetAngle) <= 15f // 15 derece tolerans
    }

    private fun updateFeedback(message: String) {
        runOnUiThread {
            binding.tvPositionFeedback.text = message
            binding.tvPositionFeedback.setBackgroundColor(
                if (isPositionCorrect) getColor(R.color.green_transparent)
                else getColor(R.color.yellow_transparent)
            )
        }
    }

    private fun playHighBeep() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastBeepTime > beepInterval) {
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 100)
            lastBeepTime = currentTime
        }
    }

    private fun playLowBeep() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastBeepTime > beepInterval * 2) {
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 50)
            lastBeepTime = currentTime
        }
    }

    private fun startAutoCapture() {
        isCapturing = true

        runOnUiThread {
            binding.countdownContainer.visibility = View.VISIBLE

            // Titreşim efekti
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(100)
            }
        }

        countDownTimer = object : CountDownTimer(3000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsRemaining = (millisUntilFinished / 1000).toInt() + 1
                runOnUiThread {
                    binding.countdownContainer.text = secondsRemaining.toString()

                    // Her saniye ses çal
                    toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 100)
                }
            }

            override fun onFinish() {
                capturePhoto()
                countDownTimer = null
            }
        }.start()
    }

    private fun capturePhoto() {
        val imageCapture = imageCapture ?: return

        val photoFile = File(
            externalMediaDirs.firstOrNull(),
            "${getCurrentMode().id}_${System.currentTimeMillis()}.jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                    Toast.makeText(this@CameraCaptureActivity,
                        "Fotoğraf çekilemedi: ${exc.message}",
                        Toast.LENGTH_SHORT).show()
                    isCapturing = false
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    capturedPhotos[getCurrentMode()] = photoFile

                    runOnUiThread {
                        // Başarılı çekim sesi
                        toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 200)

                        // Titreşim
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            vibrator.vibrate(VibrationEffect.createWaveform(
                                longArrayOf(0, 100, 50, 100), -1
                            ))
                        }

                        Toast.makeText(this@CameraCaptureActivity,
                            "${getCurrentMode().title} fotoğrafı çekildi!",
                            Toast.LENGTH_SHORT).show()

                        // Sonraki moda geç
                        moveToNextMode()
                    }

                    isCapturing = false
                }
            }
        )

        // Countdown'u temizle
        runOnUiThread {
            binding.countdownContainer.visibility = View.GONE
        }
    }

    private fun skipCurrentMode() {
        moveToNextMode()
    }

    private fun moveToNextMode() {
        currentModeIndex++

        if (currentModeIndex >= CaptureModes.getAllModes().size) {
            // Tüm modlar tamamlandı
            finishCapture()
        } else {
            // Yeni mod için kamerayı yeniden başlat
            updateUI()
            bindCameraUseCases()
        }
    }

    private fun updateUI() {
        val currentMode = getCurrentMode()

        // Progress indicators
        val progressViews = listOf(
            binding.progress1, binding.progress2, binding.progress3,
            binding.progress4, binding.progress5
        )

        progressViews.forEachIndexed { index, view ->
            val isCompleted = capturedPhotos.any { it.key.id == index + 1 }
            val isCurrent = index == currentModeIndex

            view.setBackgroundResource(when {
                isCompleted -> R.drawable.circle_indicator_completed
                isCurrent -> R.drawable.circle_indicator_current
                else -> R.drawable.circle_indicator
            })
        }

        // Mode bilgileri
        binding.tvModeTitle.text = currentMode.title
        binding.tvModeInstruction.text = currentMode.instruction

        // Guide silhouette
        binding.guideSilhouette.setImageResource(
            when (currentMode.id) {
                1, 2, 3 -> R.drawable.guide_face
                4 -> R.drawable.guide_top
                5 -> R.drawable.guide_back
                else -> 0
            }
        )
    }

    private fun finishCapture() {
        if (capturedPhotos.isEmpty()) {
            Toast.makeText(this, "Hiç fotoğraf çekilmedi!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Firebase'e yükle
        uploadPhotosToFirebase()
    }

    private fun uploadPhotosToFirebase() {
        lifecycleScope.launch {
            try {
                showProgressDialog("Fotoğraflar yükleniyor...")

                val userId = firebaseUploadManager.getCurrentUserId()
                val sessionId = System.currentTimeMillis().toString()

                capturedPhotos.forEach { (mode, file) ->
                    firebaseUploadManager.uploadPhoto(
                        userId = userId,
                        sessionId = sessionId,
                        modeId = mode.id,
                        modeName = mode.title,
                        photoFile = file
                    )
                }

                hideProgressDialog()

                Toast.makeText(this@CameraCaptureActivity,
                    "Tüm fotoğraflar başarıyla yüklendi!",
                    Toast.LENGTH_LONG).show()

                // Ana sayfaya dön veya sonuç sayfasına git
                finish()

            } catch (e: Exception) {
                hideProgressDialog()
                Log.e(TAG, "Upload failed", e)
                Toast.makeText(this@CameraCaptureActivity,
                    "Yükleme başarısız: ${e.message}",
                    Toast.LENGTH_LONG).show()
            }
        }
    }

    private var progressDialog: android.app.ProgressDialog? = null

    private fun showProgressDialog(message: String) {
        progressDialog = android.app.ProgressDialog.show(this, "", message, true)
    }

    private fun hideProgressDialog() {
        progressDialog?.dismiss()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this,
                    "Kamera izni gereklidir.",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        countDownTimer?.cancel()
        toneGenerator.release()
        sensorManager.unregisterListener(this)
    }
}
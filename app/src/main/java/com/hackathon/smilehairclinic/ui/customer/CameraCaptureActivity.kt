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
import androidx.annotation.OptIn
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
import com.hackathon.smilehairclinic.R
import com.hackathon.smilehairclinic.databinding.ActivityCameraCaptureBinding
import com.hackathon.smilehairclinic.model.CaptureMode
import com.hackathon.smilehairclinic.model.CaptureModes
import com.hackathon.smilehairclinic.utils.FirebaseUploadManager
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
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
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
            .setTitle(getString(R.string.no_internet_connection))
            .setMessage(getString(R.string.internet_connection_required_for_upload))
            .setPositiveButton(getString(R.string.ok)) { _, _ -> finish() }
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

    @OptIn(ExperimentalGetImage::class)
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
            Log.e(TAG, getString(R.string.use_case_binding_failed), e)
        }
    }

    @OptIn(ExperimentalGetImage::class)
    private fun processImageForFaceDetection(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: run { imageProxy.close(); return }
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        faceDetector.process(image)
            .addOnSuccessListener { faces ->
                detectedFace = faces.firstOrNull()
                checkPositionAndAutoCapture()
            }
            .addOnFailureListener { e -> Log.e(TAG, getString(R.string.face_detection_failed), e) }
            .addOnCompleteListener { imageProxy.close() }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            var pitch = Math.toDegrees(Math.atan2(event.values[2].toDouble(), event.values[1].toDouble())).toFloat()
            val currentMode = getCurrentMode()
            if (currentMode != null && currentMode.useFrontCamera && (currentMode.id == 4 || currentMode.id == 5)) {
                pitch *= -1
            }
            currentPitch = pitch
            checkPositionAndAutoCapture()
        }
    }

    private fun checkPositionAndAutoCapture() {
        if (isCapturing) return // Do not check or play sounds during countdown/capture
        val currentMode = getCurrentMode() ?: return

        val pitchDeviation = abs(currentPitch - currentMode.targetPitch)
        val pitchTolerance = currentMode.toleranceDegrees
        val isPitchCorrect = pitchDeviation <= pitchTolerance

        var isFaceConditionMet = false
        var feedbackMessage = getString(R.string.show_your_face_to_camera) // Default message if face is needed but not found

        if (!currentMode.requiresFaceDetection) {
            isFaceConditionMet = true
        } else {
            val face = detectedFace
            if (face != null) {
                if (currentMode.id == 2 || currentMode.id == 3) {
                    val yaw = face.headEulerAngleY
                    val targetAngle = if (currentMode.id == 2) -45f else 45f // Mode 2: Right (-45), Mode 3: Left (+45)
                    val angleTolerance = 15f

                    if (abs(yaw - targetAngle) <= angleTolerance) {
                        isFaceConditionMet = true
                    } else {
                        isFaceConditionMet = false
                        if (currentMode.id == 2) { // Turning Right
                            feedbackMessage = if (yaw > targetAngle) getString(R.string.turn_your_face_a_little_more_to_the_right) else getString(R.string.turn_your_face_a_little_more_to_the_left)
                        } else { // Turning Left
                            feedbackMessage = if (yaw < targetAngle) getString(R.string.turn_your_face_a_little_more_to_the_left) else getString(R.string.turn_your_face_a_little_more_to_the_right)
                        }
                    }
                } else {
                    isFaceConditionMet = true
                }
            }
        }

        val canPlayPitchBeeps = !currentMode.requiresFaceDetection || detectedFace != null
        playProximityBeep(pitchDeviation, pitchTolerance, canPlayPitchBeeps)

        val isNowCorrect = isPitchCorrect && isFaceConditionMet

        if (isNowCorrect && !isPositionCorrect) {
            manualCaptureButtonTimer?.cancel()
            binding.btnManualCapture.visibility = View.GONE
            startAutoCapture()
        } else if (!isNowCorrect && isPositionCorrect) {
            autoCaptureTimer?.cancel()
            autoCaptureTimer = null
            startManualCaptureTimer()
        }

        isPositionCorrect = isNowCorrect

        updateFeedback(when {
            !isPitchCorrect -> getString(R.string.move_phone_direction, if (currentPitch < currentMode.targetPitch) getString(R.string.move_up) else getString(R.string.move_down))
            !isFaceConditionMet -> feedbackMessage
            else -> getString(R.string.perfect_hold_still)
        })
    }

    private fun playProximityBeep(deviation: Float, tolerance: Float, canPlayBeeps: Boolean) {
        val currentTime = System.currentTimeMillis()
        val tone: Int
        val interval: Long

        if (!canPlayBeeps) {
            toneGenerator.stopTone()
            return
        }

        when {
            deviation <= tolerance * 1.5 -> {
                tone = ToneGenerator.TONE_PROP_BEEP2
                interval = 250L
            }
            deviation <= tolerance * 3 -> {
                tone = ToneGenerator.TONE_PROP_PROMPT
                interval = 500L
            }
            else -> {
                tone = ToneGenerator.TONE_PROP_BEEP
                interval = 1000L
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

        toneGenerator.stopTone()
        autoCaptureTimer?.cancel()
        autoCaptureTimer = null
        manualCaptureButtonTimer?.cancel()
        manualCaptureButtonTimer = null

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) {
                Log.e(TAG, getString(R.string.photo_capture_failed, exc.message), exc)
                isCapturing = false
            }

            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                capturedPhotos[currentMode] = photoFile
                vibrate(100, 50, 100)
                Toast.makeText(this@CameraCaptureActivity, getString(R.string.photo_captured, currentMode.title), Toast.LENGTH_SHORT).show()
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

        isPositionCorrect = false
        binding.btnManualCapture.visibility = View.GONE
        autoCaptureTimer?.cancel()
        autoCaptureTimer = null
        toneGenerator.stopTone()
        startManualCaptureTimer()
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
            val progressDialog = ProgressDialog.show(this@CameraCaptureActivity, "", getString(R.string.uploading_photos), true)
            try {
                val photosMap = capturedPhotos.mapKeys { it.key.id }
                firebaseUploadManager.uploadAllPhotos(photosMap) { current, total ->
                    progressDialog.setMessage(getString(R.string.uploading_progress, current, total))
                }
                progressDialog.dismiss()
                Toast.makeText(this@CameraCaptureActivity, getString(R.string.all_photos_uploaded_successfully), Toast.LENGTH_LONG).show()
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
            .setTitle(getString(R.string.upload_error))
            .setMessage(getString(R.string.photos_could_not_be_uploaded, errorMessage))
            .setPositiveButton(getString(R.string.try_again)) { _, _ -> uploadPhotosToFirebase() }
            .setNegativeButton(getString(R.string.cancel)) { _, _ -> finish() }
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
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, getString(R.string.camera_permission_required), Toast.LENGTH_SHORT).show()
                finish()
            }
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

package com.hackathon.smilehairclinic.model

data class CaptureMode(
    val id: Int,
    val title: String,
    val instruction: String,
    val targetPitch: Float,
    val targetRoll: Float,
    val toleranceDegrees: Float = 10f,
    val requiresFaceDetection: Boolean,
    val faceAngle: Float? = null,
    val useFrontCamera: Boolean
)

object CaptureModes {
    val FRONT_FACE = CaptureMode(
        id = 1,
        title = "Tam Yüz",
        instruction = "Telefonu yüzünüze paralel tutun ve düz bakın",
        targetPitch = 0f,
        targetRoll = 0f,
        requiresFaceDetection = true,
        faceAngle = 0f,
        useFrontCamera = true
    )

    val RIGHT_45 = CaptureMode(
        id = 2,
        title = "45° Sağ",
        instruction = "Yüzünüzü 45 derece sağa çevirin",
        targetPitch = 0f,
        targetRoll = 0f,
        requiresFaceDetection = true,
        faceAngle = 45f,
        useFrontCamera = true
    )

    val LEFT_45 = CaptureMode(
        id = 3,
        title = "45° Sol",
        instruction = "Yüzünüzü 45 derece sola çevirin",
        targetPitch = 0f,
        targetRoll = 0f,
        requiresFaceDetection = true,
        faceAngle = -45f,
        useFrontCamera = true
    )

    val TOP_VERTEX = CaptureMode(
        id = 4,
        title = "Tepe Bölgesi",
        instruction = "Telefonu başınızın üstüne doğru tutun",
        targetPitch = 90f,
        targetRoll = 0f,
        toleranceDegrees = 15f,
        requiresFaceDetection = false,
        faceAngle = null,
        useFrontCamera = false
    )

    val BACK_DONOR = CaptureMode(
        id = 5,
        title = "Arka Donör Bölgesi",
        instruction = "Telefonu başınızın arkasına doğru tutun",
        targetPitch = 45f,
        targetRoll = 0f,
        toleranceDegrees = 15f,
        requiresFaceDetection = false,
        faceAngle = null,
        useFrontCamera = false
    )

    fun getAllModes(): List<CaptureMode> = listOf(
        FRONT_FACE, RIGHT_45, LEFT_45, TOP_VERTEX, BACK_DONOR
    )

    fun getModeById(id: Int): CaptureMode? = getAllModes().find { it.id == id }
}
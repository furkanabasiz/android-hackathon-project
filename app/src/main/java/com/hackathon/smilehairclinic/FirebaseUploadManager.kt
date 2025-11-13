package com.hackathon.smilehairclinic

import android.net.Uri
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.tasks.await
import java.io.File

class FirebaseUploadManager {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    companion object {
        private const val TAG = "FirebaseUpload"
    }

    // Mevcut kullanıcıyı al
    fun getCurrentUser() = auth.currentUser

    fun isUserAuthenticated(): Boolean {
        return auth.currentUser != null
    }

    fun getCurrentUserId(): String {
        val user = auth.currentUser
        if (user == null) {
            Log.e(TAG, "No authenticated user found!")
            throw Exception("Kullanıcı girişi yapılmamış!")
        }
        return user.uid
    }

    fun getCurrentUserEmail(): String? {
        return auth.currentUser?.email
    }

    suspend fun uploadPhoto(
        modeId: Int,
        modeName: String,
        photoFile: File,
        sessionId: String = System.currentTimeMillis().toString()
    ): String {

        // Kullanıcı kontrolü
        if (!isUserAuthenticated()) {
            throw Exception("Lütfen önce giriş yapın!")
        }

        val userId = getCurrentUserId()
        val userEmail = getCurrentUserEmail() ?: "unknown"

        try {
            Log.d(TAG, "Starting upload for user: $userId, mode: $modeName")

            // File exists kontrolü
            if (!photoFile.exists()) {
                throw Exception("Fotoğraf dosyası bulunamadı: ${photoFile.absolutePath}")
            }

            // Storage path oluştur
            val fileName = "${modeId}_${modeName}_${System.currentTimeMillis()}.jpg"
            val storageRef = storage.reference
                .child("photos")
                .child(userId)
                .child(sessionId)
                .child(fileName)

            // Metadata ekle
            val metadata = StorageMetadata.Builder()
                .setContentType("image/jpeg")
                .setCustomMetadata("userId", userId)
                .setCustomMetadata("userEmail", userEmail)
                .setCustomMetadata("modeId", modeId.toString())
                .setCustomMetadata("modeName", modeName)
                .setCustomMetadata("sessionId", sessionId)
                .build()

            // Upload file
            Log.d(TAG, "Uploading file: ${photoFile.name}")
            val uploadTask = storageRef.putFile(Uri.fromFile(photoFile), metadata)

            // Upload progress listener (opsiyonel)
            uploadTask.addOnProgressListener { snapshot ->
                val progress = (100.0 * snapshot.bytesTransferred / snapshot.totalByteCount)
                Log.d(TAG, "Upload progress: ${progress.toInt()}%")
            }

            // Upload tamamlanmasını bekle
            val taskSnapshot = uploadTask.await()

            // Download URL'i al
            val downloadUrl = storageRef.downloadUrl.await().toString()
            Log.d(TAG, "Upload successful! URL: $downloadUrl")

            // Firestore'a kaydet
            savePhotoDataToFirestore(
                userId = userId,
                userEmail = userEmail,
                sessionId = sessionId,
                modeId = modeId,
                modeName = modeName,
                photoUrl = downloadUrl,
                fileName = fileName,
                fileSize = photoFile.length()
            )

            return downloadUrl

        } catch (e: Exception) {
            Log.e(TAG, "Upload failed: ${e.message}", e)
            throw Exception("Fotoğraf yüklenemedi: ${e.message}")
        }
    }

    private suspend fun savePhotoDataToFirestore(
        userId: String,
        userEmail: String,
        sessionId: String,
        modeId: Int,
        modeName: String,
        photoUrl: String,
        fileName: String,
        fileSize: Long
    ) {
        try {
            // Session document
            val sessionDoc = firestore
                .collection("capture_sessions")
                .document(sessionId)

            // Session yoksa oluştur
            sessionDoc.set(
                hashMapOf(
                    "userId" to userId,
                    "userEmail" to userEmail,
                    "sessionId" to sessionId,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "status" to "active"
                )
            ).await()

            // Photo document
            val photoData = hashMapOf(
                "userId" to userId,
                "userEmail" to userEmail,
                "sessionId" to sessionId,
                "modeId" to modeId,
                "modeName" to modeName,
                "photoUrl" to photoUrl,
                "fileName" to fileName,
                "fileSize" to fileSize,
                "uploadedAt" to FieldValue.serverTimestamp()
            )

            // Photos sub-collection'a ekle
            sessionDoc
                .collection("photos")
                .document("mode_$modeId")
                .set(photoData)
                .await()

            Log.d(TAG, "Photo data saved to Firestore")

        } catch (e: Exception) {
            Log.e(TAG, "Firestore save failed: ${e.message}", e)
            // Storage'a yüklendi ama Firestore'a kaydedilemedi
            // Bu durumda kullanıcıyı bilgilendir ama hata fırlatma
        }
    }

    // Batch upload için
    suspend fun uploadAllPhotos(
        photos: Map<Int, File>,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): List<String> {
        val sessionId = System.currentTimeMillis().toString()
        val uploadedUrls = mutableListOf<String>()

        photos.forEach { (modeId, file) ->
            try {
                val modeName = when(modeId) {
                    1 -> "front_face"
                    2 -> "right_45"
                    3 -> "left_45"
                    4 -> "top_vertex"
                    5 -> "back_donor"
                    else -> "unknown"
                }

                val url = uploadPhoto(modeId, modeName, file, sessionId)
                uploadedUrls.add(url)

                onProgress(uploadedUrls.size, photos.size)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to upload mode $modeId", e)
            }
        }

        // Session'ı tamamlandı olarak işaretle
        updateSessionStatus(sessionId, "completed", uploadedUrls.size)

        return uploadedUrls
    }

    private suspend fun updateSessionStatus(sessionId: String, status: String, photoCount: Int) {
        try {
            firestore.collection("capture_sessions")
                .document(sessionId)
                .update(
                    mapOf(
                        "status" to status,
                        "photoCount" to photoCount,
                        "completedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update session status", e)
        }
    }
}
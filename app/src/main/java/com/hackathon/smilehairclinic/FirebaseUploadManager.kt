package com.hackathon.smilehairclinic.utils

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.io.File

class FirebaseUploadManager {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    companion object {
        private const val TAG = "FirebaseUpload"
        private const val COLLECTION_SESSIONS = "capture_sessions"
        private const val COLLECTION_PHOTOS = "photos"
    }

    fun getCurrentUserId(): String {
        return auth.currentUser?.uid ?: "anonymous_${System.currentTimeMillis()}"
    }

    suspend fun uploadPhoto(
        userId: String,
        sessionId: String,
        modeId: Int,
        modeName: String,
        photoFile: File
    ): String {
        try {
            // Storage'a yükle
            val storageRef = storage.reference
                .child("photos")
                .child(userId)
                .child(sessionId)
                .child("${modeId}_${modeName}.jpg")

            val uploadTask = storageRef.putFile(android.net.Uri.fromFile(photoFile))
            val taskSnapshot = uploadTask.await()

            val downloadUrl = storageRef.downloadUrl.await().toString()

            // Firestore'a kaydet
            val photoData = hashMapOf(
                "userId" to userId,
                "sessionId" to sessionId,
                "modeId" to modeId,
                "modeName" to modeName,
                "photoUrl" to downloadUrl,
                "timestamp" to com.google.firebase.Timestamp.now(),
                "fileName" to photoFile.name,
                "fileSize" to photoFile.length()
            )

            firestore.collection(COLLECTION_SESSIONS)
                .document(userId)
                .collection(COLLECTION_PHOTOS)
                .document("${sessionId}_${modeId}")
                .set(photoData)
                .await()

            Log.d(TAG, "Photo uploaded successfully: $modeName")
            return downloadUrl

        } catch (e: Exception) {
            Log.e(TAG, "Upload failed for $modeName", e)
            throw e
        }
    }

    suspend fun createSession(userId: String, sessionId: String) {
        val sessionData = hashMapOf(
            "userId" to userId,
            "sessionId" to sessionId,
            "startTime" to com.google.firebase.Timestamp.now(),
            "status" to "active",
            "photosCount" to 0,
            "completedModes" to ArrayList<Int>()
        )

        firestore.collection(COLLECTION_SESSIONS)
            .document(sessionId)
            .set(sessionData)
            .await()
    }

    suspend fun updateSessionStatus(sessionId: String, status: String, photosCount: Int) {
        val updateData = hashMapOf<String, Any>(
            "status" to status,
            "photosCount" to photosCount,
            "endTime" to com.google.firebase.Timestamp.now()
        )

        firestore.collection(COLLECTION_SESSIONS)
            .document(sessionId)
            .update(updateData)
            .await()
    }

    suspend fun getAllUserSessions(userId: String): List<Map<String, Any>> {
        return try {
            val querySnapshot = firestore.collection(COLLECTION_SESSIONS)
                .whereEqualTo("userId", userId)
                .orderBy("startTime", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .await()

            querySnapshot.documents.map { it.data ?: emptyMap() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get user sessions", e)
            emptyList()
        }
    }

    suspend fun getSessionPhotos(userId: String, sessionId: String): List<Map<String, Any>> {
        return try {
            val querySnapshot = firestore.collection(COLLECTION_SESSIONS)
                .document(userId)
                .collection(COLLECTION_PHOTOS)
                .whereEqualTo("sessionId", sessionId)
                .orderBy("modeId")
                .get()
                .await()

            querySnapshot.documents.map { it.data ?: emptyMap() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get session photos", e)
            emptyList()
        }
    }
}
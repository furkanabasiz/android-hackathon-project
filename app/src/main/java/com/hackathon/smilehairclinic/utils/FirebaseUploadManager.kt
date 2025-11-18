package com.hackathon.smilehairclinic.utils

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.tasks.await
import java.io.File

class FirebaseUploadManager {

    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    suspend fun uploadAllPhotos(
        photos: Map<Int, File>,
        onProgress: (Int, Int) -> Unit
    ): Map<Int, String> {
        val currentUser = auth.currentUser
        require(currentUser != null) { "User not logged in" }

        // Patient name is retrieved from the user's display name.
        // Ensure that the displayName is set correctly during user registration/profile update.
        val patientName = currentUser.displayName
        require(!patientName.isNullOrEmpty()) { "Patient name (displayName) could not be determined." }

        val downloadUrls = mutableMapOf<Int, String>()
        val totalPhotos = photos.size
        var uploadedCount = 0

        photos.forEach { (modeId, file) ->
            // The storage path is corrected to match the download logic expected by the consultant.
            val storageRef = storage.reference
                .child("patient_submissions/$patientName/photo_$modeId.jpg")

            val downloadUrl = uploadFile(storageRef, file)
            downloadUrls[modeId] = downloadUrl
            uploadedCount++
            onProgress(uploadedCount, totalPhotos)
        }

        return downloadUrls
    }

    private suspend fun uploadFile(storageRef: StorageReference, file: File): String {
        val uploadTask = storageRef.putFile(Uri.fromFile(file))
        val snapshot = uploadTask.await()
        return snapshot.storage.downloadUrl.await().toString()
    }
}
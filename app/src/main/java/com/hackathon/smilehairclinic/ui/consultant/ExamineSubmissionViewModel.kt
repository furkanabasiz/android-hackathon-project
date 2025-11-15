package com.hackathon.smilehairclinic.ui.consultant

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date

class ExamineSubmissionViewModel : ViewModel() {

    private val storage = FirebaseStorage.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private val _uiState = MutableLiveData<UiState>()
    val uiState: LiveData<UiState> = _uiState

    fun fetchPatients() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val patientPrefixes = storage.reference.child("patient_submissions").listAll().await().prefixes
                val patientNames = patientPrefixes.map { it.name }
                _uiState.value = UiState.PatientsLoaded(patientNames)
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Hastalar yüklenemedi: ${e.message}")
            }
        }
    }

    fun fetchPatientPhotos(patientName: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val photoUris = storage.reference.child("patient_submissions/$patientName").listAll().await().items.map {
                    it.downloadUrl.await()
                }
                _uiState.value = UiState.PhotosLoaded(patientName, photoUris)
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Fotoğraflar yüklenemedi: ${e.message}")
            }
        }
    }

    fun createAppointment(patientName: String, appointmentDate: Date) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val appointment = hashMapOf(
                    "patientName" to patientName,
                    "date" to appointmentDate,
                    "status" to "confirmed"
                )
                firestore.collection("appointments").add(appointment).await()
                _uiState.value = UiState.AppointmentCreated
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Randevu oluşturulamadı: ${e.message}")
            }
        }
    }
}

sealed class UiState {
    object Loading : UiState()
    data class PatientsLoaded(val patientNames: List<String>) : UiState()
    data class PhotosLoaded(val patientName: String, val photoUris: List<Uri>) : UiState()
    object AppointmentCreated : UiState()
    data class Error(val message: String) : UiState()
}
package com.hackathon.smilehairclinic.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.hackathon.smilehairclinic.model.Appointment
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AppointmentsViewModel : ViewModel() {

    private val firestore = FirebaseFirestore.getInstance()

    private val _appointments = MutableLiveData<List<Appointment>>()
    val appointments: LiveData<List<Appointment>> = _appointments

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    fun fetchAllAppointments() {
        viewModelScope.launch {
            try {
                val snapshot = firestore.collection("appointments")
                    .orderBy("date", Query.Direction.ASCENDING)
                    .get()
                    .await()
                _appointments.value = snapshot.toObjects(Appointment::class.java)
            } catch (e: Exception) {
                _error.value = "Randevular alınamadı: ${e.message}"
            }
        }
    }
}

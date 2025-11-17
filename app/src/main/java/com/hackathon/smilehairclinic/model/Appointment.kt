package com.hackathon.smilehairclinic.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.Timestamp
import java.io.Serializable

/**
 * Represents an appointment document in the Firestore database.
 *
 * @property documentId The unique ID of the document in Firestore.
 * @property patientName The name of the patient for whom the appointment is scheduled.
 * @property date The timestamp of the appointment.
 * @property status The current status of the appointment (e.g., "confirmed").
 */
data class Appointment(
    @DocumentId
    val documentId: String = "",
    val patientName: String = "",
    val date: Timestamp? = null,
    val status: String = ""
) : Serializable

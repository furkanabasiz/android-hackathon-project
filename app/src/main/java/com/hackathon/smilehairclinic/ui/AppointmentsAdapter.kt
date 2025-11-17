package com.hackathon.smilehairclinic.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hackathon.smilehairclinic.R
import com.hackathon.smilehairclinic.model.Appointment
import java.text.SimpleDateFormat
import java.util.Locale

class AppointmentsAdapter : ListAdapter<Appointment, AppointmentsAdapter.AppointmentViewHolder>(AppointmentDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppointmentViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_appointment, parent, false)
        return AppointmentViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppointmentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AppointmentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val patientNameTextView: TextView = itemView.findViewById(R.id.textViewAppointmentPatientName)
        private val dateTextView: TextView = itemView.findViewById(R.id.textViewAppointmentDate)

        fun bind(appointment: Appointment) {
            patientNameTextView.text = appointment.patientName

            val sdf = SimpleDateFormat("dd MMMM yyyy - HH:mm", Locale("tr"))
            dateTextView.text = appointment.date?.toDate()?.let { sdf.format(it) } ?: "Tarih belirtilmemiş"
        }
    }
}

class AppointmentDiffCallback : DiffUtil.ItemCallback<Appointment>() {
    override fun areItemsTheSame(oldItem: Appointment, newItem: Appointment): Boolean {
        return oldItem.documentId == newItem.documentId
    }

    override fun areContentsTheSame(oldItem: Appointment, newItem: Appointment): Boolean {
        return oldItem == newItem
    }
}

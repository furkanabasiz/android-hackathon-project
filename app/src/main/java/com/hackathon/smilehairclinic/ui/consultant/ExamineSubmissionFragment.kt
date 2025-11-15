package com.hackathon.smilehairclinic.ui.consultant

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.hackathon.smilehairclinic.R
import com.hackathon.smilehairclinic.databinding.FragmentExamineSubmissionBinding
import java.io.OutputStream
import java.util.Calendar
import java.util.Date

class ExamineSubmissionFragment : Fragment() {

    private var _binding: FragmentExamineSubmissionBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ExamineSubmissionViewModel by viewModels()
    private lateinit var patientAdapter: PatientAdapter
    private var currentPatientName: String? = null

    private var selectedYear: Int = 0
    private var selectedMonth: Int = 0
    private var selectedDay: Int = 0
    private var selectedHour: Int = 0
    private var selectedMinute: Int = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExamineSubmissionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        observeViewModel()

        viewModel.fetchPatients()

        binding.buttonPickDateTime.setOnClickListener { showDateTimePicker() }
        binding.buttonConfirmAppointment.setOnClickListener {
            val patientName = currentPatientName
            if (patientName != null && selectedYear != 0) {
                val appointmentDate = Calendar.getInstance().apply {
                    set(selectedYear, selectedMonth, selectedDay, selectedHour, selectedMinute)
                }.time
                viewModel.createAppointment(patientName, appointmentDate)
            } else if (patientName == null) {
                Toast.makeText(requireContext(), "Hasta seçimiyle ilgili bir hata oluştu.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), getString(R.string.select_date_time_prompt), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupRecyclerView() {
        patientAdapter = PatientAdapter { patientName ->
            currentPatientName = patientName
            viewModel.fetchPatientPhotos(patientName)
        }
        binding.recyclerViewPatients.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = patientAdapter
        }
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is UiState.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.recyclerViewPatients.visibility = View.GONE
                    binding.layoutPatientDetails.visibility = View.GONE
                }
                is UiState.PatientsLoaded -> {
                    binding.progressBar.visibility = View.GONE
                    binding.recyclerViewPatients.visibility = View.VISIBLE
                    patientAdapter.submitList(state.patientNames)
                }
                is UiState.PhotosLoaded -> {
                    binding.progressBar.visibility = View.GONE
                    binding.layoutPatientDetails.visibility = View.VISIBLE
                    binding.textViewPatientName.text = state.patientName
                    loadPhotos(state.patientName, state.photoUris)
                }
                is UiState.AppointmentCreated -> {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(context, getString(R.string.appointment_created), Toast.LENGTH_LONG).show()
                    findNavController().popBackStack()
                }
                is UiState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun loadPhotos(patientName: String, photoUris: List<Uri>) {
        val imageViews = listOf(
            binding.imageView1, binding.imageView2, binding.imageView3,
            binding.imageView4, binding.imageView5
        )
        photoUris.forEachIndexed { index, uri ->
            if (index < imageViews.size) {
                Glide.with(this)
                    .asBitmap()
                    .load(uri)
                    .into(object : CustomTarget<Bitmap>() {
                        override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                            imageViews[index].setImageBitmap(resource)
                            saveImageToGallery(resource, "$patientName-photo-${index + 1}.jpg")
                        }
                        override fun onLoadCleared(placeholder: Drawable?) {}
                    })
            }
        }
    }

    private fun saveImageToGallery(bitmap: Bitmap, fileName: String) {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/SmileHairClinic")
            }
        }

        val resolver = requireActivity().contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        uri?.let {
            try {
                val stream: OutputStream? = resolver.openOutputStream(it)
                stream?.let {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                    stream.flush()
                    stream.close()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showDateTimePicker() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(requireContext(), { _, year, month, dayOfMonth ->
            selectedYear = year
            selectedMonth = month
            selectedDay = dayOfMonth
            TimePickerDialog(requireContext(), { _, hourOfDay, minute ->
                selectedHour = hourOfDay
                selectedMinute = minute
                updateSelectedDateTime()
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun updateSelectedDateTime() {
        val selectedDateTime = getString(R.string.selected_appointment_format, selectedDay, selectedMonth + 1, selectedYear, selectedHour, selectedMinute)
        binding.textViewSelectedDateTime.text = selectedDateTime
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class PatientAdapter(private val onPatientClick: (String) -> Unit) :
    ListAdapter<String, PatientAdapter.PatientViewHolder>(PatientDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PatientViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_patient, parent, false)
        return PatientViewHolder(view)
    }

    override fun onBindViewHolder(holder: PatientViewHolder, position: Int) {
        holder.bind(getItem(position))
        holder.itemView.setOnClickListener { onPatientClick(getItem(position)) }
    }

    inner class PatientViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textView: TextView = itemView.findViewById(R.id.textViewPatientNameItem)
        fun bind(patientName: String) {
            textView.text = patientName
        }
    }
}

class PatientDiffCallback : DiffUtil.ItemCallback<String>() {
    override fun areItemsTheSame(oldItem: String, newItem: String): Boolean {
        return oldItem == newItem
    }

    override fun areContentsTheSame(oldItem: String, newItem: String): Boolean {
        return oldItem == newItem
    }
}
package com.hackathon.smilehairclinic.ui.consultant

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.hackathon.smilehairclinic.R
import com.hackathon.smilehairclinic.databinding.FragmentConsultantDashboardBinding
import com.hackathon.smilehairclinic.utils.AppointmentsAdapter
import com.hackathon.smilehairclinic.viewmodel.AppointmentsViewModel

class ConsultantDashboardFragment : Fragment() {

    private var _binding: FragmentConsultantDashboardBinding? = null
    private val binding get() = _binding!!

    private val appointmentsViewModel: AppointmentsViewModel by viewModels()
    private lateinit var appointmentsAdapter: AppointmentsAdapter
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConsultantDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUserInfo()
        setupRecyclerView()
        observeViewModel()

        // Fetch all appointments when the view is created
        appointmentsViewModel.fetchAllAppointments()

        binding.examineButton.setOnClickListener {
            findNavController().navigate(R.id.action_consultantDashboardFragment_to_examineSubmissionFragment)
        }
    }

    private fun setupUserInfo() {
        // You might want to display consultant-specific info here
        binding.userNameText.text = "Danışman Paneli"
    }

    private fun setupRecyclerView() {
        appointmentsAdapter = AppointmentsAdapter()
        binding.recyclerViewAppointments.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = appointmentsAdapter
        }
    }

    private fun observeViewModel() {
        appointmentsViewModel.appointments.observe(viewLifecycleOwner) { appointments ->
            // Display all appointments without filtering
            appointmentsAdapter.submitList(appointments)
        }

        appointmentsViewModel.error.observe(viewLifecycleOwner) { errorMsg ->
            Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
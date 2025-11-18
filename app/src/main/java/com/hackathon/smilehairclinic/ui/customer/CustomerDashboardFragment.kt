package com.hackathon.smilehairclinic.ui.customer

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
import com.hackathon.smilehairclinic.databinding.FragmentCustomerDashboardBinding
import com.hackathon.smilehairclinic.utils.AppointmentsAdapter
import com.hackathon.smilehairclinic.viewmodel.AppointmentsViewModel

class CustomerDashboardFragment : Fragment() {

    private var _binding: FragmentCustomerDashboardBinding? = null
    private val binding get() = _binding!!

    private val appointmentsViewModel: AppointmentsViewModel by viewModels()
    private lateinit var appointmentsAdapter: AppointmentsAdapter
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCustomerDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUserInfo()
        setupRecyclerView()
        observeViewModel()

        appointmentsViewModel.fetchAllAppointments()

        binding.cameraButton.setOnClickListener {
            val action = CustomerDashboardFragmentDirections.actionCustomerDashboardFragmentToTutorialFragment()
            findNavController().navigate(action)
        }
        binding.imageView3.setOnClickListener {
            logout()
        }
    }

    private fun setupUserInfo() {
        val currentUser = auth.currentUser
        binding.userNameText.text = currentUser?.displayName ?: "Misafir"
    }

    private fun setupRecyclerView() {
        appointmentsAdapter = AppointmentsAdapter()
        binding.recyclerViewAppointments.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = appointmentsAdapter
        }
    }

    private fun observeViewModel() {
        val currentUserName = auth.currentUser?.displayName

        appointmentsViewModel.appointments.observe(viewLifecycleOwner) { appointments ->
            // Filter appointments to show only the current user's appointments
            val myAppointments = appointments.filter { it.patientName == currentUserName }
            appointmentsAdapter.submitList(myAppointments)
        }

        appointmentsViewModel.error.observe(viewLifecycleOwner) { errorMsg ->
            Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
        }
    }

    fun logout() {
        auth.signOut()
        val action = CustomerDashboardFragmentDirections.actionCustomerDashboardFragmentToRegisterFragment()
        findNavController().navigate(action)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
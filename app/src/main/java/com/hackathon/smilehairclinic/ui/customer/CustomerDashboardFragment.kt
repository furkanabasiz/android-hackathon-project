package com.hackathon.smilehairclinic.ui.customer

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.hackathon.smilehairclinic.R
import com.hackathon.smilehairclinic.databinding.FragmentCustomerDashboardBinding
import com.hackathon.smilehairclinic.databinding.FragmentRegisterBinding

class CustomerDashboardFragment : Fragment() {

    private var _binding: FragmentCustomerDashboardBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = Firebase.auth
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentCustomerDashboardBinding.inflate(inflater, container, false)
        val view = binding.root
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.textView6.setOnClickListener { logout(it) }
        binding.cameraButton.setOnClickListener { findNavController().navigate(
            CustomerDashboardFragmentDirections.actionCustomerDashboardFragmentToCameraCaptureActivity()) }
    }

    fun logout(view: View){
        auth.signOut()
        val action = CustomerDashboardFragmentDirections.actionCustomerDashboardFragmentToLoginFragment()
        findNavController().navigate(action)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
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
import com.google.firebase.firestore.FirebaseFirestore
import com.hackathon.smilehairclinic.R
import com.hackathon.smilehairclinic.databinding.FragmentCustomerDashboardBinding

class CustomerDashboardFragment : Fragment() {

    private var _binding: FragmentCustomerDashboardBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = Firebase.auth
        firestore = FirebaseFirestore.getInstance()
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

        val currentUser = auth.currentUser
        if (currentUser != null) {
            val docRef = firestore.collection("users").document(currentUser.uid)
            docRef.get().addOnSuccessListener { document ->
                if (document != null) {
                    val name = document.getString("name")
                    binding.userNameText.text = "Merhaba, $name"
                }
            }
        }
        binding.userNameText.setOnClickListener { logout(it) }
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
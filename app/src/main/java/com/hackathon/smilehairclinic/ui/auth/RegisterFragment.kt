package com.hackathon.smilehairclinic.ui.auth

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.hackathon.smilehairclinic.databinding.FragmentRegisterBinding

class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = Firebase.auth
        firestore = FirebaseFirestore.getInstance()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.registerButton.setOnClickListener { register() }
        binding.registerFragmentButton.setOnClickListener { goLogin() }
        binding.consultantLoginButton.setOnClickListener { goConsLogin() }

        val currentUser = auth.currentUser
        if (currentUser != null) {
            updateUI(currentUser)
        }
    }

    private fun register() {
        val name = binding.editTextName.text.toString()
        val phone = binding.editTextPhone.text.toString()
        val email = binding.editTextEmail.text.toString()
        val password = binding.editTextPassword.text.toString()
        val checkbox = binding.kvkkCheckBox.isChecked

        if (name.isEmpty() || phone.isEmpty() || email.isEmpty() || password.isEmpty()) {
            Toast.makeText(requireContext(), "Tüm alanlar doldurulmalıdır.", Toast.LENGTH_LONG).show()
            return
        }

        if (!checkbox) {
            Toast.makeText(requireContext(), "Lütfen KVKK ve Hizmet Sözleşmesini onaylayın!", Toast.LENGTH_LONG).show()
            return
        }

        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { authResult ->
                val user = authResult.user!!
                val profileUpdates = userProfileChangeRequest {
                    displayName = name
                }

                // Chain the tasks: 1. Update Profile -> 2. Write to Firestore -> 3. Navigate
                user.updateProfile(profileUpdates).addOnCompleteListener { profileTask ->
                    if (!profileTask.isSuccessful) {
                        // This is not a critical error, but we should log it.
                        // The photo upload might fail later, but the user is registered.
                        Log.w("RegisterFragment", "User profile displayName could not be set.", profileTask.exception)
                    }

                    // Proceed to save user data to Firestore
                    val userMap = hashMapOf(
                        "name" to name,
                        "phone" to phone,
                        "email" to email
                    )
                    firestore.collection("users").document(user.uid).set(userMap)
                        .addOnSuccessListener {
                            // Now that everything is saved, navigate to the next screen
                            updateUI(user)
                        }
                        .addOnFailureListener { e ->
                            // This is a more critical error
                            Toast.makeText(requireContext(), "Kullanıcı veritabanına kaydedilemedi: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(requireContext(), "Kayıt başarısız: ${exception.localizedMessage}", Toast.LENGTH_LONG).show()
            }
    }

    private fun updateUI(user: FirebaseUser?) {
        if (user != null) {
            val action = RegisterFragmentDirections.actionRegisterFragmentToCustomerDashboardFragment()
            findNavController().navigate(action)
        }
    }

    fun goLogin(){
        val action = RegisterFragmentDirections.actionRegisterFragmentToLoginFragment()
        findNavController().navigate(action)
    }

    fun goConsLogin(){
        val action = RegisterFragmentDirections.actionRegisterFragmentToLoginConsultantFragment()
        findNavController().navigate(action)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
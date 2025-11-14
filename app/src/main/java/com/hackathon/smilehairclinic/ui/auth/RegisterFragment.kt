package com.hackathon.smilehairclinic.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
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
    ): View? {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        val view = binding.root
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.registerButton.setOnClickListener { register(it) }

        val currentUser = auth.currentUser
        if (currentUser != null) {
            updateUI(currentUser)
        }
    }

    fun register(view: View) {

        val name = binding.editTextName.text.toString()
        val phone = binding.editTextPhone.text.toString()
        val email = binding.editTextEmail.text.toString()
        val password = binding.editTextPassword.text.toString()
        val checkbox = binding.kvkkCheckBox.isChecked

        if (email.isNotEmpty() && password.isNotEmpty() && checkbox) {
            auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // user created
                    val user = auth.currentUser
                    user?.let {
                        val userMap = hashMapOf(
                            "name" to name,
                            "phone" to phone,
                            "email" to email
                        )
                        firestore.collection("users").document(it.uid).set(userMap)
                    }
                    updateUI(user)
                }
            }.addOnFailureListener { exception ->
                Toast.makeText(requireContext(), exception.localizedMessage, Toast.LENGTH_LONG).show()
            }
        } else if (!checkbox) {
            Toast.makeText(requireContext(),
                "Lütfen KVKK ve Hizmet Sözleşmesini onaylayın!",
                Toast.LENGTH_LONG).show()
        }
        else {
            // If sign in fails, display a message to the user.
            Toast.makeText(
                requireContext(),
                "Register failed.",
                Toast.LENGTH_LONG,
            ).show()
            updateUI(null)
        }
    }

    fun updateUI(user: FirebaseUser?) {
        if (user != null) {
            val action = RegisterFragmentDirections.actionRegisterFragmentToCustomerDashboardFragment()
            findNavController().navigate(action)
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
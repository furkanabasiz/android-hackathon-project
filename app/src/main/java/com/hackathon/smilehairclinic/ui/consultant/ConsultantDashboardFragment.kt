package com.hackathon.smilehairclinic.ui.consultant

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.hackathon.smilehairclinic.R
import com.hackathon.smilehairclinic.databinding.FragmentConsultantDashboardBinding

class ConsultantDashboardFragment : Fragment() {

    private var _binding: FragmentConsultantDashboardBinding? = null
    private val binding get() = _binding!!

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

        binding.examineButton.setOnClickListener {
            findNavController().navigate(R.id.action_consultantDashboardFragment_to_examineSubmissionFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
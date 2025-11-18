package com.hackathon.smilehairclinic.ui.customer

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewpager2.widget.ViewPager2
import com.hackathon.smilehairclinic.R
import com.hackathon.smilehairclinic.ui.customer.adapter.TutorialViewPagerAdapter

class TutorialFragment : Fragment() {

    private lateinit var viewPager: ViewPager2

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_tutorial, container, false)
        viewPager = view.findViewById(R.id.viewPager)
        val adapter = TutorialViewPagerAdapter(requireContext(), viewPager)
        viewPager.adapter = adapter
        return view
    }
}
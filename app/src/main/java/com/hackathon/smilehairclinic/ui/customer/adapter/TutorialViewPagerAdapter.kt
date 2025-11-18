package com.hackathon.smilehairclinic.ui.customer.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.hackathon.smilehairclinic.R

class TutorialViewPagerAdapter(
    private val viewPager: ViewPager2,
    private val onNavigate: () -> Unit
) : RecyclerView.Adapter<TutorialViewPagerAdapter.ViewHolder>() {

    private val titles = arrayOf(
        "Telefonunuzu dik tutarak doğrudan kameraya bakın, sonra başınızı sırasıyla önce hafif sağa " +
                "sonra da hafif sola çevirin ",
        "Karşıya bakarken telefonunuzu yukarı kaldırıp kafanızın üst bölümünü çekin",
        "Karşıya bakarken telefonunuzu kafanızın arkasına kadar kaldırıp ense bölgenizi çekin"
    )
    private val images = intArrayOf(
        R.drawable.tutorial1,
        R.drawable.tutorial2,
        R.drawable.tutorial3,
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_tutorial, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.textView.text = titles[position]
        holder.imageView.setImageResource(images[position])

        if (position == 0) {
            holder.backButton.visibility = View.INVISIBLE
        } else {
            holder.backButton.visibility = View.VISIBLE
        }

        if (position == titles.size - 1) {
            holder.nextButton.text = "BAŞLA"
            holder.nextButton.setOnClickListener { onNavigate() }
        } else {
            holder.nextButton.text = "İLERİ"
            holder.nextButton.setOnClickListener {
                viewPager.currentItem = position + 1
            }
        }

        holder.backButton.setOnClickListener {
            viewPager.currentItem = position - 1
        }
    }

    override fun getItemCount(): Int {
        return titles.size
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.imageView)
        val textView: TextView = itemView.findViewById(R.id.textView)
        val backButton: Button = itemView.findViewById(R.id.backButton)
        val nextButton: Button = itemView.findViewById(R.id.nextButton)
    }
}

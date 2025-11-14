package com.hackathon.smilehairclinic

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.hackathon.smilehairclinic.utils.NetworkUtils

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (!NetworkUtils.isNetworkAvailable(this)) {
            showNoInternetDialog()
        }
    }

    private fun showNoInternetDialog() {
        AlertDialog.Builder(this)
            .setTitle("İnternet Bağlantısı Yok")
            .setMessage("Uygulamayı kullanmak için internet bağlantısı gereklidir.")
            .setPositiveButton("Tamam") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }
}
package com.moondiagnosticcenter.app

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 60, 40, 40)
        }

        val title = TextView(this).apply {
            text = "Moon Diagnostic Center"
            textSize = 28f
        }

        val subtitle = TextView(this).apply {
            text = "অ্যাপ প্রস্তুত হচ্ছে..."
            textSize = 18f
        }

        layout.addView(title)
        layout.addView(subtitle)

        setContentView(layout)
    }
}

package com.example.localtasktracker

import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            orientation = LinearLayout.VERTICAL
        }

        val textView = TextView(this).apply {
            text = "Hello Task Tracker!"
            textSize = 24f
        }

        layout.addView(textView)
        setContentView(layout)
    }
}
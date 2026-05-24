package com.example.localtasktracker

import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val tasks = mutableListOf<String>()
    private lateinit var taskListLayout: LinearLayout
    private lateinit var taskInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.hide()

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 120, 40, 40)
        }

        val titleText = TextView(this).apply {
            text = "Local Task Tracker"
            textSize = 28f
            gravity = Gravity.CENTER
        }

        taskInput = EditText(this).apply {
            hint = "Enter a task"
        }

        val addButton = Button(this).apply {
            text = "Add Task"
        }

        taskListLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val scrollView = ScrollView(this).apply {
            addView(taskListLayout)
        }

        addButton.setOnClickListener {
            addTask()
        }

        mainLayout.addView(titleText)
        mainLayout.addView(taskInput)
        mainLayout.addView(addButton)
        mainLayout.addView(scrollView)

        setContentView(mainLayout)
    }

    private fun addTask() {
        val taskText = taskInput.text.toString().trim()

        if (taskText.isNotEmpty()) {
            tasks.add(taskText)
            taskInput.text.clear()
            refreshTaskList()
        }
    }

    private fun refreshTaskList() {
        taskListLayout.removeAllViews()

        for ((index, task) in tasks.withIndex()) {
            val taskRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 20, 0, 20)
            }

            val taskText = TextView(this).apply {
                text = task
                textSize = 20f
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            val deleteButton = Button(this).apply {
                text = "Delete"
                setOnClickListener {
                    tasks.removeAt(index)
                    refreshTaskList()
                }
            }

            taskRow.addView(taskText)
            taskRow.addView(deleteButton)

            taskListLayout.addView(taskRow)
        }
    }
}
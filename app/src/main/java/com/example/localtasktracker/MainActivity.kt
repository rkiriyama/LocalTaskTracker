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

    private val tasks = mutableListOf<Task>()
    private lateinit var taskListLayout: LinearLayout
    private lateinit var taskInput: EditText
    private lateinit var categoryInput: EditText
    private lateinit var subTaskInput: EditText
    private var selectedTask: Task? = null
    private var selectedCategory: TaskCategory? = null

    // Unique ID counters
    private var nextTaskId = 1
    private var nextCategoryId = 1
    private var nextSubTaskId = 1

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
            addCategory()
            addSubTask()
        }

        mainLayout.addView(titleText)
        mainLayout.addView(taskInput)
        mainLayout.addView(addButton)
        mainLayout.addView(scrollView)

        setContentView(mainLayout)
    }

    private fun addTask(): Boolean {
        val taskText = taskInput.text.toString().trim()

        if (taskText.isNotEmpty()) {
            val newTask = Task(nextTaskId, taskText)
            tasks.add(newTask)
            selectedTask = newTask
            nextTaskId++
            taskInput.text.clear()
            refreshTaskList()
            return true
        }
        return false
    }

    private fun addCategory(): Boolean {
        val task = selectedTask ?: return false
        val categoryText = categoryInput.text.toString().trim()

        if (categoryText.isNotEmpty()) {
            val newCategory = TaskCategory(nextCategoryId, categoryText)
            val success = task.addCategory(newCategory)
            if (!success) {
                categoryInput.text.clear()
                return false
            }
            selectedCategory = newCategory
            nextCategoryId++
            categoryInput.text.clear()
            refreshTaskList()
            return true
        }
        return false
    }

    private fun addSubTask(): Boolean {
        val category = selectedCategory ?: return false
        val subTaskText = subTaskInput.text.toString().trim()

        if (subTaskText.isNotEmpty()) {
            val newSubTask = SubTask(nextSubTaskId, subTaskText)
            val success = category.addSubTask(newSubTask)
            if (!success) {
                subTaskInput.text.clear()
                return false
            }
            nextSubTaskId++
            subTaskInput.text.clear()
            refreshTaskList()
            return true
        }
        return false
    }

    private fun deleteTask(taskID: Int): Boolean {
        val success = tasks.removeAll { it.id == taskID }
        if (success) {
            if (selectedTask?.id == taskID) {
                selectedTask = null
                selectedCategory = null
            }
            refreshTaskList()
        }
        return success
    }

    private fun deleteCategory(categoryID: Int): Boolean {
        val task = selectedTask ?: return false
        val success = task.deleteCategory(categoryID)
        if (success) {
            if (selectedCategory?.id == categoryID) {
                selectedCategory = null
            }
            refreshTaskList()
        }
        return success
    }

    private fun deleteSubTask(subTaskID: Int): Boolean {
        val category = selectedCategory ?: return false
        val success = category.deleteSubTask(subTaskID)
        if (success) {
            refreshTaskList()
        }
        return success
    }

    private fun refreshTaskList() {
        taskListLayout.removeAllViews()

        for ((index, task) in tasks.withIndex()) {
            val taskRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 20, 0, 20)
            }

            val taskText = TextView(this).apply {
                text = task.title
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
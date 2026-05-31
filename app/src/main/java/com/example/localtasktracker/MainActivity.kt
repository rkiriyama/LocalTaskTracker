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
    private lateinit var categoryListLayout: LinearLayout
    private lateinit var subTaskListLayout: LinearLayout
    private lateinit var taskInput: EditText
    private lateinit var categoryInput: EditText
    private lateinit var subTaskInput: EditText
    private var selectedTask: Task? = null
    private var selectedCategory: TaskCategory? = null

    // Unique ID counters
    private var nextTaskId = 1
    private var nextCategoryId = 1
    private var nextSubTaskId = 1

    private lateinit var mainLayout: LinearLayout


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.hide()

        mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 120, 40, 40)
        }

        setContentView(mainLayout)

        viewTaskListPage()
    }

    private fun viewTaskListPage() {
        mainLayout.removeAllViews()

        val titleText = TextView(this).apply {
            text = "Local Task Tracker"
            textSize = 28f
            gravity = Gravity.CENTER
        }

        taskInput = EditText(this).apply {
            hint = "Enter a task"
        }

        val addTaskButton = Button(this).apply {
            text = "Add Task"
            setOnClickListener {
                addTask()
                viewTaskListPage()
            }
        }

        taskListLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val scrollView = ScrollView(this).apply {
            addView(taskListLayout)
        }

        mainLayout.addView(titleText)
        mainLayout.addView(taskInput)
        mainLayout.addView(addTaskButton)
        mainLayout.addView(scrollView)

        refreshTaskList()
    }

    private fun viewCategoryPage(task: Task) {
        mainLayout.removeAllViews()

        selectedTask = task
        selectedCategory = null

        val titleText = TextView(this).apply {
            text = task.title
            textSize = 28f
            gravity = Gravity.CENTER
        }

        categoryInput = EditText(this).apply {
            hint = "Enter a category"
        }

        val addCategoryButton = Button(this).apply {
            text = "Add Category"
            setOnClickListener {
                addCategory()
                viewCategoryPage(task)
            }
        }

        val backButton = Button(this).apply {
            text = "Back to Tasks"
            setOnClickListener {
                viewTaskListPage()
            }
        }

        categoryListLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val scrollView = ScrollView(this).apply {
            addView(categoryListLayout)
        }

        mainLayout.addView(titleText)
        mainLayout.addView(categoryInput)
        mainLayout.addView(addCategoryButton)
        mainLayout.addView(backButton)
        mainLayout.addView(scrollView)

        refreshCategoryList(task)

    }

    private fun viewSubTasksPage() {

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

            val viewTaskButton = Button(this).apply {
                text = "View"
                setOnClickListener {
                    viewCategoryPage(task)
                }
            }

            val deleteButton = Button(this).apply {
                text = "Delete"
                setOnClickListener {
                    val iD = tasks[index].id
                    deleteTask(iD)
                    refreshTaskList()
                }
            }

            taskRow.addView(taskText)
            taskRow.addView(viewTaskButton)
            taskRow.addView(deleteButton)

            taskListLayout.addView(taskRow)
        }
    }

    private fun refreshCategoryList(task: Task) {
        categoryListLayout.removeAllViews()
        val taskCat = task.categories
        for ((index, category) in taskCat.withIndex()) {
            val categoryRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 20, 0, 20)
            }

            val categoryText = TextView(this).apply {
                text = category.categoryName
                textSize = 20f
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            val viewCategoryButton = Button(this).apply {
                text = "View"
                setOnClickListener {
                }
            }

            val deleteButton = Button(this).apply {
                text = "Delete"
                setOnClickListener {
                    val iD = task.categories[index].id
                    deleteCategory(iD)
                    refreshCategoryList(task)
                }
            }

            categoryRow.addView(categoryText)
            categoryRow.addView(viewCategoryButton)
            categoryRow.addView(deleteButton)

            categoryListLayout.addView(categoryRow)
        }
    }
}
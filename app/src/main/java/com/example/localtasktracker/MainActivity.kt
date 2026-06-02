package com.example.localtasktracker

import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.widget.CheckBox
import androidx.appcompat.app.AlertDialog

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
            text = "Local Checklist Checker"
            textSize = 28f
            gravity = Gravity.CENTER
        }

        taskInput = EditText(this).apply {
            hint = "Enter checklist name"
        }

        val addTaskButton = Button(this).apply {
            text = "Add checklist"
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
            text = "Back to checklists"
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

    private fun viewSubTasksPage(task: Task, category: TaskCategory) {
        mainLayout.removeAllViews()

        selectedTask = task
        selectedCategory = category

        val titleText = TextView(this).apply {
            text = category.categoryName
            textSize = 28f
            gravity = Gravity.CENTER
        }

        subTaskInput = EditText(this).apply {
            hint = "Enter item"
        }

        val addSubTaskButton = Button(this).apply {
            text = "Add item"
            setOnClickListener {
                addSubTask()
                viewSubTasksPage(task, category)
            }
        }

        val uncheckSubTasks = Button(this).apply {
            text = "Uncheck all items"
            setOnClickListener {
                uncheckSubTasks(category)
                viewSubTasksPage(task, category)
            }
        }

        val backButton = Button(this).apply {
            text = "Back to category page"
            setOnClickListener {
                viewCategoryPage(task)
            }
        }

        subTaskListLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val scrollView = ScrollView(this).apply {
            addView(subTaskListLayout)
        }

        mainLayout.addView(titleText)
        mainLayout.addView(subTaskInput)
        mainLayout.addView(addSubTaskButton)
        mainLayout.addView(uncheckSubTasks)
        mainLayout.addView(backButton)
        mainLayout.addView(scrollView)

        refreshSubTaskList(task, category)
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
        }
        return success
    }

    private fun deleteSubTask(subTaskID: Int): Boolean {
        val category = selectedCategory ?: return false
        val success = category.deleteSubTask(subTaskID)
        return success
    }

    private fun showRenameTaskDialog(task: Task) {
        val input = EditText(this).apply {
            hint = "Enter new checklist name"
            setText(task.title)
        }

        AlertDialog.Builder(this)
            .setTitle("Rename Checklist")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()

                if (newName.isNotEmpty()) {
                    task.renameTask(newName)
                    viewTaskListPage()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRenameCategoryDialog(task: Task, category: TaskCategory) {
        val input = EditText(this).apply {
            hint = "Enter new category name"
            setText(category.categoryName)
        }

        AlertDialog.Builder(this)
            .setTitle("Rename Category")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()

                if (newName.isNotEmpty()) {
                    category.renameCategory(newName)
                    viewCategoryPage(task)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRenameSubTaskDialog(task: Task, category: TaskCategory, subTask: SubTask) {
        val input = EditText(this).apply {
            hint = "Enter new item name"
            setText(subTask.subTaskName)
        }

        AlertDialog.Builder(this)
            .setTitle("Rename item")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()

                if (newName.isNotEmpty()) {
                    subTask.renameSubTask(newName)
                    viewSubTasksPage(task, category)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun refreshTaskList() {
        taskListLayout.removeAllViews()

        for (task in tasks) {
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
                    deleteTask(task.id)
                    refreshTaskList()
                }
            }

            val renameButton = Button(this).apply {
                text = "Rename"
                setOnClickListener {
                    showRenameTaskDialog(task)
                }
            }

            taskRow.addView(taskText)
            taskRow.addView(viewTaskButton)
            taskRow.addView(renameButton)
            taskRow.addView(deleteButton)

            taskListLayout.addView(taskRow)
        }
    }

    private fun refreshCategoryList(task: Task) {
        categoryListLayout.removeAllViews()
        val taskCat = task.categories
        for (category in taskCat) {
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
                    viewSubTasksPage(task, category)
                }
            }

            val deleteButton = Button(this).apply {
                text = "Delete"
                setOnClickListener {
                    deleteCategory(category.id)
                    refreshCategoryList(task)
                }
            }

            val renameButton = Button(this).apply {
                text = "Rename"
                setOnClickListener {
                    showRenameCategoryDialog(task, category)
                }
            }

            categoryRow.addView(categoryText)
            categoryRow.addView(viewCategoryButton)
            categoryRow.addView(renameButton)
            categoryRow.addView(deleteButton)

            categoryListLayout.addView(categoryRow)
        }
    }

    private fun refreshSubTaskList(task: Task, category: TaskCategory) {
        subTaskListLayout.removeAllViews()
        val categorySubClasses = category.subTasks
        for (subTask in categorySubClasses) {
            val subTaskRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 20, 0, 20)
            }

            val subTaskText = TextView(this).apply {
                text = subTask.subTaskName
                textSize = 20f
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            val checkBox = CheckBox(this).apply {
                isChecked = subTask.isCompleted

                setOnCheckedChangeListener { _, isChecked ->
                    subTask.changeSubTaskStatus(isChecked)
                    refreshSubTaskList(task, category)
                }
            }

            val deleteButton = Button(this).apply {
                text = "Delete"
                setOnClickListener {
                    deleteSubTask(subTask.id)
                    refreshSubTaskList(task, category)
                }
            }

            val renameButton = Button(this).apply {
                text = "Rename"
                setOnClickListener {
                    showRenameSubTaskDialog(task, category, subTask)
                }
            }

            subTaskRow.addView(checkBox)
            subTaskRow.addView(subTaskText)
            subTaskRow.addView(renameButton)
            subTaskRow.addView(deleteButton)

            subTaskListLayout.addView(subTaskRow)
        }
    }

    private fun uncheckSubTasks(category: TaskCategory) {
        if (category.subTasks.isEmpty()) {
            return
        }
        for (subtask in category.subTasks) {
            if (subtask.isCompleted) {
                subtask.changeSubTaskStatus(false)
            }
        }
    }
}
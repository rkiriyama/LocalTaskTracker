package com.example.localtasktracker

import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val tasks = mutableListOf<Task>()

    private val expandedTaskIds = mutableSetOf<Int>()
    private val expandedCategoryIds = mutableSetOf<Int>()

    private var nextTaskId = 1
    private var nextCategoryId = 1
    private var nextSubTaskId = 1

    private lateinit var mainLayout: LinearLayout
    private lateinit var taskListLayout: LinearLayout
    private lateinit var taskInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.hide()

        mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 120, 40, 40)
        }
        setContentView(mainLayout)

        buildStaticHeader()
    }

    // ─── Static header (title + add-task row) ────────────────────────────────

    private fun buildStaticHeader() {
        val titleText = TextView(this).apply {
            text = "Local Checklist Checker"
            textSize = 28f
            gravity = Gravity.CENTER
        }

        val addTaskRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        taskInput = EditText(this).apply {
            hint = "Enter checklist name"
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
        }

        val addTaskButton = Button(this).apply {
            text = "+ Add"
            setOnClickListener { addTask() }
        }

        addTaskRow.addView(taskInput)
        addTaskRow.addView(addTaskButton)

        taskListLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val scrollView = ScrollView(this).apply {
            addView(taskListLayout)
        }

        mainLayout.addView(titleText)
        mainLayout.addView(addTaskRow)
        mainLayout.addView(scrollView)

        refreshFullList()
    }

    // ─── Full tree render ─────────────────────────────────────────────────────

    private fun refreshFullList() {
        taskListLayout.removeAllViews()

        for (task in tasks) {
            val isTaskExpanded = task.id in expandedTaskIds

            // Task row
            taskListLayout.addView(buildTaskRow(task, isTaskExpanded))

            if (isTaskExpanded) {
                // "Add category" input row
                taskListLayout.addView(buildAddCategoryRow(task))

                for (category in task.categories) {
                    val isCatExpanded = category.id in expandedCategoryIds

                    // Category row
                    taskListLayout.addView(buildCategoryRow(task, category, isCatExpanded))

                    if (isCatExpanded) {
                        // "Add item" input row
                        taskListLayout.addView(buildAddSubTaskRow(task, category))

                        for (subTask in category.subTasks) {
                            taskListLayout.addView(buildSubTaskRow(task, category, subTask))
                        }
                    }
                }
            }
        }
    }

    // ─── Row builders ─────────────────────────────────────────────────────────

    private fun buildTaskRow(task: Task, isExpanded: Boolean): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 24, 0, 24)

            val arrow = TextView(this@MainActivity).apply {
                text = if (isExpanded) "▼" else "▶"
                textSize = 16f
                setPadding(0, 0, 16, 0)
            }

            val nameText = TextView(this@MainActivity).apply {
                text = task.title
                textSize = 20f
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
            }

            val optionsBtn = Button(this@MainActivity).apply {
                text = "⋮"
                setOnClickListener { showTaskOptionsDialog(task) }
            }

            val toggleClick = { _: android.view.View ->
                if (isExpanded) expandedTaskIds.remove(task.id)
                else expandedTaskIds.add(task.id)
                refreshFullList()
            }

            arrow.setOnClickListener(toggleClick)
            nameText.setOnClickListener(toggleClick)

            addView(arrow)
            addView(nameText)
            addView(optionsBtn)
        }
    }

    private fun buildCategoryRow(task: Task, category: TaskCategory, isExpanded: Boolean): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(80, 16, 0, 16)

            val arrow = TextView(this@MainActivity).apply {
                text = if (isExpanded) "▼" else "▶"
                textSize = 14f
                setPadding(0, 0, 16, 0)
            }

            val nameText = TextView(this@MainActivity).apply {
                text = category.categoryName
                textSize = 18f
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
            }

            val optionsBtn = Button(this@MainActivity).apply {
                text = "⋮"
                setOnClickListener { showCategoryOptionsDialog(task, category) }
            }

            val toggleClick = { _: android.view.View ->
                if (isExpanded) expandedCategoryIds.remove(category.id)
                else expandedCategoryIds.add(category.id)
                refreshFullList()
            }

            arrow.setOnClickListener(toggleClick)
            nameText.setOnClickListener(toggleClick)

            addView(arrow)
            addView(nameText)
            addView(optionsBtn)
        }
    }

    private fun buildSubTaskRow(task: Task, category: TaskCategory, subTask: SubTask): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(160, 8, 0, 8)

            val checkBox = CheckBox(this@MainActivity).apply {
                isChecked = subTask.isCompleted
                setOnCheckedChangeListener { _, checked ->
                    subTask.changeSubTaskStatus(checked)
                    refreshFullList()
                }
            }

            val nameText = TextView(this@MainActivity).apply {
                text = subTask.subTaskName
                textSize = 16f
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
            }

            val optionsBtn = Button(this@MainActivity).apply {
                text = "⋮"
                setOnClickListener { showSubTaskOptionsDialog(task, category, subTask) }
            }

            addView(checkBox)
            addView(nameText)
            addView(optionsBtn)
        }
    }

    // ─── "Add" input row builders ─────────────────────────────────────────────

    private fun buildAddCategoryRow(task: Task): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(80, 8, 0, 8)

            val input = EditText(this@MainActivity).apply {
                hint = "New category name"
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
            }

            val addBtn = Button(this@MainActivity).apply {
                text = "+ Add"
                setOnClickListener {
                    val text = input.text.toString().trim()
                    if (text.isNotEmpty()) {
                        val newCategory = TaskCategory(nextCategoryId++, text)
                        task.addCategory(newCategory)
                        expandedCategoryIds.add(newCategory.id)
                        refreshFullList()
                    }
                }
            }

            addView(input)
            addView(addBtn)
        }
    }

    private fun buildAddSubTaskRow(task: Task, category: TaskCategory): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(160, 8, 0, 8)

            val input = EditText(this@MainActivity).apply {
                hint = "New item name"
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
            }

            val addBtn = Button(this@MainActivity).apply {
                text = "+ Add"
                setOnClickListener {
                    val text = input.text.toString().trim()
                    if (text.isNotEmpty()) {
                        val newSubTask = SubTask(nextSubTaskId++, text)
                        category.addSubTask(newSubTask)
                        refreshFullList()
                    }
                }
            }

            val uncheckBtn = Button(this@MainActivity).apply {
                text = "Uncheck all"
                setOnClickListener {
                    category.subTasks.forEach { it.changeSubTaskStatus(false) }
                    refreshFullList()
                }
            }

            addView(input)
            addView(addBtn)
            addView(uncheckBtn)
        }
    }

    // ─── Add task ─────────────────────────────────────────────────────────────

    private fun addTask() {
        val text = taskInput.text.toString().trim()
        if (text.isNotEmpty()) {
            val newTask = Task(nextTaskId++, text)
            tasks.add(newTask)
            expandedTaskIds.add(newTask.id)
            taskInput.text.clear()
            refreshFullList()
        }
    }

    // ─── Delete ───────────────────────────────────────────────────────────────

    private fun deleteTask(taskId: Int) {
        tasks.removeAll { it.id == taskId }
        expandedTaskIds.remove(taskId)
        refreshFullList()
    }

    private fun deleteCategory(task: Task, categoryId: Int) {
        task.deleteCategory(categoryId)
        expandedCategoryIds.remove(categoryId)
        refreshFullList()
    }

    private fun deleteSubTask(category: TaskCategory, subTaskId: Int) {
        category.deleteSubTask(subTaskId)
        refreshFullList()
    }

    // ─── Rename dialogs ───────────────────────────────────────────────────────

    private fun showRenameTaskDialog(task: Task) {
        val input = EditText(this).apply { setText(task.title) }
        AlertDialog.Builder(this)
            .setTitle("Rename Checklist")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    task.renameTask(newName)
                    refreshFullList()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRenameCategoryDialog(task: Task, category: TaskCategory) {
        val input = EditText(this).apply { setText(category.categoryName) }
        AlertDialog.Builder(this)
            .setTitle("Rename Category")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    category.renameCategory(newName)
                    refreshFullList()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRenameSubTaskDialog(task: Task, category: TaskCategory, subTask: SubTask) {
        val input = EditText(this).apply { setText(subTask.subTaskName) }
        AlertDialog.Builder(this)
            .setTitle("Rename Item")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    subTask.renameSubTask(newName)
                    refreshFullList()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ─── Options dialogs (⋮) ─────────────────────────────────────────────────

    private fun showTaskOptionsDialog(task: Task) {
        AlertDialog.Builder(this)
            .setTitle(task.title)
            .setItems(arrayOf("Rename", "Delete")) { _, which ->
                when (which) {
                    0 -> showRenameTaskDialog(task)
                    1 -> AlertDialog.Builder(this)
                        .setTitle("Delete \"${task.title}\"?")
                        .setMessage("This will permanently delete the checklist and all its contents.")
                        .setPositiveButton("Delete") { _, _ -> deleteTask(task.id) }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCategoryOptionsDialog(task: Task, category: TaskCategory) {
        AlertDialog.Builder(this)
            .setTitle(category.categoryName)
            .setItems(arrayOf("Rename", "Delete")) { _, which ->
                when (which) {
                    0 -> showRenameCategoryDialog(task, category)
                    1 -> AlertDialog.Builder(this)
                        .setTitle("Delete \"${category.categoryName}\"?")
                        .setMessage("This will permanently delete the category and all its items.")
                        .setPositiveButton("Delete") { _, _ -> deleteCategory(task, category.id) }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSubTaskOptionsDialog(task: Task, category: TaskCategory, subTask: SubTask) {
        AlertDialog.Builder(this)
            .setTitle(subTask.subTaskName)
            .setItems(arrayOf("Rename", "Delete")) { _, which ->
                when (which) {
                    0 -> showRenameSubTaskDialog(task, category, subTask)
                    1 -> AlertDialog.Builder(this)
                        .setTitle("Delete \"${subTask.subTaskName}\"?")
                        .setPositiveButton("Delete") { _, _ -> deleteSubTask(category, subTask.id) }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

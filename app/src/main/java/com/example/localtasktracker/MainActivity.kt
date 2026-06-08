package com.example.localtasktracker

import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private val tasks = mutableListOf<Task>()
    private val expandedCategoryIds = mutableSetOf<Int>()

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

        renderTaskListScreen()
    }

    // ─── Screen 1: Task List ──────────────────────────────────────────────────

    private fun renderTaskListScreen() {
        mainLayout.removeAllViews()

        val titleText = android.widget.TextView(this).apply {
            text = "Local Checklist Checker"
            textSize = 28f
            gravity = Gravity.CENTER
        }

        val recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
        }

        mainLayout.addView(titleText)
        mainLayout.addView(recyclerView)

        val adapter = TaskAdapter(
            tasks         = tasks,
            onTaskClick   = { task -> renderTaskDetailScreen(task) },
            onOptionsClick = { task -> showTaskOptionsDialog(task) },
            onAddClick    = { showAddTaskDialog() },
            onDragFinished = { /* list already mutated in-place */ }
        )

        recyclerView.adapter = adapter

        val touchHelper = ItemTouchHelper(DragCallback(adapter))
        touchHelper.attachToRecyclerView(recyclerView)
    }

    // ─── Screen 2: Task Detail ────────────────────────────────────────────────

    private fun renderTaskDetailScreen(task: Task) {
        expandedCategoryIds.clear()
        mainLayout.removeAllViews()

        // Header: ← Back  |  task title
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 24)
        }

        val backBtn = Button(this).apply {
            text = "← Back"
            setOnClickListener { renderTaskListScreen() }
        }

        val titleText = android.widget.TextView(this).apply {
            text = task.title
            textSize = 24f
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setPadding(16, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
        }

        headerRow.addView(backBtn)
        headerRow.addView(titleText)

        val recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
        }

        mainLayout.addView(headerRow)
        mainLayout.addView(recyclerView)

        val adapter = DetailAdapter(
            task                = task,
            expandedCategoryIds = expandedCategoryIds,
            onCategoryToggle    = { cat ->
                if (cat.id in expandedCategoryIds) expandedCategoryIds.remove(cat.id)
                else expandedCategoryIds.add(cat.id)
                (recyclerView.adapter as DetailAdapter).refresh()
            },
            onCategoryOptions   = { cat -> showCategoryOptionsDialog(task, cat, recyclerView) },
            onSubTaskChecked    = { subTask, checked ->
                subTask.changeSubTaskStatus(checked)
                (recyclerView.adapter as DetailAdapter).refresh()
            },
            onSubTaskOptions    = { cat, subTask -> showSubTaskOptionsDialog(task, cat, subTask, recyclerView) },
            onAddCategory       = { showAddCategoryDialog(task, recyclerView) },
            onAddItem           = { cat -> showAddSubTaskDialog(task, cat, recyclerView) },
            onDragFinished      = { /* list already mutated in-place */ }
        )

        recyclerView.adapter = adapter
        adapter.refresh()

        val touchHelper = ItemTouchHelper(DragCallback(adapter))
        touchHelper.attachToRecyclerView(recyclerView)
    }

    private fun refreshDetail(recyclerView: RecyclerView) {
        (recyclerView.adapter as? DetailAdapter)?.refresh()
    }

    // ─── Add dialogs ──────────────────────────────────────────────────────────

    private fun showAddTaskDialog() {
        val input = EditText(this).apply { hint = "Checklist name" }
        AlertDialog.Builder(this)
            .setTitle("New Checklist")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) {
                    val newTask = Task(nextTaskId++, text)
                    tasks.add(newTask)
                    renderTaskListScreen()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddCategoryDialog(task: Task, recyclerView: RecyclerView) {
        val input = EditText(this).apply { hint = "Category name" }
        AlertDialog.Builder(this)
            .setTitle("New Category")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) {
                    val newCat = TaskCategory(nextCategoryId++, text)
                    task.addCategory(newCat)
                    expandedCategoryIds.add(newCat.id)
                    refreshDetail(recyclerView)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddSubTaskDialog(task: Task, category: TaskCategory, recyclerView: RecyclerView) {
        val input = EditText(this).apply { hint = "Item name" }
        AlertDialog.Builder(this)
            .setTitle("New Item")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) {
                    val newSubTask = SubTask(nextSubTaskId++, text)
                    category.addSubTask(newSubTask)
                    refreshDetail(recyclerView)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ─── Delete ───────────────────────────────────────────────────────────────

    private fun deleteTask(taskId: Int) {
        tasks.removeAll { it.id == taskId }
        renderTaskListScreen()
    }

    private fun deleteCategory(task: Task, categoryId: Int, recyclerView: RecyclerView) {
        task.deleteCategory(categoryId)
        expandedCategoryIds.remove(categoryId)
        refreshDetail(recyclerView)
    }

    private fun deleteSubTask(category: TaskCategory, subTaskId: Int, recyclerView: RecyclerView) {
        category.deleteSubTask(subTaskId)
        refreshDetail(recyclerView)
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
                    renderTaskListScreen()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRenameCategoryDialog(task: Task, category: TaskCategory, recyclerView: RecyclerView) {
        val input = EditText(this).apply { setText(category.categoryName) }
        AlertDialog.Builder(this)
            .setTitle("Rename Category")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    category.renameCategory(newName)
                    refreshDetail(recyclerView)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRenameSubTaskDialog(
        task: Task, category: TaskCategory, subTask: SubTask, recyclerView: RecyclerView
    ) {
        val input = EditText(this).apply { setText(subTask.subTaskName) }
        AlertDialog.Builder(this)
            .setTitle("Rename Item")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    subTask.renameSubTask(newName)
                    refreshDetail(recyclerView)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ─── Options dialogs (⋮) ─────────────────────────────────────────────────

    private fun showTaskOptionsDialog(task: Task) {
        AlertDialog.Builder(this)
            .setTitle(task.title)
            .setItems(arrayOf("Rename", "Check All", "Uncheck All", "Duplicate", "Delete")) { _, which ->
                when (which) {
                    0 -> showRenameTaskDialog(task)
                    1 -> task.categories.forEach { cat ->
                            cat.subTasks.forEach { it.changeSubTaskStatus(true) }
                         }
                    2 -> task.categories.forEach { cat ->
                            cat.subTasks.forEach { it.changeSubTaskStatus(false) }
                         }
                    3 -> showDuplicateTaskDialog(task)
                    4 -> AlertDialog.Builder(this)
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

    private fun showCategoryOptionsDialog(task: Task, category: TaskCategory, recyclerView: RecyclerView) {
        AlertDialog.Builder(this)
            .setTitle(category.categoryName)
            .setItems(arrayOf("Rename", "Uncheck All Items", "Delete")) { _, which ->
                when (which) {
                    0 -> showRenameCategoryDialog(task, category, recyclerView)
                    1 -> {
                        category.subTasks.forEach { it.changeSubTaskStatus(false) }
                        refreshDetail(recyclerView)
                    }
                    2 -> AlertDialog.Builder(this)
                            .setTitle("Delete \"${category.categoryName}\"?")
                            .setMessage("This will permanently delete the category and all its items.")
                            .setPositiveButton("Delete") { _, _ -> deleteCategory(task, category.id, recyclerView) }
                            .setNegativeButton("Cancel", null)
                            .show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSubTaskOptionsDialog(
        task: Task, category: TaskCategory, subTask: SubTask, recyclerView: RecyclerView
    ) {
        AlertDialog.Builder(this)
            .setTitle(subTask.subTaskName)
            .setItems(arrayOf("Rename", "Delete")) { _, which ->
                when (which) {
                    0 -> showRenameSubTaskDialog(task, category, subTask, recyclerView)
                    1 -> AlertDialog.Builder(this)
                            .setTitle("Delete \"${subTask.subTaskName}\"?")
                            .setPositiveButton("Delete") { _, _ -> deleteSubTask(category, subTask.id, recyclerView) }
                            .setNegativeButton("Cancel", null)
                            .show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ─── Duplicate ────────────────────────────────────────────────────────────

    private fun showDuplicateTaskDialog(task: Task) {
        val input = EditText(this).apply { setText("${task.title} (copy)") }
        AlertDialog.Builder(this)
            .setTitle("Duplicate Checklist")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) duplicateTask(task, newName)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun duplicateTask(original: Task, newName: String) {
        val newTask = Task(nextTaskId++, newName)
        for (category in original.categories) {
            val newCat = TaskCategory(nextCategoryId++, category.categoryName)
            for (subTask in category.subTasks) {
                newCat.addSubTask(SubTask(nextSubTaskId++, subTask.subTaskName))
            }
            newTask.addCategory(newCat)
        }
        tasks.add(newTask)
        renderTaskListScreen()
    }
}

package com.example.localtasktracker

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private val tasks = mutableListOf<Task>()
    private val expandedCategoryIds = mutableSetOf<Int>()
    private val expandedSubTaskIds  = mutableSetOf<Int>()

    // Per-task persisted expand/collapse state (keyed by task ID)
    private val savedExpandedCategoryIds = mutableMapOf<Int, MutableSet<Int>>()
    private val savedExpandedSubTaskIds  = mutableMapOf<Int, MutableSet<Int>>()

    // Holds the task to be backed up while waiting for the SAF file-picker result
    private var pendingBackupTask: Task? = null

    // SAF launcher: save one task to a user-chosen file
    private val backupSingleTaskLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            val task = pendingBackupTask ?: return@registerForActivityResult
            pendingBackupTask = null
            if (uri != null) writeBackupToUri(uri, serializeTasksToJson(listOf(task)))
        }

    // SAF launcher: save all tasks to a user-chosen file
    private val backupAllTasksLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) writeBackupToUri(uri, serializeTasksToJson(tasks))
        }

    // SAF launcher: open a backup file and show the load-checklist picker
    private val loadChecklistLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) loadChecklistFromUri(uri)
        }

    private var nextTaskId     = 1
    private var nextCategoryId = 1
    private var nextSubTaskId  = 1
    private var nextSubItemId  = 1

    private lateinit var mainLayout: LinearLayout

    companion object {
        private const val PREFS_NAME = "LocalTaskTrackerPrefs"
        private const val KEY_DATA   = "tasks_json"
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 120, 40, 110)
        }
        setContentView(mainLayout)
        loadData()
        renderTaskListScreen()
    }

    // ─── Persistence ──────────────────────────────────────────────────────────

    private fun saveData() {
        val root = JSONArray()
        for (task in tasks) {
            val tObj = JSONObject()
            tObj.put("id",    task.id)
            tObj.put("title", task.title)

            // Persist expand/collapse state for this task
            val expCatArr = JSONArray()
            savedExpandedCategoryIds[task.id]?.forEach { expCatArr.put(it) }
            tObj.put("expandedCategoryIds", expCatArr)

            val expSubArr = JSONArray()
            savedExpandedSubTaskIds[task.id]?.forEach { expSubArr.put(it) }
            tObj.put("expandedSubTaskIds", expSubArr)

            val cats = JSONArray()
            for (cat in task.categories) {
                val cObj = JSONObject()
                cObj.put("id",   cat.id)
                cObj.put("name", cat.categoryName)

                val subs = JSONArray()
                for (sub in cat.subTasks) {
                    val sObj = JSONObject()
                    sObj.put("id",        sub.id)
                    sObj.put("name",      sub.subTaskName)
                    sObj.put("completed", sub.isCompleted)

                    // Persist subitems
                    val subItemsArr = JSONArray()
                    for (si in sub.subItems) {
                        val siObj = JSONObject()
                        siObj.put("id",        si.id)
                        siObj.put("name",      si.subItemName)
                        siObj.put("completed", si.isCompleted)
                        subItemsArr.put(siObj)
                    }
                    sObj.put("subItems", subItemsArr)
                    subs.put(sObj)
                }
                cObj.put("subTasks", subs)
                cats.put(cObj)
            }
            tObj.put("categories", cats)
            root.put(tObj)
        }
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DATA, root.toString())
            .apply()
    }

    private fun loadData() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json  = prefs.getString(KEY_DATA, null) ?: return
        try {
            tasks.clear()
            savedExpandedCategoryIds.clear()
            savedExpandedSubTaskIds.clear()
            var maxTaskId    = 0
            var maxCatId     = 0
            var maxSubId     = 0
            var maxSubItemId = 0

            val root = JSONArray(json)
            for (ti in 0 until root.length()) {
                val tObj = root.getJSONObject(ti)
                val task = Task(tObj.optInt("id", nextTaskId++), tObj.optString("title", "Untitled"))
                if (task.id > maxTaskId) maxTaskId = task.id

                // Restore per-task expand/collapse state
                val expCatArr = tObj.optJSONArray("expandedCategoryIds")
                if (expCatArr != null) {
                    val catSet = mutableSetOf<Int>()
                    for (i in 0 until expCatArr.length()) catSet.add(expCatArr.getInt(i))
                    savedExpandedCategoryIds[task.id] = catSet
                }
                val expSubArr = tObj.optJSONArray("expandedSubTaskIds")
                if (expSubArr != null) {
                    val subSet = mutableSetOf<Int>()
                    for (i in 0 until expSubArr.length()) subSet.add(expSubArr.getInt(i))
                    savedExpandedSubTaskIds[task.id] = subSet
                }

                val cats = tObj.optJSONArray("categories") ?: JSONArray()
                for (ci in 0 until cats.length()) {
                    val cObj = cats.getJSONObject(ci)
                    val cat  = TaskCategory(cObj.optInt("id", nextCategoryId++), cObj.optString("name", "Unnamed"))
                    if (cat.id > maxCatId) maxCatId = cat.id

                    val subs = cObj.optJSONArray("subTasks") ?: JSONArray()
                    for (si in 0 until subs.length()) {
                        val sObj = subs.getJSONObject(si)
                        val sub  = SubTask(
                            sObj.optInt("id", nextSubTaskId++),
                            sObj.optString("name", "Unnamed"),
                            sObj.optBoolean("completed", false)
                        )
                        if (sub.id > maxSubId) maxSubId = sub.id

                        // Load subitems
                        val subItemsArr = sObj.optJSONArray("subItems") ?: JSONArray()
                        for (sii in 0 until subItemsArr.length()) {
                            val siObj = subItemsArr.getJSONObject(sii)
                            val subItem = SubItem(
                                siObj.optInt("id", nextSubItemId++),
                                siObj.optString("name", "Unnamed"),
                                siObj.optBoolean("completed", false)
                            )
                            if (subItem.id > maxSubItemId) maxSubItemId = subItem.id
                            sub.subItems.add(subItem)
                        }
                        cat.subTasks.add(sub)
                    }
                    task.categories.add(cat)
                }
                tasks.add(task)
            }

            nextTaskId     = maxTaskId    + 1
            nextCategoryId = maxCatId     + 1
            nextSubTaskId  = maxSubId     + 1
            nextSubItemId  = maxSubItemId + 1

        } catch (e: Exception) {
            tasks.clear()
            savedExpandedCategoryIds.clear()
            savedExpandedSubTaskIds.clear()
        }
    }

    // ─── Screen 1: Task List ──────────────────────────────────────────────────

    private fun renderTaskListScreen() {
        mainLayout.removeAllViews()

        // ── Header row: [Checklist title] [⋮] ────────────────────────────────
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleText = android.widget.TextView(this).apply {
            text = "Checklist"
            textSize = 28f
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val globalOptionsBtn = Button(this).apply {
            text = "⋮"
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Options")
                    .setItems(arrayOf("Backup All Checklists")) { _, which ->
                        when (which) {
                            0 -> backupAllTasksLauncher.launch("localtasktracker_backup_all.json")
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
        headerRow.addView(titleText)
        headerRow.addView(globalOptionsBtn)

        val recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        // ── Bottom row: [Load Checklist] [Add Checklist (in adapter footer)] ─
        // Load Checklist lives here; Add Checklist is rendered by TaskAdapter's footer row
        val loadBtn = Button(this).apply {
            text = "Load Checklist"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                loadChecklistLauncher.launch(arrayOf("application/json", "*/*"))
            }
        }

        mainLayout.addView(headerRow)
        mainLayout.addView(recyclerView)
        mainLayout.addView(loadBtn)

        val adapter = TaskAdapter(
            tasks          = tasks,
            onTaskClick    = { task -> renderTaskDetailScreen(task) },
            onOptionsClick = { task -> showTaskOptionsDialog(task) },
            onAddClick     = { showAddTaskDialog() },
            onDragFinished = { saveData() }
        )
        recyclerView.adapter = adapter
        ItemTouchHelper(DragCallback(adapter)).attachToRecyclerView(recyclerView)
    }

    // ─── Screen 2: Task Detail ────────────────────────────────────────────────

    private fun renderTaskDetailScreen(task: Task, startInEditMode: Boolean = false) {
        // Restore saved expand/collapse state, or default to all-expanded for new tasks
        expandedCategoryIds.clear()
        expandedSubTaskIds.clear()
        val savedCatIds = savedExpandedCategoryIds[task.id]
        val savedSubIds = savedExpandedSubTaskIds[task.id]
        if (savedCatIds != null) {
            expandedCategoryIds.addAll(savedCatIds)
        } else {
            // No saved state: default to all expanded
            task.categories.forEach { expandedCategoryIds.add(it.id) }
        }
        if (savedSubIds != null) {
            expandedSubTaskIds.addAll(savedSubIds)
        } else {
            // No saved state: expand all subtasks that have subitems
            task.categories.forEach { cat ->
                cat.subTasks.forEach { sub ->
                    if (sub.hasSubItems()) expandedSubTaskIds.add(sub.id)
                }
            }
        }

        mainLayout.removeAllViews()

        // ── Header: [← Back] [title] [⋮] [Done] ─────────────────────────────
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
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val taskOptionsBtn = Button(this).apply { text = "⋮" }
        val doneBtn = Button(this).apply {
            text = "Done"
            visibility = if (startInEditMode) android.view.View.VISIBLE else android.view.View.GONE
        }
        headerRow.addView(backBtn)
        headerRow.addView(titleText)
        headerRow.addView(taskOptionsBtn)
        headerRow.addView(doneBtn)

        val recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        mainLayout.addView(headerRow)
        mainLayout.addView(recyclerView)

        val adapter = DetailAdapter(
            task                = task,
            expandedCategoryIds = expandedCategoryIds,
            expandedSubTaskIds  = expandedSubTaskIds,
            onCategoryToggle    = { cat ->
                if (cat.id in expandedCategoryIds) expandedCategoryIds.remove(cat.id)
                else expandedCategoryIds.add(cat.id)
                saveExpandState(task)
                refreshDetail(recyclerView)
            },
            onCategoryOptions   = { cat -> showCategoryOptionsDialog(task, cat, recyclerView) },
            onSubTaskToggle     = { sub ->
                if (sub.id in expandedSubTaskIds) expandedSubTaskIds.remove(sub.id)
                else expandedSubTaskIds.add(sub.id)
                saveExpandState(task)
                refreshDetail(recyclerView)
            },
            onSubTaskChecked    = { subTask, checked ->
                subTask.changeSubTaskStatus(checked)
                refreshDetail(recyclerView)
                saveData()
            },
            onSubTaskOptions    = { cat, subTask -> showSubTaskOptionsDialog(task, cat, subTask, recyclerView) },
            onAddSubItem        = { subTask -> showAddSubItemDialog(subTask, recyclerView) },
            onSubItemChecked    = { subItem, checked ->
                subItem.changeSubItemStatus(checked)
                refreshDetail(recyclerView)
                saveData()
            },
            onSubItemOptions    = { subTask, subItem -> showSubItemOptionsDialog(subTask, subItem, recyclerView) },
            onAddCategory       = { showAddCategoryDialog(task, recyclerView) },
            onAddItem           = { cat -> showAddSubTaskDialog(task, cat, recyclerView) },
            onDragFinished      = { saveData() }
        )

        recyclerView.adapter = adapter
        if (startInEditMode) adapter.setEditMode(true) else adapter.refresh()
        ItemTouchHelper(DragCallback(adapter)).attachToRecyclerView(recyclerView)

        // ── Task ⋮ ────────────────────────────────────────────────────────────
        taskOptionsBtn.setOnClickListener {
            if (adapter.isEditMode) {
                AlertDialog.Builder(this)
                    .setTitle(task.title)
                    .setItems(arrayOf("Rename", "Check All", "Uncheck All", "Expand All", "Collapse All", "Backup", "Duplicate", "Delete")) { _, which ->
                        when (which) {
                            0 -> showRenameTaskDialog(task, recyclerView, titleText)
                            1 -> {
                                task.categories.forEach { cat ->
                                    cat.subTasks.forEach { sub ->
                                        sub.changeSubTaskStatus(true)
                                        sub.subItems.forEach { it.changeSubItemStatus(true) }
                                    }
                                }
                                saveData(); adapter.refresh()
                            }
                            2 -> {
                                task.categories.forEach { cat ->
                                    cat.subTasks.forEach { sub ->
                                        sub.changeSubTaskStatus(false)
                                        sub.subItems.forEach { it.changeSubItemStatus(false) }
                                    }
                                }
                                saveData(); adapter.refresh()
                            }
                            3 -> {
                                expandedCategoryIds.clear()
                                expandedSubTaskIds.clear()
                                task.categories.forEach { cat ->
                                    expandedCategoryIds.add(cat.id)
                                    cat.subTasks.forEach { sub ->
                                        if (sub.hasSubItems()) expandedSubTaskIds.add(sub.id)
                                    }
                                }
                                saveExpandState(task); adapter.refresh()
                            }
                            4 -> {
                                expandedCategoryIds.clear()
                                expandedSubTaskIds.clear()
                                saveExpandState(task); adapter.refresh()
                            }
                            5 -> {
                                pendingBackupTask = task
                                val safeName = task.title
                                    .replace(Regex("[^a-zA-Z0-9_\\- ]"), "")
                                    .trim()
                                    .replace(" ", "_")
                                    .ifEmpty { "backup" }
                                backupSingleTaskLauncher.launch("${safeName}_backup.json")
                            }
                            6 -> showDuplicateTaskDialog(task)
                            7 -> AlertDialog.Builder(this)
                                    .setTitle("Delete \"${task.title}\"?")
                                    .setMessage("This will permanently delete the checklist and all its contents.")
                                    .setPositiveButton("Delete") { _, _ -> deleteTask(task.id) }
                                    .setNegativeButton("Cancel", null).show()
                        }
                    }
                    .setNegativeButton("Cancel", null).show()
            } else {
                AlertDialog.Builder(this)
                    .setTitle(task.title)
                    .setItems(arrayOf("Edit", "Check All", "Uncheck All", "Expand All", "Collapse All", "Backup")) { _, which ->
                        when (which) {
                            0 -> {
                                adapter.setEditMode(true)
                                doneBtn.visibility = android.view.View.VISIBLE
                            }
                            1 -> {
                                task.categories.forEach { cat ->
                                    cat.subTasks.forEach { sub ->
                                        sub.changeSubTaskStatus(true)
                                        sub.subItems.forEach { it.changeSubItemStatus(true) }
                                    }
                                }
                                saveData(); adapter.refresh()
                            }
                            2 -> {
                                task.categories.forEach { cat ->
                                    cat.subTasks.forEach { sub ->
                                        sub.changeSubTaskStatus(false)
                                        sub.subItems.forEach { it.changeSubItemStatus(false) }
                                    }
                                }
                                saveData(); adapter.refresh()
                            }
                            3 -> {
                                expandedCategoryIds.clear()
                                expandedSubTaskIds.clear()
                                task.categories.forEach { cat ->
                                    expandedCategoryIds.add(cat.id)
                                    cat.subTasks.forEach { sub ->
                                        if (sub.hasSubItems()) expandedSubTaskIds.add(sub.id)
                                    }
                                }
                                saveExpandState(task); adapter.refresh()
                            }
                            4 -> {
                                expandedCategoryIds.clear()
                                expandedSubTaskIds.clear()
                                saveExpandState(task); adapter.refresh()
                            }
                            5 -> {
                                pendingBackupTask = task
                                val safeName = task.title
                                    .replace(Regex("[^a-zA-Z0-9_\\- ]"), "")
                                    .trim()
                                    .replace(" ", "_")
                                    .ifEmpty { "backup" }
                                backupSingleTaskLauncher.launch("${safeName}_backup.json")
                            }
                        }
                    }
                    .setNegativeButton("Cancel", null).show()
            }
        }

        doneBtn.setOnClickListener {
            adapter.setEditMode(false)
            doneBtn.visibility = android.view.View.GONE
        }
    }

    private fun refreshDetail(recyclerView: RecyclerView) {
        (recyclerView.adapter as? DetailAdapter)?.refresh()
    }

    /**
     * Snapshots the current in-memory expand/collapse sets into the per-task
     * saved maps and immediately persists them to SharedPreferences.
     */
    private fun saveExpandState(task: Task) {
        savedExpandedCategoryIds[task.id] = expandedCategoryIds.toMutableSet()
        savedExpandedSubTaskIds[task.id]  = expandedSubTaskIds.toMutableSet()
        saveData()
    }

    // ─── Load helpers ─────────────────────────────────────────────────────────

    /**
     * Reads a backup JSON file from the SAF URI, parses it, and shows the
     * task-picker dialog. Never modifies the backup file.
     */
    private fun loadChecklistFromUri(uri: Uri) {
        val json = try {
            contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            } ?: run {
                Toast.makeText(this, "Could not read file", Toast.LENGTH_LONG).show()
                return
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to open file: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }

        val root = try {
            JSONArray(json)
        } catch (e: Exception) {
            AlertDialog.Builder(this)
                .setTitle("Invalid Backup File")
                .setMessage("The selected file is not a valid backup.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        if (root.length() == 0) {
            AlertDialog.Builder(this)
                .setTitle("Empty Backup")
                .setMessage("No checklists found in this backup file.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        showLoadChecklistDialog(root)
    }

    /**
     * Shows a dialog listing the task names from the parsed backup JSON array.
     * Selecting one copies it into the live task list with fresh IDs.
     * The backup data is never modified.
     */
    private fun showLoadChecklistDialog(root: JSONArray) {
        val titles = Array(root.length()) { i ->
            root.getJSONObject(i).optString("title", "Untitled")
        }

        AlertDialog.Builder(this)
            .setTitle("Load Checklist")
            .setItems(titles) { _, which ->
                val tObj = root.getJSONObject(which)
                importTaskFromJson(tObj)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Copies a single task from a backup JSON object into the live task list,
     * assigning fresh IDs to every element. Completion states are preserved.
     * The source JSON object is never modified.
     */
    private fun importTaskFromJson(tObj: JSONObject) {
        val newTask = Task(nextTaskId++, tObj.optString("title", "Untitled"))

        val cats = tObj.optJSONArray("categories") ?: JSONArray()
        for (ci in 0 until cats.length()) {
            val cObj = cats.getJSONObject(ci)
            val newCat = TaskCategory(nextCategoryId++, cObj.optString("name", "Unnamed"))

            val subs = cObj.optJSONArray("subTasks") ?: JSONArray()
            for (si in 0 until subs.length()) {
                val sObj = subs.getJSONObject(si)
                val newSub = SubTask(
                    nextSubTaskId++,
                    sObj.optString("name", "Unnamed"),
                    sObj.optBoolean("completed", false)
                )

                val subItemsArr = sObj.optJSONArray("subItems") ?: JSONArray()
                for (sii in 0 until subItemsArr.length()) {
                    val siObj = subItemsArr.getJSONObject(sii)
                    newSub.addSubItem(SubItem(
                        nextSubItemId++,
                        siObj.optString("name", "Unnamed"),
                        siObj.optBoolean("completed", false)
                    ))
                }
                newCat.addSubTask(newSub)
            }
            newTask.addCategory(newCat)
        }

        tasks.add(newTask)
        saveData()
        renderTaskListScreen()
        Toast.makeText(this, "\"${newTask.title}\" loaded successfully", Toast.LENGTH_SHORT).show()
    }

    private fun focusInput(dialog: AlertDialog, input: EditText, selectAll: Boolean = false) {
        input.requestFocus()
        if (selectAll) input.selectAll()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    }

    // ─── Backup helpers ───────────────────────────────────────────────────────

    /**
     * Serializes a list of tasks into a JSON array string using the same
     * format as saveData(). Read-only — never touches live data.
     */
    private fun serializeTasksToJson(taskList: List<Task>): String {
        val root = JSONArray()
        for (task in taskList) {
            val tObj = JSONObject()
            tObj.put("id",    task.id)
            tObj.put("title", task.title)

            val expCatArr = JSONArray()
            savedExpandedCategoryIds[task.id]?.forEach { expCatArr.put(it) }
            tObj.put("expandedCategoryIds", expCatArr)

            val expSubArr = JSONArray()
            savedExpandedSubTaskIds[task.id]?.forEach { expSubArr.put(it) }
            tObj.put("expandedSubTaskIds", expSubArr)

            val cats = JSONArray()
            for (cat in task.categories) {
                val cObj = JSONObject()
                cObj.put("id",   cat.id)
                cObj.put("name", cat.categoryName)
                val subs = JSONArray()
                for (sub in cat.subTasks) {
                    val sObj = JSONObject()
                    sObj.put("id",        sub.id)
                    sObj.put("name",      sub.subTaskName)
                    sObj.put("completed", sub.isCompleted)
                    val subItemsArr = JSONArray()
                    for (si in sub.subItems) {
                        val siObj = JSONObject()
                        siObj.put("id",        si.id)
                        siObj.put("name",      si.subItemName)
                        siObj.put("completed", si.isCompleted)
                        subItemsArr.put(siObj)
                    }
                    sObj.put("subItems", subItemsArr)
                    subs.put(sObj)
                }
                cObj.put("subTasks", subs)
                cats.put(cObj)
            }
            tObj.put("categories", cats)
            root.put(tObj)
        }
        return root.toString(2) // pretty-print with 2-space indent
    }

    /**
     * Writes a JSON string to the URI returned by the SAF file picker.
     * Shows a Toast on success or failure. Never modifies live data.
     */
    private fun writeBackupToUri(uri: Uri, json: String) {
        try {
            contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(json.toByteArray(Charsets.UTF_8))
            }
            Toast.makeText(this, "Backup saved successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Backup failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // ─── Add dialogs ──────────────────────────────────────────────────────────

    private fun showAddTaskDialog() {
        val input = EditText(this).apply { hint = "Checklist name" }
        val dialog = AlertDialog.Builder(this)
            .setTitle("New Checklist")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) {
                    tasks.add(Task(nextTaskId++, text))
                    saveData()
                    renderTaskListScreen()
                }
            }
            .setNegativeButton("Cancel", null).show()
        focusInput(dialog, input)
    }

    private fun showAddCategoryDialog(task: Task, recyclerView: RecyclerView) {
        val input = EditText(this).apply { hint = "Category name" }
        val dialog = AlertDialog.Builder(this)
            .setTitle("New Category")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) {
                    val newCat = TaskCategory(nextCategoryId++, text)
                    task.addCategory(newCat)
                    expandedCategoryIds.add(newCat.id)
                    saveData()
                    refreshDetail(recyclerView)
                }
            }
            .setNegativeButton("Cancel", null).show()
        focusInput(dialog, input)
    }

    private fun showAddSubTaskDialog(task: Task, category: TaskCategory, recyclerView: RecyclerView) {
        val input = EditText(this).apply { hint = "Item name" }
        val dialog = AlertDialog.Builder(this)
            .setTitle("New Item")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) {
                    category.addSubTask(SubTask(nextSubTaskId++, text))
                    saveData()
                    refreshDetail(recyclerView)
                }
            }
            .setNegativeButton("Cancel", null).show()
        focusInput(dialog, input)
    }

    private fun showAddSubItemDialog(subTask: SubTask, recyclerView: RecyclerView) {
        val input = EditText(this).apply { hint = "Subitem name" }
        val dialog = AlertDialog.Builder(this)
            .setTitle("New Subitem")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) {
                    subTask.addSubItem(SubItem(nextSubItemId++, text))
                    // Ensure the parent subtask expands to show the new subitem
                    expandedSubTaskIds.add(subTask.id)
                    saveData()
                    refreshDetail(recyclerView)
                }
            }
            .setNegativeButton("Cancel", null).show()
        focusInput(dialog, input)
    }

    // ─── Delete ───────────────────────────────────────────────────────────────

    private fun deleteTask(taskId: Int) {
        tasks.removeAll { it.id == taskId }
        saveData()
        renderTaskListScreen()
    }

    private fun deleteCategory(task: Task, categoryId: Int, recyclerView: RecyclerView) {
        task.deleteCategory(categoryId)
        expandedCategoryIds.remove(categoryId)
        saveData()
        refreshDetail(recyclerView)
    }

    private fun deleteSubTask(category: TaskCategory, subTaskId: Int, recyclerView: RecyclerView) {
        category.deleteSubTask(subTaskId)
        expandedSubTaskIds.remove(subTaskId)
        saveData()
        refreshDetail(recyclerView)
    }

    private fun deleteSubItem(subTask: SubTask, subItemId: Int, recyclerView: RecyclerView) {
        subTask.deleteSubItem(subItemId)
        saveData()
        refreshDetail(recyclerView)
    }

    // ─── Rename dialogs ───────────────────────────────────────────────────────

    private fun showRenameTaskDialog(task: Task) {
        val input = EditText(this).apply { setText(task.title) }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Rename Checklist")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    task.renameTask(newName)
                    saveData()
                    renderTaskListScreen()
                }
            }
            .setNegativeButton("Cancel", null).show()
        focusInput(dialog, input, selectAll = true)
    }

    private fun showRenameTaskDialog(task: Task, recyclerView: RecyclerView, titleText: android.widget.TextView) {
        val input = EditText(this).apply { setText(task.title) }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Rename Checklist")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    task.renameTask(newName)
                    titleText.text = task.title
                    saveData()
                }
            }
            .setNegativeButton("Cancel", null).show()
        focusInput(dialog, input, selectAll = true)
    }

    private fun showRenameCategoryDialog(task: Task, category: TaskCategory, recyclerView: RecyclerView) {
        val input = EditText(this).apply { setText(category.categoryName) }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Rename Category")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    category.renameCategory(newName)
                    saveData()
                    refreshDetail(recyclerView)
                }
            }
            .setNegativeButton("Cancel", null).show()
        focusInput(dialog, input, selectAll = true)
    }

    private fun showRenameSubTaskDialog(
        task: Task, category: TaskCategory, subTask: SubTask, recyclerView: RecyclerView
    ) {
        val input = EditText(this).apply { setText(subTask.subTaskName) }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Rename Item")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    subTask.renameSubTask(newName)
                    saveData()
                    refreshDetail(recyclerView)
                }
            }
            .setNegativeButton("Cancel", null).show()
        focusInput(dialog, input, selectAll = true)
    }

    private fun showRenameSubItemDialog(subTask: SubTask, subItem: SubItem, recyclerView: RecyclerView) {
        val input = EditText(this).apply { setText(subItem.subItemName) }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Rename Subitem")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    subItem.renameSubItem(newName)
                    saveData()
                    refreshDetail(recyclerView)
                }
            }
            .setNegativeButton("Cancel", null).show()
        focusInput(dialog, input, selectAll = true)
    }

    // ─── Options dialogs (⋮) ─────────────────────────────────────────────────

    private fun showTaskOptionsDialog(task: Task) {
        AlertDialog.Builder(this)
            .setTitle(task.title)
            .setItems(arrayOf("Rename", "Check All", "Uncheck All", "Expand All", "Collapse All", "Duplicate", "Delete")) { _, which ->
                when (which) {
                    0 -> showRenameTaskDialog(task)
                    1 -> {
                        task.categories.forEach { cat ->
                            cat.subTasks.forEach { sub ->
                                sub.changeSubTaskStatus(true)
                                sub.subItems.forEach { it.changeSubItemStatus(true) }
                            }
                        }
                        saveData(); renderTaskListScreen()
                    }
                    2 -> {
                        task.categories.forEach { cat ->
                            cat.subTasks.forEach { sub ->
                                sub.changeSubTaskStatus(false)
                                sub.subItems.forEach { it.changeSubItemStatus(false) }
                            }
                        }
                        saveData(); renderTaskListScreen()
                    }
                    3 -> {
                        // Expand All: open the task with all categories and subtask groups expanded
                        val catIds = mutableSetOf<Int>()
                        val subIds = mutableSetOf<Int>()
                        task.categories.forEach { cat ->
                            catIds.add(cat.id)
                            cat.subTasks.forEach { sub ->
                                if (sub.hasSubItems()) subIds.add(sub.id)
                            }
                        }
                        savedExpandedCategoryIds[task.id] = catIds
                        savedExpandedSubTaskIds[task.id]  = subIds
                        saveData()
                        renderTaskDetailScreen(task)
                    }
                    4 -> {
                        // Collapse All: open the task with all categories and subtask groups collapsed
                        savedExpandedCategoryIds[task.id] = mutableSetOf()
                        savedExpandedSubTaskIds[task.id]  = mutableSetOf()
                        saveData()
                        renderTaskDetailScreen(task)
                    }
                    5 -> showDuplicateTaskDialog(task)
                    6 -> AlertDialog.Builder(this)
                            .setTitle("Delete \"${task.title}\"?")
                            .setMessage("This will permanently delete the checklist and all its contents.")
                            .setPositiveButton("Delete") { _, _ -> deleteTask(task.id) }
                            .setNegativeButton("Cancel", null).show()
                }
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showCategoryOptionsDialog(task: Task, category: TaskCategory, recyclerView: RecyclerView) {
        AlertDialog.Builder(this)
            .setTitle(category.categoryName)
            .setItems(arrayOf("Rename", "Uncheck All Items", "Delete")) { _, which ->
                when (which) {
                    0 -> showRenameCategoryDialog(task, category, recyclerView)
                    1 -> {
                        category.subTasks.forEach { sub ->
                            sub.changeSubTaskStatus(false)
                            sub.subItems.forEach { it.changeSubItemStatus(false) }
                        }
                        saveData(); refreshDetail(recyclerView)
                    }
                    2 -> AlertDialog.Builder(this)
                            .setTitle("Delete \"${category.categoryName}\"?")
                            .setMessage("This will permanently delete the category and all its items.")
                            .setPositiveButton("Delete") { _, _ -> deleteCategory(task, category.id, recyclerView) }
                            .setNegativeButton("Cancel", null).show()
                }
            }
            .setNegativeButton("Cancel", null).show()
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
                            .setNegativeButton("Cancel", null).show()
                }
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showSubItemOptionsDialog(subTask: SubTask, subItem: SubItem, recyclerView: RecyclerView) {
        AlertDialog.Builder(this)
            .setTitle(subItem.subItemName)
            .setItems(arrayOf("Rename", "Delete")) { _, which ->
                when (which) {
                    0 -> showRenameSubItemDialog(subTask, subItem, recyclerView)
                    1 -> AlertDialog.Builder(this)
                            .setTitle("Delete \"${subItem.subItemName}\"?")
                            .setPositiveButton("Delete") { _, _ -> deleteSubItem(subTask, subItem.id, recyclerView) }
                            .setNegativeButton("Cancel", null).show()
                }
            }
            .setNegativeButton("Cancel", null).show()
    }

    // ─── Duplicate ────────────────────────────────────────────────────────────

    private fun showDuplicateTaskDialog(task: Task) {
        val input = EditText(this).apply { setText("${task.title} (copy)") }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Duplicate Checklist")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) duplicateTask(task, newName)
            }
            .setNegativeButton("Cancel", null).show()
        focusInput(dialog, input, selectAll = true)
    }

    private fun duplicateTask(original: Task, newName: String) {
        val newTask = Task(nextTaskId++, newName)
        for (category in original.categories) {
            val newCat = TaskCategory(nextCategoryId++, category.categoryName)
            for (subTask in category.subTasks) {
                val newSub = SubTask(nextSubTaskId++, subTask.subTaskName)
                for (subItem in subTask.subItems) {
                    newSub.addSubItem(SubItem(nextSubItemId++, subItem.subItemName))
                }
                newCat.addSubTask(newSub)
            }
            newTask.addCategory(newCat)
        }
        tasks.add(newTask)
        saveData()
        renderTaskListScreen()
    }
}

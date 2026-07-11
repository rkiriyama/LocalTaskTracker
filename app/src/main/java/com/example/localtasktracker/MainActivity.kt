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

    private val savedExpandedCategoryIds = mutableMapOf<Int, MutableSet<Int>>()
    private val savedExpandedSubTaskIds  = mutableMapOf<Int, MutableSet<Int>>()

    private var pendingBackupTask: Task? = null

    private val backupSingleTaskLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            val task = pendingBackupTask ?: return@registerForActivityResult
            pendingBackupTask = null
            if (uri != null) writeBackupToUri(uri, serializeTasksToJson(listOf(task)))
        }

    private val backupAllTasksLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) writeBackupToUri(uri, serializeTasksToJson(tasks))
        }

    private val loadChecklistLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) loadChecklistFromUri(uri)
        }

    private var nextTaskId    = 1
    private var nextNodeId    = 1   // single counter for all Node IDs

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

    /** Recursively serialises a [Node] and all its children. */
    private fun nodeToJson(node: Node): JSONObject {
        val obj = JSONObject()
        obj.put("id",        node.id)
        obj.put("name",      node.name)
        obj.put("type",      node.nodeType.name)
        obj.put("completed", node.isCompleted)
        val childArr = JSONArray()
        for (child in node.children) childArr.put(nodeToJson(child))
        obj.put("children", childArr)
        return obj
    }

    /** Recursively deserialises a [Node] from JSON, tracking the max seen ID. */
    private fun nodeFromJson(obj: JSONObject, maxId: IntArray): Node {
        val id   = obj.optInt("id", nextNodeId++)
        val name = obj.optString("name", "Unnamed")
        val type = try {
            NodeType.valueOf(obj.optString("type", NodeType.CATEGORY.name))
        } catch (e: IllegalArgumentException) { NodeType.CATEGORY }
        val completed = obj.optBoolean("completed", false)
        if (id > maxId[0]) maxId[0] = id
        val node = Node(id = id, name = name, nodeType = type, isCompleted = completed)
        val childArr = obj.optJSONArray("children") ?: JSONArray()
        for (i in 0 until childArr.length()) {
            node.children.add(nodeFromJson(childArr.getJSONObject(i), maxId))
        }
        return node
    }

    private fun saveData() {
        val root = JSONArray()
        for (task in tasks) {
            val tObj = JSONObject()
            tObj.put("id",    task.id)
            tObj.put("title", task.title)

            val expCatArr = JSONArray()
            savedExpandedCategoryIds[task.id]?.forEach { expCatArr.put(it) }
            tObj.put("expandedCategoryIds", expCatArr)

            val expSubArr = JSONArray()
            savedExpandedSubTaskIds[task.id]?.forEach { expSubArr.put(it) }
            tObj.put("expandedSubTaskIds", expSubArr)

            val childArr = JSONArray()
            for (node in task.children) childArr.put(nodeToJson(node))
            tObj.put("children", childArr)

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
            var maxTaskId = 0
            val maxNodeId = intArrayOf(0)

            val root = JSONArray(json)
            for (ti in 0 until root.length()) {
                val tObj = root.getJSONObject(ti)
                val taskId = tObj.optInt("id", nextTaskId++)
                if (taskId > maxTaskId) maxTaskId = taskId
                val task = Task(taskId, tObj.optString("title", "Untitled"))

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

                // New format: "children" array of recursive nodes
                val childArr = tObj.optJSONArray("children")
                if (childArr != null) {
                    for (i in 0 until childArr.length()) {
                        task.children.add(nodeFromJson(childArr.getJSONObject(i), maxNodeId))
                    }
                } else {
                    // ── Legacy format migration ────────────────────────────────────────
                    // Old saves used "categories" → subTasks → subItems.
                    // Translate each to the Node tree so existing data is not lost.
                    val cats = tObj.optJSONArray("categories") ?: JSONArray()
                    for (ci in 0 until cats.length()) {
                        val cObj = cats.getJSONObject(ci)
                        val catId = cObj.optInt("id", nextNodeId++)
                        if (catId > maxNodeId[0]) maxNodeId[0] = catId
                        val catNode = Node(catId, cObj.optString("name", "Unnamed"), NodeType.CATEGORY)

                        val subs = cObj.optJSONArray("subTasks") ?: JSONArray()
                        for (si in 0 until subs.length()) {
                            val sObj = subs.getJSONObject(si)
                            val subId = sObj.optInt("id", nextNodeId++)
                            if (subId > maxNodeId[0]) maxNodeId[0] = subId
                            val subNode = Node(
                                subId,
                                sObj.optString("name", "Unnamed"),
                                NodeType.SUBTASK,
                                sObj.optBoolean("completed", false)
                            )
                            val subItemsArr = sObj.optJSONArray("subItems") ?: JSONArray()
                            for (sii in 0 until subItemsArr.length()) {
                                val siObj = subItemsArr.getJSONObject(sii)
                                val siId = siObj.optInt("id", nextNodeId++)
                                if (siId > maxNodeId[0]) maxNodeId[0] = siId
                                subNode.children.add(Node(
                                    siId,
                                    siObj.optString("name", "Unnamed"),
                                    NodeType.SUBITEM,
                                    siObj.optBoolean("completed", false)
                                ))
                            }
                            catNode.children.add(subNode)
                        }
                        task.children.add(catNode)
                    }
                }
                tasks.add(task)
            }

            nextTaskId = maxTaskId + 1
            nextNodeId = maxNodeId[0] + 1

        } catch (e: Exception) {
            tasks.clear()
            savedExpandedCategoryIds.clear()
            savedExpandedSubTaskIds.clear()
        }
    }


    // ─── Screen 1: Task List ──────────────────────────────────────────────────

    private fun renderTaskListScreen() {
        mainLayout.removeAllViews()

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
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }

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
        expandedCategoryIds.clear()
        expandedSubTaskIds.clear()
        val savedCatIds = savedExpandedCategoryIds[task.id]
        val savedSubIds = savedExpandedSubTaskIds[task.id]
        if (savedCatIds != null) {
            expandedCategoryIds.addAll(savedCatIds)
        } else {
            task.children.forEach { expandedCategoryIds.add(it.id) }
        }
        if (savedSubIds != null) {
            expandedSubTaskIds.addAll(savedSubIds)
        } else {
            task.children.forEach { cat ->
                cat.children.forEach { sub ->
                    if (sub.hasChildren()) expandedSubTaskIds.add(sub.id)
                }
            }
        }

        mainLayout.removeAllViews()

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
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
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
                subTask.changeStatus(checked)
                refreshDetail(recyclerView)
                saveData()
            },
            onSubTaskOptions    = { cat, subTask -> showSubTaskOptionsDialog(task, cat, subTask, recyclerView) },
            onAddSubItem        = { subTask -> showAddSubItemDialog(subTask, recyclerView) },
            onSubItemChecked    = { subItem, checked ->
                subItem.changeStatus(checked)
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

        taskOptionsBtn.setOnClickListener {
            if (adapter.isEditMode) {
                AlertDialog.Builder(this)
                    .setTitle(task.title)
                    .setItems(arrayOf("Rename", "Check All", "Uncheck All", "Expand All", "Collapse All", "Backup", "Duplicate", "Delete")) { _, which ->
                        when (which) {
                            0 -> showRenameTaskDialog(task, recyclerView, titleText)
                            1 -> { checkAllNodes(task, true);  saveData(); adapter.refresh() }
                            2 -> { checkAllNodes(task, false); saveData(); adapter.refresh() }
                            3 -> { expandAll(task); saveExpandState(task); adapter.refresh() }
                            4 -> { collapseAll(task); saveExpandState(task); adapter.refresh() }
                            5 -> launchBackupSingle(task)
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
                            0 -> { adapter.setEditMode(true); doneBtn.visibility = android.view.View.VISIBLE }
                            1 -> { checkAllNodes(task, true);  saveData(); adapter.refresh() }
                            2 -> { checkAllNodes(task, false); saveData(); adapter.refresh() }
                            3 -> { expandAll(task); saveExpandState(task); adapter.refresh() }
                            4 -> { collapseAll(task); saveExpandState(task); adapter.refresh() }
                            5 -> launchBackupSingle(task)
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

    private fun saveExpandState(task: Task) {
        savedExpandedCategoryIds[task.id] = expandedCategoryIds.toMutableSet()
        savedExpandedSubTaskIds[task.id]  = expandedSubTaskIds.toMutableSet()
        saveData()
    }


    // ─── Expand / collapse / check helpers ───────────────────────────────────

    /** Recursively set isCompleted on every node in the task tree. */
    private fun checkAllNodes(task: Task, checked: Boolean) {
        fun walkNode(node: Node) {
            node.changeStatus(checked)
            node.children.forEach { walkNode(it) }
        }
        task.children.forEach { walkNode(it) }
    }

    private fun expandAll(task: Task) {
        expandedCategoryIds.clear()
        expandedSubTaskIds.clear()
        task.children.forEach { cat ->
            expandedCategoryIds.add(cat.id)
            cat.children.forEach { sub ->
                if (sub.hasChildren()) expandedSubTaskIds.add(sub.id)
            }
        }
    }

    private fun collapseAll(task: Task) {
        expandedCategoryIds.clear()
        expandedSubTaskIds.clear()
    }

    private fun launchBackupSingle(task: Task) {
        pendingBackupTask = task
        val safeName = task.title
            .replace(Regex("[^a-zA-Z0-9_\\- ]"), "")
            .trim().replace(" ", "_").ifEmpty { "backup" }
        backupSingleTaskLauncher.launch("${safeName}_backup.json")
    }

    // ─── Load helpers ─────────────────────────────────────────────────────────

    private fun loadChecklistFromUri(uri: Uri) {
        val json = try {
            contentResolver.openInputStream(uri)?.use { it.bufferedReader(Charsets.UTF_8).readText() }
                ?: run { Toast.makeText(this, "Could not read file", Toast.LENGTH_LONG).show(); return }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to open file: ${e.message}", Toast.LENGTH_LONG).show(); return
        }

        val root = try { JSONArray(json) } catch (e: Exception) {
            AlertDialog.Builder(this)
                .setTitle("Invalid Backup File")
                .setMessage("The selected file is not a valid backup.")
                .setPositiveButton("OK", null).show()
            return
        }

        if (root.length() == 0) {
            AlertDialog.Builder(this)
                .setTitle("Empty Backup")
                .setMessage("No checklists found in this backup file.")
                .setPositiveButton("OK", null).show()
            return
        }
        showLoadChecklistDialog(root)
    }

    private fun showLoadChecklistDialog(root: JSONArray) {
        val titles = Array(root.length()) { i -> root.getJSONObject(i).optString("title", "Untitled") }
        AlertDialog.Builder(this)
            .setTitle("Load Checklist")
            .setItems(titles) { _, which -> importTaskFromJson(root.getJSONObject(which)) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun importTaskFromJson(tObj: JSONObject) {
        val newTask = Task(nextTaskId++, tObj.optString("title", "Untitled"))
        val maxNodeId = intArrayOf(nextNodeId - 1)

        /** Recursively copy a node, assigning fresh IDs to everything. */
        fun copyNode(src: JSONObject): Node {
            val id = nextNodeId++
            if (id > maxNodeId[0]) maxNodeId[0] = id
            val type = try { NodeType.valueOf(src.optString("type", NodeType.CATEGORY.name)) }
                       catch (e: IllegalArgumentException) { NodeType.CATEGORY }
            val node = Node(id, src.optString("name", "Unnamed"), type,
                src.optBoolean("completed", false))
            val childArr = src.optJSONArray("children") ?: JSONArray()
            for (i in 0 until childArr.length()) node.children.add(copyNode(childArr.getJSONObject(i)))
            return node
        }

        val childArr = tObj.optJSONArray("children")
        if (childArr != null) {
            for (i in 0 until childArr.length()) newTask.children.add(copyNode(childArr.getJSONObject(i)))
        } else {
            // Legacy backup format
            val cats = tObj.optJSONArray("categories") ?: JSONArray()
            for (ci in 0 until cats.length()) {
                val cObj = cats.getJSONObject(ci)
                val catNode = Node(nextNodeId++, cObj.optString("name", "Unnamed"), NodeType.CATEGORY)
                val subs = cObj.optJSONArray("subTasks") ?: JSONArray()
                for (si in 0 until subs.length()) {
                    val sObj = subs.getJSONObject(si)
                    val subNode = Node(nextNodeId++, sObj.optString("name", "Unnamed"),
                        NodeType.SUBTASK, sObj.optBoolean("completed", false))
                    val subItemsArr = sObj.optJSONArray("subItems") ?: JSONArray()
                    for (sii in 0 until subItemsArr.length()) {
                        val siObj = subItemsArr.getJSONObject(sii)
                        subNode.children.add(Node(nextNodeId++, siObj.optString("name", "Unnamed"),
                            NodeType.SUBITEM, siObj.optBoolean("completed", false)))
                    }
                    catNode.children.add(subNode)
                }
                newTask.children.add(catNode)
            }
        }

        tasks.add(newTask)
        saveData()
        renderTaskListScreen()
        Toast.makeText(this, "\"${newTask.title}\" loaded successfully", Toast.LENGTH_SHORT).show()
    }


    // ─── Backup helpers ───────────────────────────────────────────────────────

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
            val childArr = JSONArray()
            for (node in task.children) childArr.put(nodeToJson(node))
            tObj.put("children", childArr)
            root.put(tObj)
        }
        return root.toString(2)
    }

    private fun writeBackupToUri(uri: Uri, json: String) {
        try {
            contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            Toast.makeText(this, "Backup saved successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Backup failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun focusInput(dialog: AlertDialog, input: EditText, selectAll: Boolean = false) {
        input.requestFocus()
        if (selectAll) input.selectAll()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
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
                    val newCat = Node(nextNodeId++, text, NodeType.CATEGORY)
                    task.addCategory(newCat)
                    expandedCategoryIds.add(newCat.id)
                    saveData()
                    refreshDetail(recyclerView)
                }
            }
            .setNegativeButton("Cancel", null).show()
        focusInput(dialog, input)
    }

    private fun showAddSubTaskDialog(task: Task, category: Node, recyclerView: RecyclerView) {
        val input = EditText(this).apply { hint = "Item name" }
        val dialog = AlertDialog.Builder(this)
            .setTitle("New Item")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) {
                    category.addChild(Node(nextNodeId++, text, NodeType.SUBTASK))
                    saveData()
                    refreshDetail(recyclerView)
                }
            }
            .setNegativeButton("Cancel", null).show()
        focusInput(dialog, input)
    }

    private fun showAddSubItemDialog(subTask: Node, recyclerView: RecyclerView) {
        val input = EditText(this).apply { hint = "Subitem name" }
        val dialog = AlertDialog.Builder(this)
            .setTitle("New Subitem")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) {
                    subTask.addChild(Node(nextNodeId++, text, NodeType.SUBITEM))
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

    private fun deleteSubTask(category: Node, subTaskId: Int, recyclerView: RecyclerView) {
        category.removeChild(subTaskId)
        expandedSubTaskIds.remove(subTaskId)
        saveData()
        refreshDetail(recyclerView)
    }

    private fun deleteSubItem(subTask: Node, subItemId: Int, recyclerView: RecyclerView) {
        subTask.removeChild(subItemId)
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
                if (newName.isNotEmpty()) { task.renameTask(newName); saveData(); renderTaskListScreen() }
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
                if (newName.isNotEmpty()) { task.renameTask(newName); titleText.text = task.title; saveData() }
            }
            .setNegativeButton("Cancel", null).show()
        focusInput(dialog, input, selectAll = true)
    }

    private fun showRenameCategoryDialog(task: Task, category: Node, recyclerView: RecyclerView) {
        val input = EditText(this).apply { setText(category.name) }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Rename Category")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) { category.rename(newName); saveData(); refreshDetail(recyclerView) }
            }
            .setNegativeButton("Cancel", null).show()
        focusInput(dialog, input, selectAll = true)
    }

    private fun showRenameSubTaskDialog(task: Task, category: Node, subTask: Node, recyclerView: RecyclerView) {
        val input = EditText(this).apply { setText(subTask.name) }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Rename Item")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) { subTask.rename(newName); saveData(); refreshDetail(recyclerView) }
            }
            .setNegativeButton("Cancel", null).show()
        focusInput(dialog, input, selectAll = true)
    }

    private fun showRenameSubItemDialog(subTask: Node, subItem: Node, recyclerView: RecyclerView) {
        val input = EditText(this).apply { setText(subItem.name) }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Rename Subitem")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) { subItem.rename(newName); saveData(); refreshDetail(recyclerView) }
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
                    1 -> { checkAllNodes(task, true);  saveData(); renderTaskListScreen() }
                    2 -> { checkAllNodes(task, false); saveData(); renderTaskListScreen() }
                    3 -> {
                        val catIds = mutableSetOf<Int>()
                        val subIds = mutableSetOf<Int>()
                        task.children.forEach { cat ->
                            catIds.add(cat.id)
                            cat.children.forEach { sub -> if (sub.hasChildren()) subIds.add(sub.id) }
                        }
                        savedExpandedCategoryIds[task.id] = catIds
                        savedExpandedSubTaskIds[task.id]  = subIds
                        saveData()
                        renderTaskDetailScreen(task)
                    }
                    4 -> {
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

    private fun showCategoryOptionsDialog(task: Task, category: Node, recyclerView: RecyclerView) {
        AlertDialog.Builder(this)
            .setTitle(category.name)
            .setItems(arrayOf("Rename", "Uncheck All Items", "Delete")) { _, which ->
                when (which) {
                    0 -> showRenameCategoryDialog(task, category, recyclerView)
                    1 -> {
                        fun uncheckAll(node: Node) { node.changeStatus(false); node.children.forEach { uncheckAll(it) } }
                        category.children.forEach { uncheckAll(it) }
                        saveData(); refreshDetail(recyclerView)
                    }
                    2 -> AlertDialog.Builder(this)
                            .setTitle("Delete \"${category.name}\"?")
                            .setMessage("This will permanently delete the category and all its items.")
                            .setPositiveButton("Delete") { _, _ -> deleteCategory(task, category.id, recyclerView) }
                            .setNegativeButton("Cancel", null).show()
                }
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showSubTaskOptionsDialog(task: Task, category: Node, subTask: Node, recyclerView: RecyclerView) {
        AlertDialog.Builder(this)
            .setTitle(subTask.name)
            .setItems(arrayOf("Rename", "Delete")) { _, which ->
                when (which) {
                    0 -> showRenameSubTaskDialog(task, category, subTask, recyclerView)
                    1 -> AlertDialog.Builder(this)
                            .setTitle("Delete \"${subTask.name}\"?")
                            .setPositiveButton("Delete") { _, _ -> deleteSubTask(category, subTask.id, recyclerView) }
                            .setNegativeButton("Cancel", null).show()
                }
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showSubItemOptionsDialog(subTask: Node, subItem: Node, recyclerView: RecyclerView) {
        AlertDialog.Builder(this)
            .setTitle(subItem.name)
            .setItems(arrayOf("Rename", "Delete")) { _, which ->
                when (which) {
                    0 -> showRenameSubItemDialog(subTask, subItem, recyclerView)
                    1 -> AlertDialog.Builder(this)
                            .setTitle("Delete \"${subItem.name}\"?")
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

        fun copyNode(src: Node): Node {
            val copy = Node(nextNodeId++, src.name, src.nodeType, src.isCompleted)
            src.children.forEach { copy.children.add(copyNode(it)) }
            return copy
        }

        original.children.forEach { newTask.children.add(copyNode(it)) }
        tasks.add(newTask)
        saveData()
        renderTaskListScreen()
    }
}

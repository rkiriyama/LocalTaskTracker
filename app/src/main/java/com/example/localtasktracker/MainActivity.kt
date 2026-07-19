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

    /** Per-task expand state: task.id → set of expanded node IDs. */
    private val savedExpandedNodeIds = mutableMapOf<Int, MutableSet<Int>>()

    /** Current detail-screen expand state (active while viewing a task). */
    private val expandedNodeIds = mutableSetOf<Int>()

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

    private var nextTaskId = 1
    private var nextNodeId = 1

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
        // Restore expand state
        expandedNodeIds.clear()
        val saved = savedExpandedNodeIds[task.id]
        if (saved != null) {
            expandedNodeIds.addAll(saved)
        } else {
            // Default: expand all nodes that have children
            fun expandAll(node: Node) {
                if (node.hasChildren()) {
                    expandedNodeIds.add(node.id)
                    node.children.forEach { expandAll(it) }
                }
            }
            task.children.forEach { expandAll(it) }
        }

        mainLayout.removeAllViews()

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
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
            task            = task,
            expandedNodeIds = expandedNodeIds,
            onNodeToggle    = { node ->
                if (node.id in expandedNodeIds) expandedNodeIds.remove(node.id)
                else expandedNodeIds.add(node.id)
                saveExpandState(task)
                refreshDetail(recyclerView)
            },
            onNodeChecked   = { node, checked ->
                node.changeStatus(checked)
                refreshDetail(recyclerView)
                saveData()
            },
            onNodeOptions   = { node, parent -> showNodeOptionsDialog(task, node, parent, recyclerView) },
            onAddChild      = { parent -> showAddChildDialog(task, parent, recyclerView) },
            onAddCategory   = { showAddCategoryDialog(task, recyclerView) },
            onDragFinished  = { saveData() }
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
                            3 -> { expandAllNodes(task); saveExpandState(task); adapter.refresh() }
                            4 -> { collapseAllNodes(); saveExpandState(task); adapter.refresh() }
                            5 -> launchBackupSingle(task)
                            6 -> showDuplicateTaskDialog(task)
                            7 -> confirmDeleteTask(task)
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
                            3 -> { expandAllNodes(task); saveExpandState(task); adapter.refresh() }
                            4 -> { collapseAllNodes(); saveExpandState(task); adapter.refresh() }
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
        savedExpandedNodeIds[task.id] = expandedNodeIds.toMutableSet()
        saveData()
    }


    // ─── Expand / collapse / check helpers ───────────────────────────────────

    private fun checkAllNodes(task: Task, checked: Boolean) {
        fun walk(node: Node) { node.changeStatus(checked); node.children.forEach { walk(it) } }
        task.children.forEach { walk(it) }
    }

    private fun expandAllNodes(task: Task) {
        expandedNodeIds.clear()
        fun walk(node: Node) {
            if (node.hasChildren()) { expandedNodeIds.add(node.id); node.children.forEach { walk(it) } }
        }
        task.children.forEach { walk(it) }
    }

    private fun collapseAllNodes() {
        expandedNodeIds.clear()
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
                    expandedNodeIds.add(newCat.id)
                    saveData()
                    refreshDetail(recyclerView)
                }
            }
            .setNegativeButton("Cancel", null).show()
        focusInput(dialog, input)
    }

    private fun showAddChildDialog(task: Task, parent: Node, recyclerView: RecyclerView) {
        val input = EditText(this).apply { hint = "Name" }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Add to \"${parent.name}\"")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) {
                    parent.addChild(Node(nextNodeId++, text, NodeType.NODE))
                    expandedNodeIds.add(parent.id)
                    saveData()
                    refreshDetail(recyclerView)
                }
            }
            .setNegativeButton("Cancel", null).show()
        focusInput(dialog, input)
    }


    // ─── Node options dialog (⋮) ─────────────────────────────────────────────

    private fun showNodeOptionsDialog(task: Task, node: Node, parent: Node?, recyclerView: RecyclerView) {
        AlertDialog.Builder(this)
            .setTitle(node.name)
            .setItems(arrayOf("Rename", "Move to…", "Uncheck All", "Delete")) { _, which ->
                when (which) {
                    0 -> showRenameNodeDialog(node, recyclerView)
                    1 -> showMoveToDialog(task, node, parent, recyclerView)
                    2 -> {
                        fun uncheckAll(n: Node) { n.changeStatus(false); n.children.forEach { uncheckAll(it) } }
                        uncheckAll(node)
                        saveData(); refreshDetail(recyclerView)
                    }
                    3 -> confirmDeleteNode(task, node, parent, recyclerView)
                }
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun confirmDeleteNode(task: Task, node: Node, parent: Node?, recyclerView: RecyclerView) {
        AlertDialog.Builder(this)
            .setTitle("Delete \"${node.name}\"?")
            .setMessage("This will permanently delete this item and all its children.")
            .setPositiveButton("Delete") { _, _ ->
                if (parent == null) {
                    task.deleteCategory(node.id)
                } else {
                    parent.removeChild(node.id)
                }
                expandedNodeIds.remove(node.id)
                saveData()
                refreshDetail(recyclerView)
            }
            .setNegativeButton("Cancel", null).show()
    }


    // ─── Move to… dialog ─────────────────────────────────────────────────────

    private data class MoveDestination(val label: String, val action: () -> Unit)

    private fun collectDescendantIds(node: Node): Set<Int> {
        val ids = mutableSetOf(node.id)
        fun walk(n: Node) { ids.add(n.id); n.children.forEach { walk(it) } }
        node.children.forEach { walk(it) }
        return ids
    }

    /**
     * Expands the entire ancestor chain from root down to [targetId] so the
     * target node is visible after a move. Walks the tree recursively; when
     * [targetId] is found, returns true and each ancestor on the path gets
     * added to [expandedNodeIds].
     */
    private fun expandAncestorChain(task: Task, targetId: Int) {
        fun walkAndExpand(node: Node): Boolean {
            if (node.id == targetId) return true
            for (child in node.children) {
                if (walkAndExpand(child)) {
                    expandedNodeIds.add(node.id)
                    return true
                }
            }
            return false
        }
        task.children.forEach { walkAndExpand(it) }
    }

    /**
     * Shows the "Move to…" destination picker.
     * Lists every node in the tree (excluding self & descendants) as a valid parent.
     * Also offers "Top level (as category)" if not already at root.
     */
    private fun showMoveToDialog(
        task: Task, node: Node, currentParent: Node?, recyclerView: RecyclerView
    ) {
        val destinations = mutableListOf<MoveDestination>()
        val excludedIds = collectDescendantIds(node)

        // Option: promote to top level
        if (currentParent != null) {
            destinations.add(MoveDestination("★ Top level (as category)") {
                currentParent.removeChild(node.id)
                node.nodeType = NodeType.CATEGORY
                task.children.add(node)
                expandedNodeIds.add(node.id)
                saveData(); refreshDetail(recyclerView)
            })
        }

        // Recursively collect all valid parents
        fun walkDestinations(candidate: Node, path: String) {
            if (candidate.id in excludedIds) return

            // Skip adding the current parent as a destination (already there),
            // but still recurse into its children so they appear as options.
            val isCurrentParent = currentParent != null && currentParent.id == candidate.id
            if (!isCurrentParent) {
                destinations.add(MoveDestination("$path${candidate.name}") {
                    // Detach from old parent
                    if (currentParent == null) {
                        task.deleteCategory(node.id)
                    } else {
                        currentParent.removeChild(node.id)
                    }
                    node.nodeType = NodeType.NODE
                    candidate.children.add(node)
                    // Expand the entire ancestor chain so the moved node is visible
                    expandAncestorChain(task, candidate.id)
                    expandedNodeIds.add(candidate.id)
                    saveData(); refreshDetail(recyclerView)
                })
            }

            // Always recurse into children (even if candidate is current parent)
            for (child in candidate.children) {
                if (child.id !in excludedIds) {
                    walkDestinations(child, "$path${candidate.name} → ")
                }
            }
        }

        for (cat in task.children) {
            walkDestinations(cat, "")
        }

        if (destinations.isEmpty()) {
            Toast.makeText(this, "No valid destinations available", Toast.LENGTH_SHORT).show()
            return
        }

        val labels = destinations.map { it.label }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Move \"${node.name}\" to…")
            .setItems(labels) { _, which -> destinations[which].action() }
            .setNegativeButton("Cancel", null)
            .show()
    }


    // ─── Task options dialog (Screen 1 ⋮) ────────────────────────────────────

    private fun showTaskOptionsDialog(task: Task) {
        AlertDialog.Builder(this)
            .setTitle(task.title)
            .setItems(arrayOf("Rename", "Check All", "Uncheck All", "Expand All", "Collapse All", "Duplicate", "Delete")) { _, which ->
                when (which) {
                    0 -> showRenameTaskDialog(task)
                    1 -> { checkAllNodes(task, true);  saveData(); renderTaskListScreen() }
                    2 -> { checkAllNodes(task, false); saveData(); renderTaskListScreen() }
                    3 -> {
                        expandAllNodes(task)
                        savedExpandedNodeIds[task.id] = expandedNodeIds.toMutableSet()
                        saveData(); renderTaskDetailScreen(task)
                    }
                    4 -> {
                        collapseAllNodes()
                        savedExpandedNodeIds[task.id] = mutableSetOf()
                        saveData(); renderTaskDetailScreen(task)
                    }
                    5 -> showDuplicateTaskDialog(task)
                    6 -> confirmDeleteTask(task)
                }
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun confirmDeleteTask(task: Task) {
        AlertDialog.Builder(this)
            .setTitle("Delete \"${task.title}\"?")
            .setMessage("This will permanently delete the checklist and all its contents.")
            .setPositiveButton("Delete") { _, _ -> deleteTask(task.id) }
            .setNegativeButton("Cancel", null).show()
    }

    // ─── Delete ───────────────────────────────────────────────────────────────

    private fun deleteTask(taskId: Int) {
        tasks.removeAll { it.id == taskId }
        savedExpandedNodeIds.remove(taskId)
        saveData()
        renderTaskListScreen()
    }


    // ─── Rename dialogs ───────────────────────────────────────────────────────

    private fun showRenameTaskDialog(task: Task) {
        val input = EditText(this).apply { setText(task.title) }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Rename Checklist")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val n = input.text.toString().trim()
                if (n.isNotEmpty()) { task.renameTask(n); saveData(); renderTaskListScreen() }
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
                val n = input.text.toString().trim()
                if (n.isNotEmpty()) { task.renameTask(n); titleText.text = task.title; saveData() }
            }
            .setNegativeButton("Cancel", null).show()
        focusInput(dialog, input, selectAll = true)
    }

    private fun showRenameNodeDialog(node: Node, recyclerView: RecyclerView) {
        val input = EditText(this).apply { setText(node.name) }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Rename")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val n = input.text.toString().trim()
                if (n.isNotEmpty()) { node.rename(n); saveData(); refreshDetail(recyclerView) }
            }
            .setNegativeButton("Cancel", null).show()
        focusInput(dialog, input, selectAll = true)
    }


    // ─── Duplicate ────────────────────────────────────────────────────────────

    private fun showDuplicateTaskDialog(task: Task) {
        val input = EditText(this).apply { setText("${task.title} (copy)") }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Duplicate Checklist")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val n = input.text.toString().trim()
                if (n.isNotEmpty()) duplicateTask(task, n)
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

    // ─── Backup / Load ────────────────────────────────────────────────────────

    private fun launchBackupSingle(task: Task) {
        pendingBackupTask = task
        val safeName = task.title
            .replace(Regex("[^a-zA-Z0-9_\\- ]"), "")
            .trim().replace(" ", "_").ifEmpty { "backup" }
        backupSingleTaskLauncher.launch("${safeName}_backup.json")
    }

    private fun serializeTasksToJson(taskList: List<Task>): String {
        val root = JSONArray()
        for (task in taskList) {
            val tObj = JSONObject()
            tObj.put("id", task.id)
            tObj.put("title", task.title)
            val expArr = JSONArray()
            savedExpandedNodeIds[task.id]?.forEach { expArr.put(it) }
            tObj.put("expandedNodeIds", expArr)
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

    private fun loadChecklistFromUri(uri: Uri) {
        val json = try {
            contentResolver.openInputStream(uri)?.use { it.bufferedReader(Charsets.UTF_8).readText() }
                ?: run { Toast.makeText(this, "Could not read file", Toast.LENGTH_LONG).show(); return }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to open file: ${e.message}", Toast.LENGTH_LONG).show(); return
        }
        val root = try { JSONArray(json) } catch (_: Exception) {
            AlertDialog.Builder(this).setTitle("Invalid Backup File")
                .setMessage("The selected file is not a valid backup.")
                .setPositiveButton("OK", null).show(); return
        }
        if (root.length() == 0) {
            AlertDialog.Builder(this).setTitle("Empty Backup")
                .setMessage("No checklists found in this backup file.")
                .setPositiveButton("OK", null).show(); return
        }
        showLoadChecklistDialog(root)
    }

    private fun showLoadChecklistDialog(root: JSONArray) {
        val titles = Array(root.length()) { i -> root.getJSONObject(i).optString("title", "Untitled") }
        AlertDialog.Builder(this)
            .setTitle("Load Checklist")
            .setItems(titles) { _, which -> importTaskFromJson(root.getJSONObject(which)) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun importTaskFromJson(tObj: JSONObject) {
        val newTask = Task(nextTaskId++, tObj.optString("title", "Untitled"))
        val maxId = intArrayOf(nextNodeId - 1)

        fun copyNode(src: JSONObject): Node {
            val id = nextNodeId++
            if (id > maxId[0]) maxId[0] = id
            val type = mapLegacyType(src.optString("type", NodeType.CATEGORY.name))
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
            // Legacy format: categories → subTasks → subItems
            val cats = tObj.optJSONArray("categories") ?: JSONArray()
            for (ci in 0 until cats.length()) {
                val cObj = cats.getJSONObject(ci)
                val catNode = Node(nextNodeId++, cObj.optString("name", "Unnamed"), NodeType.CATEGORY)
                val subs = cObj.optJSONArray("subTasks") ?: JSONArray()
                for (si in 0 until subs.length()) {
                    val sObj = subs.getJSONObject(si)
                    val subNode = Node(nextNodeId++, sObj.optString("name", "Unnamed"),
                        NodeType.NODE, sObj.optBoolean("completed", false))
                    val items = sObj.optJSONArray("subItems") ?: JSONArray()
                    for (ii in 0 until items.length()) {
                        val iObj = items.getJSONObject(ii)
                        subNode.children.add(Node(nextNodeId++, iObj.optString("name", "Unnamed"),
                            NodeType.NODE, iObj.optBoolean("completed", false)))
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


    // ─── Persistence ──────────────────────────────────────────────────────────

    private fun nodeToJson(node: Node): JSONObject {
        val obj = JSONObject()
        obj.put("id", node.id)
        obj.put("name", node.name)
        obj.put("type", node.nodeType.name)
        obj.put("completed", node.isCompleted)
        val childArr = JSONArray()
        for (child in node.children) childArr.put(nodeToJson(child))
        obj.put("children", childArr)
        return obj
    }

    private fun nodeFromJson(obj: JSONObject, maxId: IntArray): Node {
        val id = obj.optInt("id", nextNodeId++)
        val name = obj.optString("name", "Unnamed")
        val type = mapLegacyType(obj.optString("type", NodeType.CATEGORY.name))
        val completed = obj.optBoolean("completed", false)
        if (id > maxId[0]) maxId[0] = id
        val node = Node(id, name, type, completed)
        val childArr = obj.optJSONArray("children") ?: JSONArray()
        for (i in 0 until childArr.length()) {
            node.children.add(nodeFromJson(childArr.getJSONObject(i), maxId))
        }
        return node
    }

    /** Maps old SUBTASK/SUBITEM names to NODE for legacy data migration. */
    private fun mapLegacyType(typeName: String): NodeType {
        return when (typeName) {
            "CATEGORY" -> NodeType.CATEGORY
            "SUBTASK", "SUBITEM", "NODE" -> NodeType.NODE
            else -> NodeType.NODE
        }
    }

    private fun saveData() {
        val root = JSONArray()
        for (task in tasks) {
            val tObj = JSONObject()
            tObj.put("id", task.id)
            tObj.put("title", task.title)
            val expArr = JSONArray()
            savedExpandedNodeIds[task.id]?.forEach { expArr.put(it) }
            tObj.put("expandedNodeIds", expArr)
            val childArr = JSONArray()
            for (node in task.children) childArr.put(nodeToJson(node))
            tObj.put("children", childArr)
            root.put(tObj)
        }
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_DATA, root.toString()).apply()
    }

    private fun loadData() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_DATA, null) ?: return
        try {
            tasks.clear()
            savedExpandedNodeIds.clear()
            var maxTaskId = 0
            val maxNodeId = intArrayOf(0)

            val root = JSONArray(json)
            for (ti in 0 until root.length()) {
                val tObj = root.getJSONObject(ti)
                val taskId = tObj.optInt("id", nextTaskId++)
                if (taskId > maxTaskId) maxTaskId = taskId
                val task = Task(taskId, tObj.optString("title", "Untitled"))

                // Expand state — new format: single "expandedNodeIds" array
                val expArr = tObj.optJSONArray("expandedNodeIds")
                if (expArr != null) {
                    val ids = mutableSetOf<Int>()
                    for (i in 0 until expArr.length()) ids.add(expArr.getInt(i))
                    savedExpandedNodeIds[task.id] = ids
                } else {
                    // Legacy: merge old expandedCategoryIds + expandedSubTaskIds
                    val ids = mutableSetOf<Int>()
                    val catArr = tObj.optJSONArray("expandedCategoryIds")
                    if (catArr != null) for (i in 0 until catArr.length()) ids.add(catArr.getInt(i))
                    val subArr = tObj.optJSONArray("expandedSubTaskIds")
                    if (subArr != null) for (i in 0 until subArr.length()) ids.add(subArr.getInt(i))
                    if (ids.isNotEmpty()) savedExpandedNodeIds[task.id] = ids
                }

                // Children — new format
                val childArr = tObj.optJSONArray("children")
                if (childArr != null) {
                    for (i in 0 until childArr.length()) {
                        task.children.add(nodeFromJson(childArr.getJSONObject(i), maxNodeId))
                    }
                } else {
                    // Legacy: categories → subTasks → subItems
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
                            val subNode = Node(subId, sObj.optString("name", "Unnamed"),
                                NodeType.NODE, sObj.optBoolean("completed", false))
                            val items = sObj.optJSONArray("subItems") ?: JSONArray()
                            for (ii in 0 until items.length()) {
                                val iObj = items.getJSONObject(ii)
                                val siId = iObj.optInt("id", nextNodeId++)
                                if (siId > maxNodeId[0]) maxNodeId[0] = siId
                                subNode.children.add(Node(siId, iObj.optString("name", "Unnamed"),
                                    NodeType.NODE, iObj.optBoolean("completed", false)))
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
        } catch (_: Exception) {
            tasks.clear()
            savedExpandedNodeIds.clear()
        }
    }

    private fun focusInput(dialog: AlertDialog, input: EditText, selectAll: Boolean = false) {
        input.requestFocus()
        if (selectAll) input.selectAll()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    }
}

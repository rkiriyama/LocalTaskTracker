package com.example.localtasktracker

data class Task(
    val id: Int,
    var title: String,
    var isCompleted: Boolean = false,
    var completionProgress: Int = 0,
    /** Top-level nodes — these should all have nodeType == CATEGORY. */
    val children: MutableList<Node> = mutableListOf()
) {
    // ── Title ─────────────────────────────────────────────────────────────────

    fun renameTask(newTaskName: String): Boolean {
        val trimmed = newTaskName.trim()
        if (trimmed.isEmpty()) return false
        title = trimmed
        return true
    }

    // ── Status ────────────────────────────────────────────────────────────────

    fun changeTaskStatus(status: Boolean): Boolean {
        val currentProgress = computeProgress()
        if ((status && currentProgress != 100) || (!status && currentProgress == 100)) return false
        isCompleted = status
        return true
    }

    // ── Progress ──────────────────────────────────────────────────────────────

    fun computeProgress(): Int {
        if (children.isEmpty()) return 0
        return children.map { it.computeProgress() }.average().toInt()
    }

    // ── Category (top-level Node) CRUD ────────────────────────────────────────

    fun addCategory(node: Node): Boolean {
        if (node.name.trim().isEmpty()) return false
        children.add(node)
        return true
    }

    fun deleteCategory(categoryId: Int): Boolean {
        if (children.isEmpty()) return false
        return children.removeAll { it.id == categoryId }
    }

    fun findCategory(categoryId: Int): Node? = children.firstOrNull { it.id == categoryId }
}

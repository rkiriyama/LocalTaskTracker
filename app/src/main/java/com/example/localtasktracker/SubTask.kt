package com.example.localtasktracker

import kotlin.math.roundToInt

data class SubTask(
    val id: Int,
    var subTaskName: String,
    var isCompleted: Boolean = false,
    val subItems: MutableList<SubItem> = mutableListOf()
) {
    fun renameSubTask(newTaskName: String): Boolean {
        val trimmedTitle = newTaskName.trim()
        if (trimmedTitle.isEmpty()) return false
        subTaskName = trimmedTitle
        return true
    }

    fun changeSubTaskStatus(status: Boolean) {
        isCompleted = status
    }

    // ── SubItem helpers ───────────────────────────────────────────────────────

    /** True when this subtask has at least one subitem — acts as a mini-category. */
    fun hasSubItems(): Boolean = subItems.isNotEmpty()

    fun addSubItem(subItem: SubItem): Boolean {
        if (subItem.subItemName.trim().isEmpty()) return false
        subItems.add(subItem)
        return true
    }

    fun deleteSubItem(subItemId: Int): Boolean {
        if (subItems.isEmpty()) return false
        return subItems.removeAll { it.id == subItemId }
    }

    /**
     * When this subtask has subitems, progress is the percentage of completed
     * subitems (0–100). Returns 0 when there are no subitems (plain checkbox mode).
     */
    fun computeProgress(): Int {
        if (subItems.isEmpty()) return 0
        val completed = subItems.count { it.isCompleted }
        return (completed.toDouble() / subItems.size * 100).roundToInt()
    }
}

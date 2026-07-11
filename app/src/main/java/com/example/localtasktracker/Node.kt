package com.example.localtasktracker

import kotlin.math.roundToInt

/**
 * Unified recursive tree node that replaces [TaskCategory], [SubTask], and [SubItem].
 *
 * [nodeType] controls how a node is rendered and what roles it plays in the UI:
 *   - CATEGORY  : top-level group header (was TaskCategory)
 *   - SUBTASK   : item under a category; acts as a mini-category when it has children (was SubTask)
 *   - SUBITEM   : leaf checkbox item (was SubItem)
 *
 * Any node can hold children of any type — the type only drives rendering.
 * When a SUBTASK node has children it shows a progress ring; when it has no
 * children it shows a plain checkbox.  SUBITEM nodes are always leaf-level in the
 * current UI but may hold children after a "promote" operation.
 */
enum class NodeType { CATEGORY, SUBTASK, SUBITEM }

data class Node(
    val id: Int,
    var name: String,
    var nodeType: NodeType,
    var isCompleted: Boolean = false,
    val children: MutableList<Node> = mutableListOf()
) {
    // ── Name ──────────────────────────────────────────────────────────────────

    fun rename(newName: String): Boolean {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return false
        name = trimmed
        return true
    }

    // ── Completion ────────────────────────────────────────────────────────────

    fun changeStatus(status: Boolean) {
        isCompleted = status
    }

    // ── Children helpers ──────────────────────────────────────────────────────

    fun hasChildren(): Boolean = children.isNotEmpty()

    fun addChild(node: Node): Boolean {
        if (node.name.trim().isEmpty()) return false
        children.add(node)
        return true
    }

    fun removeChild(childId: Int): Boolean {
        if (children.isEmpty()) return false
        return children.removeAll { it.id == childId }
    }

    fun findChild(childId: Int): Node? = children.firstOrNull { it.id == childId }

    // ── Progress ──────────────────────────────────────────────────────────────

    /**
     * Recursive progress computation (0–100):
     *   - SUBITEM  / leaf node (no children): 0 or 100 based on [isCompleted].
     *   - Node with children: average of each child's [computeProgress].
     *
     * This mirrors the old per-class logic:
     *   • old TaskCategory.computeProgress   → CATEGORY with children
     *   • old SubTask.computeProgress        → SUBTASK with children
     *   • plain SubTask (no subitems)        → SUBTASK with no children
     *   • SubItem                            → SUBITEM (leaf)
     */
    fun computeProgress(): Int {
        if (children.isEmpty()) return if (isCompleted) 100 else 0
        return children.map { it.computeProgress() }.average().roundToInt()
    }

    /**
     * Count of direct children whose [isCompleted] is true.
     * Equivalent to old TaskCategory.getTasksCompleted().
     */
    fun completedChildCount(): Int = children.count { it.isCompleted }
}

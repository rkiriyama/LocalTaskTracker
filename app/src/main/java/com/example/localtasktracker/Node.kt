package com.example.localtasktracker

import kotlin.math.roundToInt

/**
 * Unified recursive tree node.
 *
 * [nodeType] controls top-level vs. nested rendering:
 *   - CATEGORY : depth-0 node (always bold, always shows progress ring)
 *   - NODE     : any nested node at depth ≥ 1; rendering is determined at
 *                runtime by whether it has children:
 *                  • has children → bold collapsible mini-category with progress ring
 *                  • no children  → checkable checkbox
 *
 * Any node can hold children of any type — there is no depth limit.
 */
enum class NodeType { CATEGORY, NODE }

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
     *   - Leaf node (no children): 0 or 100 based on [isCompleted].
     *   - Node with children: average of each child's [computeProgress].
     */
    fun computeProgress(): Int {
        if (children.isEmpty()) return if (isCompleted) 100 else 0
        return children.map { it.computeProgress() }.average().roundToInt()
    }

    /**
     * Count of direct children whose [isCompleted] is true.
     */
    fun completedChildCount(): Int = children.count { it.isCompleted }
}

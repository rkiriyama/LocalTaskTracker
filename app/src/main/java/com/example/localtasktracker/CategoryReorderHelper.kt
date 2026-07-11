package com.example.localtasktracker

/**
 * Pure-logic helper for category drag-and-drop reordering.
 *
 * Operates on [task].children (top-level [Node] list) instead of the old
 * TaskCategory list.  Logic is identical to before:
 *   - [moveCategory] does a removeAt + add at the new position (not a swap).
 *   - No Android imports — directly unit-testable on the JVM.
 */
class CategoryReorderHelper(private val task: Task) {

    /** Returns true iff dragging is meaningful (requires at least 2 top-level nodes). */
    fun canDragCategory(): Boolean = task.children.size >= 2

    /**
     * Moves the category-node currently at [from] to [to], shifting everything
     * in between — identical to what TaskAdapter does with its task list.
     *
     * Both indices must be valid positions in [task].children.
     */
    fun moveCategory(from: Int, to: Int) {
        if (from == to) return
        require(from in task.children.indices) {
            "from=$from out of bounds (size=${task.children.size})"
        }
        require(to in task.children.indices) {
            "to=$to out of bounds (size=${task.children.size})"
        }
        val node = task.children.removeAt(from)
        task.children.add(to, node)
    }
}

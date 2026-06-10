package com.example.localtasktracker

/**
 * Pure-logic helper for category drag-and-drop reordering.
 *
 * Mirrors exactly how TaskAdapter manages its task list:
 *   - [moveCategory] does a removeAt + add at the new position (not a swap).
 *   - [task].categories is kept in sync after every call.
 *   - No Android imports — directly unit-testable on the JVM.
 */
class CategoryReorderHelper(private val task: Task) {

    /** Returns true iff dragging is meaningful (requires at least 2 categories). */
    fun canDragCategory(): Boolean = task.categories.size >= 2

    /**
     * Moves the category currently at [from] to [to], shifting everything
     * in between — identical to what TaskAdapter does with its task list.
     *
     * Both indices must be valid positions in [task].categories.
     */
    fun moveCategory(from: Int, to: Int) {
        if (from == to) return
        require(from in task.categories.indices) { "from=$from out of bounds (size=${task.categories.size})" }
        require(to   in task.categories.indices) { "to=$to out of bounds (size=${task.categories.size})" }
        val cat = task.categories.removeAt(from)
        task.categories.add(to, cat)
    }
}

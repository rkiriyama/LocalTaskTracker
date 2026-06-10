package com.example.localtasktracker

/**
 * Pure-logic helper that owns all category-drag state and operations.
 *
 * Keeping this class free of Android imports makes it directly unit-testable
 * on the JVM without Robolectric or any test-double framework.
 *
 * ── Contract ──────────────────────────────────────────────────────────────────
 *
 *  1. Call [canDragCategory] before starting a drag to check eligibility.
 *  2. On the very first drag event, call [beginCategoryDrag] to collapse the
 *     task's categories into a single-row-per-category shadow list.
 *  3. For every subsequent ItemTouchHelper move event, call [swapCategories]
 *     with the two positions in the shadow list.
 *  4. When the drag gesture ends, call [commitCategoryDrag]:
 *       - [task].categories is already up-to-date (kept in sync by [swapCategories]).
 *       - All subtasks remain inside their original category objects untouched.
 *       - The helper resets its internal state, ready for the next drag.
 *  5. Call [cancelCategoryDrag] if the drag is abandoned without any move.
 *
 * The [shadowList] is a plain list of [TaskCategory] references in display order.
 * It is only populated during an active drag; it is empty otherwise.
 * ─────────────────────────────────────────────────────────────────────────────
 */
class CategoryReorderHelper(private val task: Task) {

    /** Whether a category drag is currently in progress. */
    var isDragging: Boolean = false
        private set

    /**
     * Shadow list used during a drag: one entry per category, mirrors
     * [task].categories in display order. Cleared when the drag ends.
     */
    val shadowList: MutableList<TaskCategory> = mutableListOf()

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns true iff a category drag is permitted right now.
     * Dragging is only useful (and safe) when there are at least 2 categories.
     */
    fun canDragCategory(): Boolean = task.categories.size >= 2

    /**
     * Initialises a drag gesture. Populates [shadowList] from the current
     * [task].categories order and sets [isDragging] = true.
     *
     * Safe to call even if [isDragging] is already true (idempotent).
     */
    fun beginCategoryDrag() {
        if (isDragging) return
        isDragging = true
        shadowList.clear()
        shadowList.addAll(task.categories)
    }

    /**
     * Swaps the categories at positions [a] and [b] in both [shadowList] and
     * [task].categories. Both indices must be valid positions in [shadowList].
     *
     * @throws IllegalStateException if called before [beginCategoryDrag].
     * @throws IndexOutOfBoundsException if [a] or [b] are out of range.
     */
    fun swapCategories(a: Int, b: Int) {
        check(isDragging) { "swapCategories called outside of an active drag" }
        require(a in shadowList.indices) { "Index a=$a out of bounds (size=${shadowList.size})" }
        require(b in shadowList.indices) { "Index b=$b out of bounds (size=${shadowList.size})" }
        if (a == b) return

        // Swap in shadow list
        val tmp = shadowList[a]
        shadowList[a] = shadowList[b]
        shadowList[b] = tmp

        // Keep task.categories in sync so commitCategoryDrag needs no extra work
        task.categories[a] = shadowList[a]
        task.categories[b] = shadowList[b]
    }

    /**
     * Finalises the drag. [task].categories already reflects the new order
     * (kept in sync by [swapCategories]). Resets internal state.
     *
     * All subtasks remain inside their original [TaskCategory] objects — nothing
     * is moved between categories by this operation.
     */
    fun commitCategoryDrag() {
        isDragging = false
        shadowList.clear()
    }

    /**
     * Abandons a drag without committing — restores [task].categories to the
     * order that was snapshotted in [beginCategoryDrag].
     */
    fun cancelCategoryDrag() {
        if (!isDragging) return
        task.categories.clear()
        task.categories.addAll(shadowList)
        isDragging = false
        shadowList.clear()
    }
}

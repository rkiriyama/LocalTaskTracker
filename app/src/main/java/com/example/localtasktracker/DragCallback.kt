package com.example.localtasktracker

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

/**
 * Shared ItemTouchHelper.Callback that enables long-press drag-and-drop reordering.
 * Swiping is disabled. The host adapter implements [DragHost] to receive move events.
 */
class DragCallback(private val host: DragHost) : ItemTouchHelper.Callback() {

    interface DragHost {
        /** Called continuously while the user drags item from [from] toward [to]. */
        fun onItemMoved(from: Int, to: Int)
        /** Called once when the drag gesture is fully complete (finger lifted). */
        fun onDragFinished()
        /** Return false for rows that must not be draggable (e.g. "Add" buttons). */
        fun canDrag(position: Int): Boolean
        /** Return false for rows that must not act as drop targets. */
        fun canDrop(position: Int): Boolean
    }

    /** True once at least one successful onMove has occurred in this drag gesture. */
    private var moved = false

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        if (!host.canDrag(viewHolder.bindingAdapterPosition)) return 0
        return makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        dragged: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        val from = dragged.bindingAdapterPosition
        val to   = target.bindingAdapterPosition

        // Guard: positions can be NO_POSITION (-1) while animations are in flight,
        // or stale beyond the current item count after a rapid sequence of moves.
        val itemCount = recyclerView.adapter?.itemCount ?: return false
        if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
        if (from < 0 || from >= itemCount || to < 0 || to >= itemCount) return false

        if (!host.canDrop(to)) return false
        host.onItemMoved(from, to)
        moved = true
        return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        // Swipe disabled — nothing to do.
    }

    override fun isLongPressDragEnabled(): Boolean = true
    override fun isItemViewSwipeEnabled(): Boolean = false

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        // Only commit the drag if at least one move occurred; a cancelled drag
        // (finger returned to origin) must not corrupt the model or trigger a save.
        if (moved) {
            moved = false
            host.onDragFinished()
        }
    }
}

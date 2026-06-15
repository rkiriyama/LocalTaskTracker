package com.example.localtasktracker

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

/**
 * Shared ItemTouchHelper.Callback that enables long-press drag-and-drop reordering.
 * Swiping is disabled. The host adapter implements [DragHost] to receive move events.
 */
class DragCallback(private val host: DragHost) : ItemTouchHelper.Callback() {

    interface DragHost {
        /**
         * Called once when a long-press is detected and a drag is about to begin,
         * BEFORE any [onItemMoved] calls. The adapter can use this to prepare the
         * list (e.g. collapse child rows) while ItemTouchHelper has not yet latched
         * onto a ViewHolder — so incremental notify calls here are safe.
         */
        fun onDragStarting(position: Int)
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

    /** The position being long-pressed, or NO_POSITION when idle. */
    private var pendingDragPosition = RecyclerView.NO_POSITION

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        val position = viewHolder.bindingAdapterPosition
        if (position == RecyclerView.NO_POSITION) return 0
        if (!host.canDrag(position)) return 0
        return makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
    }

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        super.onSelectedChanged(viewHolder, actionState)
        // onSelectedChanged with ACTION_STATE_DRAG fires exactly once when the
        // long-press threshold is crossed and ItemTouchHelper takes ownership of
        // the ViewHolder. This is the correct and only place to call onDragStarting.
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
            val position = viewHolder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                pendingDragPosition = position
                host.onDragStarting(position)
            }
        } else if (actionState == ItemTouchHelper.ACTION_STATE_IDLE) {
            pendingDragPosition = RecyclerView.NO_POSITION
        }
    }

    override fun onMove(
        recyclerView: RecyclerView,
        dragged: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        val from = dragged.bindingAdapterPosition
        val to   = target.bindingAdapterPosition

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
        if (moved) {
            moved = false
            host.onDragFinished()
        }
    }
}

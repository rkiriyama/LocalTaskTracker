package com.example.localtasktracker

import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * RecyclerView adapter for Screen 2 — the flat mixed-type detail list.
 *
 * View types:
 *   TYPE_CATEGORY     — category header row (draggable; see category-drag notes below)
 *   TYPE_SUBTASK      — subtask row (draggable within its category; droppable into another)
 *   TYPE_ADD_ITEM     — "+ Add Item" button (not draggable)
 *   TYPE_ADD_CATEGORY — "+ Add Category" button (not draggable)
 *
 * ── Category drag design ──────────────────────────────────────────────────────
 * ItemTouchHelper fires onItemMoved one row at a time, so dragging a multi-row
 * block (header + subtasks + add-button) would require complex position arithmetic
 * and fighting the framework.
 *
 * Instead we mirror exactly what TaskAdapter does for its single-row tasks:
 *   1. On the FIRST move of a category drag, collapse ALL categories to header-only
 *      rows so the list becomes N single-row items — identical to TaskAdapter.
 *   2. Each subsequent onItemMoved is a plain single-row swap (same as TaskAdapter).
 *   3. On drag-finish, rebuild task.categories from the header order, re-expand
 *      whatever was expanded before, and call refresh().
 *
 * This guarantees: no items are lost, no items change category, and the
 * category simply swaps position with whichever category the user dragged it onto.
 * ─────────────────────────────────────────────────────────────────────────────
 */
class DetailAdapter(
    private val task: Task,
    private val expandedCategoryIds: MutableSet<Int>,
    private val onCategoryToggle: (TaskCategory) -> Unit,
    private val onCategoryOptions: (TaskCategory) -> Unit,
    private val onSubTaskChecked: (SubTask, Boolean) -> Unit,
    private val onSubTaskOptions: (TaskCategory, SubTask) -> Unit,
    private val onAddCategory: () -> Unit,
    private val onAddItem: (TaskCategory) -> Unit,
    private val onDragFinished: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>(), DragCallback.DragHost {

    companion object {
        const val TYPE_CATEGORY     = 0
        const val TYPE_SUBTASK      = 1
        const val TYPE_ADD_ITEM     = 2
        const val TYPE_ADD_CATEGORY = 3
    }

    // ─── Flat item model ──────────────────────────────────────────────────────

    sealed class DetailItem {
        data class CategoryItem(val category: TaskCategory) : DetailItem()
        data class SubTaskItem(val category: TaskCategory, val subTask: SubTask) : DetailItem()
        data class AddItemButton(val category: TaskCategory) : DetailItem()
        object AddCategoryButton : DetailItem()
    }

    internal val items = mutableListOf<DetailItem>()

    /** True while a programmatic checkbox change is in flight, to avoid re-entrancy. */
    private var isBindingCheckbox = false

    /** Owns all category-drag state and pure logic (unit-testable without Android). */
    internal val reorderHelper = CategoryReorderHelper(task)

    fun refresh() {
        items.clear()
        for (category in task.categories) {
            items.add(DetailItem.CategoryItem(category))
            if (category.id in expandedCategoryIds) {
                for (subTask in category.subTasks) {
                    items.add(DetailItem.SubTaskItem(category, subTask))
                }
                items.add(DetailItem.AddItemButton(category))
            }
        }
        items.add(DetailItem.AddCategoryButton)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is DetailItem.CategoryItem      -> TYPE_CATEGORY
        is DetailItem.SubTaskItem       -> TYPE_SUBTASK
        is DetailItem.AddItemButton     -> TYPE_ADD_ITEM
        is DetailItem.AddCategoryButton -> TYPE_ADD_CATEGORY
    }

    // ─── ViewHolders ──────────────────────────────────────────────────────────

    inner class CategoryViewHolder(val row: LinearLayout) : RecyclerView.ViewHolder(row) {
        // order: arrow | badgeFrame | nameText | optionsBtn
        val arrow:      TextView    = row.getChildAt(0) as TextView
        val badgeFrame: FrameLayout = row.getChildAt(1) as FrameLayout
        val nameText:   TextView    = row.getChildAt(2) as TextView
        val optionsBtn: Button      = row.getChildAt(3) as Button
        val ringView:   RingView    = badgeFrame.getChildAt(0) as RingView
        val pctText:    TextView    = badgeFrame.getChildAt(1) as TextView
    }

    inner class SubTaskViewHolder(val row: LinearLayout) : RecyclerView.ViewHolder(row) {
        val checkBox:   CheckBox = row.getChildAt(0) as CheckBox
        val nameText:   TextView = row.getChildAt(1) as TextView
        val optionsBtn: Button   = row.getChildAt(2) as Button
    }

    inner class AddItemViewHolder(val row: LinearLayout) : RecyclerView.ViewHolder(row) {
        val addBtn: Button = row.getChildAt(0) as Button
    }

    inner class AddCategoryViewHolder(val row: LinearLayout) : RecyclerView.ViewHolder(row) {
        val addBtn: Button = row.getChildAt(0) as Button
    }

    // ─── Create views ─────────────────────────────────────────────────────────

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val ctx = parent.context
        val matchWrap = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        return when (viewType) {
            TYPE_CATEGORY -> {
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(0, 16, 0, 16)
                    layoutParams = matchWrap
                }
                val arrow = TextView(ctx).apply {
                    textSize = 18f
                    setPadding(0, 0, 16, 0)
                }
                val badgeFrame = makeBadgeFrame(ctx, 44)
                val nameText = TextView(ctx).apply {
                    textSize = 18f
                    setPadding(12, 0, 0, 0)
                    layoutParams = LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                    )
                }
                val optionsBtn = Button(ctx).apply { text = "⋮" }
                row.addView(arrow)
                row.addView(badgeFrame)
                row.addView(nameText)
                row.addView(optionsBtn)
                CategoryViewHolder(row)
            }
            TYPE_SUBTASK -> {
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(60, 8, 0, 8)
                    layoutParams = matchWrap
                }
                val checkBox = CheckBox(ctx)
                val nameText = TextView(ctx).apply {
                    textSize = 16f
                    layoutParams = LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                    )
                }
                val optionsBtn = Button(ctx).apply { text = "⋮" }
                row.addView(checkBox)
                row.addView(nameText)
                row.addView(optionsBtn)
                SubTaskViewHolder(row)
            }
            TYPE_ADD_ITEM -> {
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(80, 8, 0, 16)
                    layoutParams = matchWrap
                }
                val addBtn = Button(ctx).apply { text = "+ Add Item" }
                row.addView(addBtn)
                AddItemViewHolder(row)
            }
            else -> {
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, 8, 0, 16)
                    layoutParams = matchWrap
                }
                val addBtn = Button(ctx).apply { text = "+ Add Category" }
                row.addView(addBtn)
                AddCategoryViewHolder(row)
            }
        }
    }

    // ─── Bind data ────────────────────────────────────────────────────────────

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is DetailItem.CategoryItem -> {
                val vh = holder as CategoryViewHolder
                val cat = item.category
                val isExpanded = cat.id in expandedCategoryIds
                vh.arrow.text = if (isExpanded) "▼" else "▶"
                vh.nameText.text = cat.categoryName
                vh.row.setBackgroundColor(Color.TRANSPARENT)
                val toggle = { _: android.view.View -> onCategoryToggle(cat) }
                vh.arrow.setOnClickListener(toggle)
                vh.nameText.setOnClickListener(toggle)
                vh.optionsBtn.setOnClickListener { onCategoryOptions(cat) }
                applyBadge(vh.ringView, vh.pctText, cat.computeProgress())
            }
            is DetailItem.SubTaskItem -> {
                val vh = holder as SubTaskViewHolder
                val subTask = item.subTask
                vh.nameText.text = subTask.subTaskName
                vh.checkBox.setOnCheckedChangeListener(null)
                isBindingCheckbox = true
                vh.checkBox.isChecked = subTask.isCompleted
                isBindingCheckbox = false
                vh.checkBox.setOnCheckedChangeListener { _, checked ->
                    if (!isBindingCheckbox) onSubTaskChecked(subTask, checked)
                }
                vh.optionsBtn.setOnClickListener { onSubTaskOptions(item.category, subTask) }
            }
            is DetailItem.AddItemButton -> {
                (holder as AddItemViewHolder).addBtn.setOnClickListener { onAddItem(item.category) }
            }
            is DetailItem.AddCategoryButton -> {
                (holder as AddCategoryViewHolder).addBtn.setOnClickListener { onAddCategory() }
            }
        }
    }

    // ─── DragHost ─────────────────────────────────────────────────────────────

    override fun canDrag(position: Int): Boolean {
        val type = getItemViewType(position)
        // Categories are only draggable when there are 2+ to swap.
        if (type == TYPE_CATEGORY) return reorderHelper.canDragCategory()
        return type == TYPE_SUBTASK
    }

    override fun canDrop(position: Int): Boolean {
        val targetType = getItemViewType(position)
        return if (reorderHelper.isDragging) {
            // During a category drag the list is headers-only; only category rows exist
            // (plus the terminal AddCategoryButton which we exclude).
            targetType == TYPE_CATEGORY
        } else {
            // Subtask drag: may land on another subtask or a category header
            targetType == TYPE_SUBTASK || targetType == TYPE_CATEGORY
        }
    }

    override fun onItemMoved(from: Int, to: Int) {
        if (from < 0 || to < 0 || from >= items.size || to >= items.size) return

        if (!reorderHelper.isDragging && getItemViewType(from) == TYPE_CATEGORY) {
            // ── First move of a category drag ────────────────────────────────
            // Sync task.categories to the current visual order, then collapse to
            // headers-only so every category becomes exactly one row.
            task.categories.clear()
            task.categories.addAll(
                items.filterIsInstance<DetailItem.CategoryItem>().map { it.category }
            )
            reorderHelper.beginCategoryDrag()
            collapseToHeadersOnly()

            // After collapse the positions are simpler: each category is at its
            // index in task.categories. Clamp both positions into the valid header range.
            val collapsedFrom = from.coerceIn(0, task.categories.size - 1)
            val collapsedTo   = to.coerceIn(0, task.categories.size - 1)
            if (collapsedFrom != collapsedTo) {
                reorderHelper.swapCategories(collapsedFrom, collapsedTo)
                syncItemsFromShadow()
            }
        } else if (reorderHelper.isDragging) {
            // ── Subsequent moves of the same category drag ────────────────────
            // List is already headers-only; swap the two rows and keep the model in sync.
            // Use notifyDataSetChanged (not notifyItemMoved) so ItemTouchHelper's
            // internal ViewHolder position tracking stays accurate across multiple
            // consecutive moves in one gesture.
            reorderHelper.swapCategories(from, to)
            syncItemsFromShadow()
            notifyDataSetChanged()
        } else {
            // ── Subtask drag (unchanged) ──────────────────────────────────────
            val item = items.removeAt(from)
            items.add(to, item)
            notifyItemMoved(from, to)
        }
    }

    /**
     * Rebuilds the [items] list from [reorderHelper].shadowList during a category
     * drag. Each entry becomes a [DetailItem.CategoryItem] for its category, followed
     * by the terminal [DetailItem.AddCategoryButton].
     */
    private fun syncItemsFromShadow() {
        items.clear()
        for (cat in reorderHelper.shadowList) {
            items.add(DetailItem.CategoryItem(cat))
        }
        items.add(DetailItem.AddCategoryButton)
    }

    /**
     * Collapses the flat list to category headers only (+ AddCategoryButton).
     * Called once at the start of a category drag.
     */
    private fun collapseToHeadersOnly() {
        items.clear()
        for (category in task.categories) {
            items.add(DetailItem.CategoryItem(category))
        }
        items.add(DetailItem.AddCategoryButton)
        notifyDataSetChanged()
    }

    override fun onDragFinished() {
        if (reorderHelper.isDragging) {
            // task.categories is already correct (kept in sync by swapCategories).
            // Commit, re-expand, and redraw.  Subtask contents are untouched.
            reorderHelper.commitCategoryDrag()
        } else {
            // ── Subtask drag finish ───────────────────────────────────────────
            // Rebuild each category's subtask list from the current flat-list order.
            for (cat in task.categories) cat.subTasks.clear()
            var currentCat: TaskCategory? = null
            for (flatItem in items) {
                when (flatItem) {
                    is DetailItem.CategoryItem -> currentCat = flatItem.category
                    is DetailItem.SubTaskItem  -> currentCat?.subTasks?.add(flatItem.subTask)
                    else -> { /* AddItemButton / AddCategoryButton — skip */ }
                }
            }
        }
        refresh()
        onDragFinished.invoke()
    }

    // ─── Badge helpers ────────────────────────────────────────────────────────

    private fun makeBadgeFrame(ctx: android.content.Context, dpSize: Int): FrameLayout {
        val dp   = ctx.resources.displayMetrics.density
        val size = (dpSize * dp).toInt()
        val params = LinearLayout.LayoutParams(size, size)
        val frame = FrameLayout(ctx).apply { layoutParams = params }
        val ring = RingView(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        val label = TextView(ctx).apply {
            gravity = Gravity.CENTER
            textSize = 8f
            setTextColor(android.graphics.Color.WHITE)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        frame.addView(ring)
        frame.addView(label)
        return frame
    }

    private fun applyBadge(ring: RingView, label: TextView, percent: Int) {
        label.text = "$percent%"
        ring.setProgress(percent, badgeColor(percent))
    }
}

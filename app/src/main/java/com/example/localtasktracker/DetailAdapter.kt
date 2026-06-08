package com.example.localtasktracker

import android.graphics.Color
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * RecyclerView adapter for Screen 2 — the flat mixed-type detail list.
 *
 * The list is rebuilt as a flat sequence of [DetailItem] entries every time
 * [refresh] is called. Each entry carries enough context to handle clicks and
 * drag moves correctly.
 *
 * View types:
 *   TYPE_CATEGORY     — category header row (draggable among other category headers)
 *   TYPE_SUBTASK      — subtask row (draggable within its category; droppable into another category)
 *   TYPE_ADD_ITEM     — "+ Add Item" button (not draggable)
 *   TYPE_ADD_CATEGORY — "+ Add Category" button (not draggable)
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

    private val items = mutableListOf<DetailItem>()

    /** Rebuild the flat list from the current task/expanded state then notify. */
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
        is DetailItem.CategoryItem     -> TYPE_CATEGORY
        is DetailItem.SubTaskItem      -> TYPE_SUBTASK
        is DetailItem.AddItemButton    -> TYPE_ADD_ITEM
        is DetailItem.AddCategoryButton -> TYPE_ADD_CATEGORY
    }

    // ─── ViewHolders ──────────────────────────────────────────────────────────

    inner class CategoryViewHolder(val row: LinearLayout) : RecyclerView.ViewHolder(row) {
        val arrow:      TextView = row.getChildAt(0) as TextView
        val nameText:   TextView = row.getChildAt(1) as TextView
        val optionsBtn: Button   = row.getChildAt(2) as Button
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
                    setPadding(0, 16, 0, 16)
                    layoutParams = matchWrap
                }
                val arrow = TextView(ctx).apply {
                    textSize = 18f
                    setPadding(0, 0, 16, 0)
                }
                val nameText = TextView(ctx).apply {
                    textSize = 18f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                val optionsBtn = Button(ctx).apply { text = "⋮" }
                row.addView(arrow)
                row.addView(nameText)
                row.addView(optionsBtn)
                CategoryViewHolder(row)
            }
            TYPE_SUBTASK -> {
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(60, 8, 0, 8)
                    layoutParams = matchWrap
                }
                val checkBox = CheckBox(ctx)
                val nameText = TextView(ctx).apply {
                    textSize = 16f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
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
            else -> { // TYPE_ADD_CATEGORY
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
                val toggle = { _: android.view.View ->
                    onCategoryToggle(cat)
                }
                vh.arrow.setOnClickListener(toggle)
                vh.nameText.setOnClickListener(toggle)
                vh.optionsBtn.setOnClickListener { onCategoryOptions(cat) }
            }
            is DetailItem.SubTaskItem -> {
                val vh = holder as SubTaskViewHolder
                val subTask = item.subTask
                vh.nameText.text = subTask.subTaskName
                // Detach listener before setting state to avoid spurious callbacks
                vh.checkBox.setOnCheckedChangeListener(null)
                vh.checkBox.isChecked = subTask.isCompleted
                vh.checkBox.setOnCheckedChangeListener { _, checked ->
                    onSubTaskChecked(subTask, checked)
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

    // ─── DragHost — reorder categories and subtasks ───────────────────────────

    override fun canDrag(position: Int): Boolean =
        getItemViewType(position) == TYPE_CATEGORY || getItemViewType(position) == TYPE_SUBTASK

    override fun canDrop(position: Int): Boolean =
        getItemViewType(position) == TYPE_CATEGORY || getItemViewType(position) == TYPE_SUBTASK

    /**
     * Called continuously while the user is actively dragging — potentially
     * many times per second. We ONLY mutate the flat [items] list and call
     * [notifyItemMoved] here. The real data structures (task.categories,
     * category.subTasks) are NOT touched until [onDragFinished].
     *
     * This matches how TaskAdapter works and allows the drag to glide freely
     * across the entire list instead of resetting after each step.
     */
    override fun onItemMoved(from: Int, to: Int) {
        val fromItem = items[from]
        val toItem   = items[to]

        // Only allow valid drag combinations — silently ignore anything else
        val valid = when {
            fromItem is DetailItem.CategoryItem && toItem is DetailItem.CategoryItem -> true
            fromItem is DetailItem.SubTaskItem  && toItem is DetailItem.SubTaskItem  -> true
            fromItem is DetailItem.SubTaskItem  && toItem is DetailItem.CategoryItem -> true
            else -> false
        }
        if (!valid) return

        // Swap in the flat display list only — no data model writes yet
        items.removeAt(from)
        items.add(to, fromItem)
        notifyItemMoved(from, to)
    }

    /**
     * Called once when the finger lifts. Now we read the final order of [items]
     * and commit it back to the real data structures, then call [refresh] to
     * keep everything in sync.
     */
    override fun onDragFinished() {
        // ── Commit category order ─────────────────────────────────────────────
        val newCategoryOrder = items
            .filterIsInstance<DetailItem.CategoryItem>()
            .map { it.category }
        task.categories.clear()
        task.categories.addAll(newCategoryOrder)

        // ── Commit subtask order (and handle cross-category moves) ────────────
        // Clear all subtask lists first, then repopulate from flat items order
        task.categories.forEach { it.subTasks.clear() }

        for (item in items) {
            if (item is DetailItem.SubTaskItem) {
                // The category reference in the item may be stale if the subtask
                // was dragged to a different category. Find the correct destination
                // category by looking at what category header preceded this subtask
                // in the flat items list.
                val ownerCategory = findOwnerCategory(item)
                ownerCategory?.subTasks?.add(item.subTask)
            }
        }

        // Auto-expand any category that received a subtask from another category
        task.categories.forEach { cat ->
            if (cat.subTasks.isNotEmpty()) expandedCategoryIds.add(cat.id)
        }

        refresh()
        onDragFinished.invoke()
    }

    /**
     * Walk backwards through [items] from the position of [subTaskItem] to find
     * the nearest [DetailItem.CategoryItem] above it — that is the category that
     * now owns this subtask after the drag.
     */
    private fun findOwnerCategory(subTaskItem: DetailItem.SubTaskItem): TaskCategory? {
        val pos = items.indexOf(subTaskItem)
        for (i in pos downTo 0) {
            val item = items[i]
            if (item is DetailItem.CategoryItem) return item.category
        }
        return null
    }
}

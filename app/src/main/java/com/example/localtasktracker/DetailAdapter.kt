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

    override fun onItemMoved(from: Int, to: Int) {
        val fromItem = items[from]
        val toItem   = items[to]

        when {
            // ── Both categories: reorder categories list ──────────────────────
            fromItem is DetailItem.CategoryItem && toItem is DetailItem.CategoryItem -> {
                val fromIdx = task.categories.indexOf(fromItem.category)
                val toIdx   = task.categories.indexOf(toItem.category)
                val cat = task.categories.removeAt(fromIdx)
                task.categories.add(toIdx, cat)
                // Rebuild flat list to keep subtask blocks attached to their category
                refresh()
            }

            // ── SubTask moving within the same category: simple reorder ───────
            fromItem is DetailItem.SubTaskItem && toItem is DetailItem.SubTaskItem &&
            fromItem.category.id == toItem.category.id -> {
                val subTasks = fromItem.category.subTasks
                val fromIdx  = subTasks.indexOf(fromItem.subTask)
                val toIdx    = subTasks.indexOf(toItem.subTask)
                val st = subTasks.removeAt(fromIdx)
                subTasks.add(toIdx, st)
                refresh()
            }

            // ── SubTask dropped onto a different category header ───────────────
            fromItem is DetailItem.SubTaskItem && toItem is DetailItem.CategoryItem -> {
                fromItem.category.subTasks.remove(fromItem.subTask)
                toItem.category.subTasks.add(fromItem.subTask)
                // Auto-expand destination so the moved item is visible
                expandedCategoryIds.add(toItem.category.id)
                refresh()
            }

            // ── SubTask dropped onto a subtask in a different category ─────────
            fromItem is DetailItem.SubTaskItem && toItem is DetailItem.SubTaskItem &&
            fromItem.category.id != toItem.category.id -> {
                fromItem.category.subTasks.remove(fromItem.subTask)
                val destSubTasks = toItem.category.subTasks
                val toIdx = destSubTasks.indexOf(toItem.subTask)
                destSubTasks.add(toIdx, fromItem.subTask)
                expandedCategoryIds.add(toItem.category.id)
                refresh()
            }
        }
    }

    override fun onDragFinished() {
        onDragFinished.invoke()
    }
}

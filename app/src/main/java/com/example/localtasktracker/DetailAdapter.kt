package com.example.localtasktracker

import android.graphics.Color
import android.view.Gravity
import android.view.View
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
 * The data model is now a recursive [Node] tree instead of three separate classes.
 * View types and visual behaviour are identical to before:
 *
 *   TYPE_CATEGORY      — category header row (draggable)
 *   TYPE_SUBTASK       — subtask row; shows progress ring when it has children
 *   TYPE_ADD_ITEM      — "+ Add Item" button under a category (Edit mode only)
 *   TYPE_ADD_CATEGORY  — "+ Add Category" button at the bottom (Edit mode only)
 *   TYPE_SUBITEM       — subitem row under a parent subtask (draggable within parent)
 *   TYPE_ADD_SUBITEM   — "+ Add Subitem" button under a parent subtask (Edit mode only)
 *
 * ── Drag design ───────────────────────────────────────────────────────────────
 * Category drag  : collapses all child rows so only TYPE_CATEGORY rows remain.
 * Subtask drag   : single-row moves; can cross category boundaries.
 * Subitem drag   : all visible rows stay in place; can drop onto any visible
 *                  TYPE_SUBITEM (reorder within parent or move to another parent)
 *                  or onto any TYPE_SUBTASK (move into that item as a subitem).
 *                  Items inside collapsed categories are not visible, so they
 *                  are naturally excluded as drop targets.
 * ─────────────────────────────────────────────────────────────────────────────
 */
class DetailAdapter(
    private val task: Task,
    private val expandedCategoryIds: MutableSet<Int>,
    private val expandedSubTaskIds: MutableSet<Int>,
    private val onCategoryToggle: (Node) -> Unit,
    private val onCategoryOptions: (Node) -> Unit,
    private val onSubTaskToggle: (Node) -> Unit,
    private val onSubTaskChecked: (Node, Boolean) -> Unit,
    private val onSubTaskOptions: (Node /* parent category */, Node /* subtask */) -> Unit,
    private val onAddSubItem: (Node /* parent subtask */) -> Unit,
    private val onSubItemChecked: (Node, Boolean) -> Unit,
    private val onSubItemOptions: (Node /* parent subtask */, Node /* subitem */) -> Unit,
    private val onAddCategory: () -> Unit,
    private val onAddItem: (Node /* parent category */) -> Unit,
    private val onDragFinished: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>(), DragCallback.DragHost {

    companion object {
        const val TYPE_CATEGORY     = 0
        const val TYPE_SUBTASK      = 1
        const val TYPE_ADD_ITEM     = 2
        const val TYPE_ADD_CATEGORY = 3
        const val TYPE_SUBITEM      = 4
        const val TYPE_ADD_SUBITEM  = 5
    }

    // ─── Flat item model ──────────────────────────────────────────────────────

    sealed class DetailItem {
        data class CategoryItem(val category: Node) : DetailItem()
        data class SubTaskItem(val category: Node, val subTask: Node) : DetailItem()
        data class AddItemButton(val category: Node) : DetailItem()
        object AddCategoryButton : DetailItem()
        data class SubItemRow(val subTask: Node, val subItem: Node) : DetailItem()
        data class AddSubItemButton(val subTask: Node) : DetailItem()
    }

    internal val items = mutableListOf<DetailItem>()

    /** True while a category drag gesture is active. */
    private var isDraggingCategory = false

    /** True while a subitem drag gesture is active. */
    private var isDraggingSubItem = false

    /** Controls which rows and buttons are visible. False = View mode, True = Edit mode. */
    var isEditMode: Boolean = false
        private set

    internal val reorderHelper = CategoryReorderHelper(task)

    // ─── Public API ───────────────────────────────────────────────────────────

    fun setEditMode(enabled: Boolean) {
        isEditMode = enabled
        refresh()
    }

    fun refresh() {
        items.clear()
        for (category in task.children) {
            items.add(DetailItem.CategoryItem(category))
            if (category.id in expandedCategoryIds) {
                for (subTask in category.children) {
                    items.add(DetailItem.SubTaskItem(category, subTask))
                    if (subTask.hasChildren() && subTask.id in expandedSubTaskIds) {
                        for (subItem in subTask.children) {
                            items.add(DetailItem.SubItemRow(subTask, subItem))
                        }
                        if (isEditMode) items.add(DetailItem.AddSubItemButton(subTask))
                    }
                }
                if (isEditMode) items.add(DetailItem.AddItemButton(category))
            }
        }
        if (isEditMode) items.add(DetailItem.AddCategoryButton)
        notifyDataSetChanged()
    }

    // ─── Collapse helpers for drag ────────────────────────────────────────────

    private fun collapseAllForCategoryDrag() {
        for (i in items.indices.reversed()) {
            val item = items[i]
            if (item !is DetailItem.CategoryItem && item !is DetailItem.AddCategoryButton) {
                items.removeAt(i)
                notifyItemRemoved(i)
            }
        }
        isDraggingCategory = true
    }

    private fun collapseOtherSubItemsForDrag(parentSubTask: Node) {
        for (i in items.indices.reversed()) {
            val item = items[i]
            val belongsToOtherParent = when (item) {
                is DetailItem.SubItemRow       -> item.subTask.id != parentSubTask.id
                is DetailItem.AddSubItemButton -> item.subTask.id != parentSubTask.id
                else -> false
            }
            if (belongsToOtherParent) {
                items.removeAt(i)
                notifyItemRemoved(i)
            }
        }
        isDraggingSubItem = true
    }

    // ─── RecyclerView.Adapter ─────────────────────────────────────────────────

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is DetailItem.CategoryItem      -> TYPE_CATEGORY
        is DetailItem.SubTaskItem       -> TYPE_SUBTASK
        is DetailItem.AddItemButton     -> TYPE_ADD_ITEM
        is DetailItem.AddCategoryButton -> TYPE_ADD_CATEGORY
        is DetailItem.SubItemRow        -> TYPE_SUBITEM
        is DetailItem.AddSubItemButton  -> TYPE_ADD_SUBITEM
    }

    // ─── ViewHolders ──────────────────────────────────────────────────────────

    inner class CategoryViewHolder(val row: LinearLayout) : RecyclerView.ViewHolder(row) {
        val arrow:      TextView    = row.getChildAt(0) as TextView
        val badgeFrame: FrameLayout = row.getChildAt(1) as FrameLayout
        val nameText:   TextView    = row.getChildAt(2) as TextView
        val optionsBtn: Button      = row.getChildAt(3) as Button
        val ringView:   RingView    = badgeFrame.getChildAt(0) as RingView
        val pctText:    TextView    = badgeFrame.getChildAt(1) as TextView
    }

    inner class SubTaskViewHolder(val row: LinearLayout) : RecyclerView.ViewHolder(row) {
        val arrow:         TextView    = row.getChildAt(0) as TextView
        val badgeFrame:    FrameLayout = row.getChildAt(1) as FrameLayout
        val checkBox:      CheckBox    = row.getChildAt(2) as CheckBox
        val nameText:      TextView    = row.getChildAt(3) as TextView
        val addSubItemBtn: Button      = row.getChildAt(4) as Button
        val optionsBtn:    Button      = row.getChildAt(5) as Button
        val ringView:      RingView    = badgeFrame.getChildAt(0) as RingView
        val pctText:       TextView    = badgeFrame.getChildAt(1) as TextView
    }

    inner class AddItemViewHolder(val row: LinearLayout) : RecyclerView.ViewHolder(row) {
        val addBtn: Button = row.getChildAt(0) as Button
    }

    inner class AddCategoryViewHolder(val row: LinearLayout) : RecyclerView.ViewHolder(row) {
        val addBtn: Button = row.getChildAt(0) as Button
    }

    inner class SubItemViewHolder(val row: LinearLayout) : RecyclerView.ViewHolder(row) {
        val checkBox:   CheckBox = row.getChildAt(0) as CheckBox
        val nameText:   TextView = row.getChildAt(1) as TextView
        val optionsBtn: Button   = row.getChildAt(2) as Button
    }

    inner class AddSubItemViewHolder(val row: LinearLayout) : RecyclerView.ViewHolder(row) {
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
                    gravity = Gravity.CENTER_VERTICAL
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
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
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
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(60, 8, 0, 8)
                    layoutParams = matchWrap
                }
                val arrow = TextView(ctx).apply {
                    textSize = 16f
                    setPadding(0, 0, 8, 0)
                }
                val badgeFrame = makeBadgeFrame(ctx, 36)
                val checkBox = CheckBox(ctx)
                val nameText = TextView(ctx).apply {
                    textSize = 16f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                val addSubItemBtn = Button(ctx).apply { text = "+" }
                val optionsBtn    = Button(ctx).apply { text = "⋮" }
                row.addView(arrow)
                row.addView(badgeFrame)
                row.addView(checkBox)
                row.addView(nameText)
                row.addView(addSubItemBtn)
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

            TYPE_ADD_CATEGORY -> {
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, 8, 0, 16)
                    layoutParams = matchWrap
                }
                val addBtn = Button(ctx).apply { text = "+ Add Category" }
                row.addView(addBtn)
                AddCategoryViewHolder(row)
            }

            TYPE_SUBITEM -> {
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(120, 8, 0, 8)
                    layoutParams = matchWrap
                }
                val checkBox   = CheckBox(ctx)
                val nameText   = TextView(ctx).apply {
                    textSize = 15f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                val optionsBtn = Button(ctx).apply { text = "⋮" }
                row.addView(checkBox)
                row.addView(nameText)
                row.addView(optionsBtn)
                SubItemViewHolder(row)
            }

            else -> { // TYPE_ADD_SUBITEM
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(140, 8, 0, 16)
                    layoutParams = matchWrap
                }
                val addBtn = Button(ctx).apply { text = "+ Add Subitem" }
                row.addView(addBtn)
                AddSubItemViewHolder(row)
            }
        }
    }

    // ─── Bind data ────────────────────────────────────────────────────────────

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {

            is DetailItem.CategoryItem -> {
                val vh  = holder as CategoryViewHolder
                val cat = item.category
                val isExpanded = cat.id in expandedCategoryIds
                vh.arrow.text = if (isExpanded) "▼" else "▶"
                vh.nameText.text = cat.name
                vh.row.setBackgroundColor(Color.TRANSPARENT)
                val toggle = { _: View -> onCategoryToggle(cat) }
                vh.arrow.setOnClickListener(toggle)
                vh.nameText.setOnClickListener(toggle)
                vh.optionsBtn.visibility = if (isEditMode) View.VISIBLE else View.GONE
                vh.optionsBtn.setOnClickListener { onCategoryOptions(cat) }
                applyBadge(vh.ringView, vh.pctText, cat.computeProgress())
            }

            is DetailItem.SubTaskItem -> {
                val vh      = holder as SubTaskViewHolder
                val subTask = item.subTask
                vh.nameText.text = subTask.name

                if (subTask.hasChildren()) {
                    // ── Parent mode (mini-category) ───────────────────────────
                    val isExpanded = subTask.id in expandedSubTaskIds
                    vh.arrow.text            = if (isExpanded) "▼" else "▶"
                    vh.arrow.visibility      = View.VISIBLE
                    vh.badgeFrame.visibility = View.VISIBLE
                    applyBadge(vh.ringView, vh.pctText, subTask.computeProgress())
                    vh.checkBox.visibility   = View.GONE
                    vh.checkBox.setOnCheckedChangeListener(null)
                    val toggle = { _: View -> onSubTaskToggle(subTask) }
                    vh.arrow.setOnClickListener(toggle)
                    vh.nameText.setOnClickListener(toggle)
                } else {
                    // ── Plain checkbox mode ───────────────────────────────────
                    vh.arrow.visibility      = View.GONE
                    vh.arrow.setOnClickListener(null)
                    vh.badgeFrame.visibility = View.GONE
                    vh.checkBox.visibility   = View.VISIBLE
                    vh.nameText.setOnClickListener(null)
                    vh.checkBox.setOnCheckedChangeListener(null)
                    vh.checkBox.isChecked = subTask.isCompleted
                    vh.checkBox.setOnCheckedChangeListener { _, checked ->
                        onSubTaskChecked(subTask, checked)
                    }
                }

                vh.addSubItemBtn.visibility = if (isEditMode) View.VISIBLE else View.GONE
                vh.addSubItemBtn.setOnClickListener { onAddSubItem(subTask) }
                vh.optionsBtn.visibility = if (isEditMode) View.VISIBLE else View.GONE
                vh.optionsBtn.setOnClickListener { onSubTaskOptions(item.category, subTask) }
            }

            is DetailItem.SubItemRow -> {
                val vh      = holder as SubItemViewHolder
                val subItem = item.subItem
                vh.nameText.text = subItem.name
                vh.checkBox.setOnCheckedChangeListener(null)
                vh.checkBox.isChecked = subItem.isCompleted
                vh.checkBox.setOnCheckedChangeListener { _, checked ->
                    onSubItemChecked(subItem, checked)
                }
                vh.optionsBtn.visibility = if (isEditMode) View.VISIBLE else View.GONE
                vh.optionsBtn.setOnClickListener { onSubItemOptions(item.subTask, subItem) }
            }

            is DetailItem.AddItemButton -> {
                (holder as AddItemViewHolder).addBtn.setOnClickListener { onAddItem(item.category) }
            }

            is DetailItem.AddCategoryButton -> {
                (holder as AddCategoryViewHolder).addBtn.setOnClickListener { onAddCategory() }
            }

            is DetailItem.AddSubItemButton -> {
                (holder as AddSubItemViewHolder).addBtn.setOnClickListener { onAddSubItem(item.subTask) }
            }
        }
    }

    // ─── DragHost ─────────────────────────────────────────────────────────────

    override fun onDragStarting(position: Int) {
        when (getItemViewType(position)) {
            TYPE_CATEGORY -> {
                if (!isDraggingCategory) collapseAllForCategoryDrag()
            }
            TYPE_SUBITEM -> {
                // Leave all visible rows in place so subitems from other
                // expanded parents and their parent subtask rows remain
                // visible as valid drop targets.
                isDraggingSubItem = true
            }
            // TYPE_SUBTASK: nothing to collapse
        }
    }

    override fun canDrag(position: Int): Boolean {
        return when (getItemViewType(position)) {
            TYPE_CATEGORY -> reorderHelper.canDragCategory()
            TYPE_SUBTASK  -> true
            TYPE_SUBITEM  -> true   // always draggable — can reorder or move cross-parent
            else -> false
        }
    }

    override fun canDrop(position: Int): Boolean {
        val targetType = getItemViewType(position)
        return when {
            isDraggingCategory -> targetType == TYPE_CATEGORY
            isDraggingSubItem  -> targetType == TYPE_SUBITEM || targetType == TYPE_SUBTASK
            else               -> targetType == TYPE_SUBTASK || targetType == TYPE_CATEGORY
        }
    }

    override fun onItemMoved(from: Int, to: Int) {
        if (from < 0 || to < 0 || from >= items.size || to >= items.size) return

        when {
            isDraggingCategory -> {
                val item = items.removeAt(from)
                items.add(to, item)
                notifyItemMoved(from, to)
                reorderHelper.moveCategory(from, to)
            }
            isDraggingSubItem -> {
                val item = items.removeAt(from)
                items.add(to, item)
                notifyItemMoved(from, to)
            }
            else -> {
                // Subtask drag
                val item = items.removeAt(from)
                items.add(to, item)
                notifyItemMoved(from, to)
            }
        }
    }

    override fun onDragFinished() {
        when {
            isDraggingCategory -> {
                isDraggingCategory = false
                refresh()
            }
            isDraggingSubItem -> {
                isDraggingSubItem = false

                // ── Snapshot every subtask's original children list ────────────
                // Key: subtask node id  →  original MutableList<Node> (copy)
                val snapshot: Map<Int, MutableList<Node>> = buildMap {
                    for (cat in task.children) {
                        for (sub in cat.children) {
                            put(sub.id, sub.children.toMutableList())
                        }
                    }
                }

                // ── Determine the final parent for the dragged subitem ─────────
                //
                // We walk the flat item list to find where the SubItemRow
                // ended up and what surrounds it:
                //
                //   • If its neighbours are other SubItemRows from the SAME
                //     subtask → it stayed in the same parent (reorder).
                //   • If it landed next to SubItemRows from a DIFFERENT subtask,
                //     or directly after a SubTaskItem → it moved cross-parent.
                //   • If it landed directly on a SubTaskItem (the drag settled
                //     on a TYPE_SUBTASK row), the SubItemRow will appear just
                //     after that SubTaskItem in the flat list.
                //
                // Strategy: rebuild each subtask's children list from the flat
                // list, treating the SubTask that immediately precedes each
                // SubItemRow as its new parent.

                // Clear all subtask children that are visible in the flat list.
                val subtasksInFlatList = mutableSetOf<Int>()
                for (flatItem in items) {
                    when (flatItem) {
                        is DetailItem.SubTaskItem  -> subtasksInFlatList.add(flatItem.subTask.id)
                        is DetailItem.SubItemRow   -> subtasksInFlatList.add(flatItem.subTask.id)
                        else -> {}
                    }
                }
                for (cat in task.children) {
                    for (sub in cat.children) {
                        if (sub.id in subtasksInFlatList) sub.children.clear()
                    }
                }

                // Walk the flat list; track the "current subtask" and assign
                // each SubItemRow to it. A SubItemRow's subTask field still
                // holds its OLD parent — we use the tracked currentSubTask
                // instead to determine the new parent.
                var currentSubTask: Node? = null
                for (flatItem in items) {
                    when (flatItem) {
                        is DetailItem.SubTaskItem -> currentSubTask = flatItem.subTask
                        is DetailItem.SubItemRow  -> {
                            val newParent = currentSubTask ?: flatItem.subTask
                            newParent.children.add(flatItem.subItem)
                            // Ensure the new parent is expanded so the subitem
                            // is visible immediately after the drop.
                            if (newParent.id != flatItem.subTask.id) {
                                expandedSubTaskIds.add(newParent.id)
                            }
                        }
                        else -> {}
                    }
                }

                // Restore children of subtasks that were NOT visible in the
                // flat list (inside collapsed categories) — snapshot preserves them.
                for (cat in task.children) {
                    for (sub in cat.children) {
                        if (sub.id !in subtasksInFlatList) {
                            val saved = snapshot[sub.id] ?: continue
                            if (sub.children.isEmpty() && saved.isNotEmpty()) {
                                sub.children.addAll(saved)
                            }
                        }
                    }
                }

                refresh()
            }
            else -> {
                // Subtask drag — rebuild category children lists.
                //
                // Strategy mirrors the old implementation exactly:
                // 1. Snapshot every category's children list.
                // 2. For each EXPANDED category, rebuild from `items`.
                // 3. For each CLOSED category, restore from snapshot.
                // 4. Handle the edge case of a subtask dropped onto a closed category header.

                val snapshot: Map<Int, MutableList<Node>> = task.children.associate { cat ->
                    cat.id to cat.children.toMutableList()
                }

                // Clear only expanded categories.
                for (cat in task.children) {
                    if (cat.id in expandedCategoryIds) cat.children.clear()
                }

                // Walk flat list and fill categories.
                var currentCat: Node? = null
                var currentCatIsExpanded: Boolean = false
                for (flatItem in items) {
                    when (flatItem) {
                        is DetailItem.CategoryItem -> {
                            currentCat = flatItem.category
                            currentCatIsExpanded = currentCat.id in expandedCategoryIds
                        }
                        is DetailItem.SubTaskItem -> {
                            if (currentCatIsExpanded) {
                                currentCat?.children?.add(flatItem.subTask)
                            } else {
                                // Subtask dropped onto a closed category header.
                                currentCat?.let { closedCat ->
                                    val saved = snapshot[closedCat.id]
                                    if (saved != null && !closedCat.children.containsAll(saved)) {
                                        closedCat.children.clear()
                                        closedCat.children.addAll(saved)
                                    }
                                    if (flatItem.subTask !in closedCat.children) {
                                        closedCat.children.add(flatItem.subTask)
                                    }
                                    snapshot[flatItem.category.id]?.remove(flatItem.subTask)
                                }
                            }
                        }
                        else -> { /* skip */ }
                    }
                }

                // Restore untouched closed categories.
                for (cat in task.children) {
                    if (cat.id !in expandedCategoryIds) {
                        val saved = snapshot[cat.id] ?: continue
                        if (cat.children.isEmpty() && saved.isNotEmpty()) {
                            cat.children.addAll(saved)
                        }
                    }
                }

                refresh()
            }
        }
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
            setTextColor(Color.WHITE)
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

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
 * View types:
 *   TYPE_CATEGORY      — category header row (draggable)
 *   TYPE_SUBTASK       — subtask row; acts as mini-category when it has subitems
 *   TYPE_ADD_ITEM      — "+ Add Item" button under a category (Edit mode only)
 *   TYPE_ADD_CATEGORY  — "+ Add Category" button at the bottom (Edit mode only)
 *   TYPE_SUBITEM       — subitem row under a parent subtask (draggable within parent)
 *   TYPE_ADD_SUBITEM   — "+ Add Subitem" button under a parent subtask (Edit mode only)
 *
 * ── Drag design ───────────────────────────────────────────────────────────────
 * Category drag  : onDragStarting collapses all child rows via incremental
 *                  notifyItemRemoved so ItemTouchHelper never sees notifyDataSetChanged.
 *                  Each subsequent onItemMoved is a plain removeAt+add+notifyItemMoved.
 * Subtask drag   : single-row moves; can cross category boundaries.
 * Subitem drag   : onDragStarting collapses all OTHER parent subtasks' subitems so
 *                  only the dragged subitem's siblings remain visible. Subitems can
 *                  only land on TYPE_SUBITEM rows (within the same parent block).
 * ─────────────────────────────────────────────────────────────────────────────
 */
class DetailAdapter(
    private val task: Task,
    private val expandedCategoryIds: MutableSet<Int>,
    private val expandedSubTaskIds: MutableSet<Int>,
    private val onCategoryToggle: (TaskCategory) -> Unit,
    private val onCategoryOptions: (TaskCategory) -> Unit,
    private val onSubTaskToggle: (SubTask) -> Unit,
    private val onSubTaskChecked: (SubTask, Boolean) -> Unit,
    private val onSubTaskOptions: (TaskCategory, SubTask) -> Unit,
    private val onAddSubItem: (SubTask) -> Unit,
    private val onSubItemChecked: (SubItem, Boolean) -> Unit,
    private val onSubItemOptions: (SubTask, SubItem) -> Unit,
    private val onAddCategory: () -> Unit,
    private val onAddItem: (TaskCategory) -> Unit,
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
        data class CategoryItem(val category: TaskCategory) : DetailItem()
        data class SubTaskItem(val category: TaskCategory, val subTask: SubTask) : DetailItem()
        data class AddItemButton(val category: TaskCategory) : DetailItem()
        object AddCategoryButton : DetailItem()
        data class SubItemRow(val subTask: SubTask, val subItem: SubItem) : DetailItem()
        data class AddSubItemButton(val subTask: SubTask) : DetailItem()
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
        for (category in task.categories) {
            items.add(DetailItem.CategoryItem(category))
            if (category.id in expandedCategoryIds) {
                for (subTask in category.subTasks) {
                    items.add(DetailItem.SubTaskItem(category, subTask))
                    if (subTask.hasSubItems() && subTask.id in expandedSubTaskIds) {
                        for (subItem in subTask.subItems) {
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

    /** Collapses all category child rows (subtasks, subitems, add-buttons) for category drag. */
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

    /**
     * For a subitem drag: collapses subitems belonging to every parent subtask
     * OTHER than [parentSubTask], so only that parent's subitems are visible.
     * The dragged subitem's siblings stay in the list — drag is within-parent only.
     */
    private fun collapseOtherSubItemsForDrag(parentSubTask: SubTask) {
        for (i in items.indices.reversed()) {
            val item = items[i]
            val belongsToOtherParent = when (item) {
                is DetailItem.SubItemRow      -> item.subTask.id != parentSubTask.id
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
        // arrow | badgeFrame | nameText | optionsBtn
        val arrow:      TextView    = row.getChildAt(0) as TextView
        val badgeFrame: FrameLayout = row.getChildAt(1) as FrameLayout
        val nameText:   TextView    = row.getChildAt(2) as TextView
        val optionsBtn: Button      = row.getChildAt(3) as Button
        val ringView:   RingView    = badgeFrame.getChildAt(0) as RingView
        val pctText:    TextView    = badgeFrame.getChildAt(1) as TextView
    }

    /**
     * SubTask row — layout: arrow | badgeFrame | checkBox | nameText | addSubItemBtn | optionsBtn
     *
     * When the subtask has subitems:  arrow=▼/▶, badgeFrame=VISIBLE, checkBox=GONE
     * When plain (no subitems):       arrow=GONE, badgeFrame=GONE,    checkBox=VISIBLE
     */
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

    /** SubItem row — mirrors SubTaskViewHolder but no arrow/addSubItemBtn. */
    inner class SubItemViewHolder(val row: LinearLayout) : RecyclerView.ViewHolder(row) {
        // checkBox | nameText | optionsBtn
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
                    // Indent deeper than subtasks (subtasks are at 60)
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
                vh.nameText.text = cat.categoryName
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
                vh.nameText.text = subTask.subTaskName

                if (subTask.hasSubItems()) {
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
                    // Clear before setting isChecked so the recycled ViewHolder's
                    // old listener cannot fire during rebind.
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
                vh.nameText.text = subItem.subItemName
                // Clear before setting isChecked — same recycling safety as above.
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
                if (!isDraggingSubItem) {
                    val parentSubTask = (items[position] as DetailItem.SubItemRow).subTask
                    collapseOtherSubItemsForDrag(parentSubTask)
                }
            }
            // TYPE_SUBTASK: nothing to collapse
        }
    }

    override fun canDrag(position: Int): Boolean {
        return when (getItemViewType(position)) {
            TYPE_CATEGORY -> reorderHelper.canDragCategory()
            TYPE_SUBTASK  -> true
            TYPE_SUBITEM  -> {
                // Only draggable when there are 2+ siblings to reorder
                val subTask = (items[position] as DetailItem.SubItemRow).subTask
                subTask.subItems.size >= 2
            }
            else -> false
        }
    }

    override fun canDrop(position: Int): Boolean {
        val targetType = getItemViewType(position)
        return when {
            isDraggingCategory -> targetType == TYPE_CATEGORY
            isDraggingSubItem  -> targetType == TYPE_SUBITEM
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
                // Rebuild each subtask's subItems list from the flat list order
                val seenSubTasks = mutableSetOf<Int>()
                for (flatItem in items) {
                    if (flatItem is DetailItem.SubItemRow) {
                        val st = flatItem.subTask
                        if (st.id !in seenSubTasks) {
                            st.subItems.clear()
                            seenSubTasks.add(st.id)
                        }
                        st.subItems.add(flatItem.subItem)
                    }
                }
                refresh()
            }
            else -> {
                // Subtask drag — rebuild only the categories that are visible (expanded)
                // in the flat list. Closed categories are absent from `items` entirely,
                // so we must NOT clear them; otherwise their subtasks would be lost.

                // Step 1: Snapshot the current subTask lists for ALL categories so we
                //         can restore any category that the flat list doesn't cover.
                val snapshot = task.categories.associate { cat ->
                    cat.id to cat.subTasks.toMutableList()
                }

                // Step 2: Collect the set of category IDs that are actually represented
                //         in the flat list (i.e. currently expanded/visible).
                val visibleCatIds = items
                    .filterIsInstance<DetailItem.CategoryItem>()
                    .map { it.category.id }
                    .toSet()

                // Step 3: Clear only the visible categories — we are about to refill
                //         them from the (possibly reordered) flat list.
                for (cat in task.categories) {
                    if (cat.id in visibleCatIds) cat.subTasks.clear()
                }

                // Step 4: Walk the flat list and assign subtasks to their new category.
                //         Subtasks that land under a closed category header are correctly
                //         captured here because canDrop() allows dropping onto a
                //         TYPE_CATEGORY row, making it appear in `items`.
                var currentCat: TaskCategory? = null
                for (flatItem in items) {
                    when (flatItem) {
                        is DetailItem.CategoryItem -> currentCat = flatItem.category
                        is DetailItem.SubTaskItem  -> currentCat?.subTasks?.add(flatItem.subTask)
                        else -> { /* skip non-subtask rows */ }
                    }
                }

                // Step 5: Restore closed categories that were never cleared.
                //         For safety, any visible category that somehow still ended up
                //         empty while it had items before is also restored (shouldn't
                //         happen, but guards against edge cases).
                for (cat in task.categories) {
                    if (cat.id !in visibleCatIds) {
                        // Closed category — flat list never touched it; restore snapshot.
                        val saved = snapshot[cat.id]
                        if (saved != null) {
                            cat.subTasks.clear()
                            cat.subTasks.addAll(saved)
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

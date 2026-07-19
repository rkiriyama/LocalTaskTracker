package com.example.localtasktracker

import android.graphics.Color
import android.graphics.Typeface
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
 * Supports unlimited nesting depth via a recursive tree walk.
 *
 * View types:
 *   TYPE_NODE         — any data row (category or nested node)
 *   TYPE_ADD_CHILD    — "+ Add" button under any expanded node (Edit mode)
 *   TYPE_ADD_CATEGORY — "+ Add Category" at the bottom (Edit mode)
 *
 * Rendering rule (applied to every TYPE_NODE row):
 *   • Depth 0 (CATEGORY): always bold, always shows progress ring
 *   • Depth ≥ 1, has children: bold, collapsible, shows progress ring
 *   • Depth ≥ 1, no children: checkbox
 *
 * Drag: siblings-only reorder within the same parent at the same depth.
 */
class DetailAdapter(
    private val task: Task,
    private val expandedNodeIds: MutableSet<Int>,
    private val onNodeToggle: (Node) -> Unit,
    private val onNodeChecked: (Node, Boolean) -> Unit,
    private val onNodeOptions: (Node /* node */, Node? /* parent */) -> Unit,
    private val onAddChild: (Node /* parent */) -> Unit,
    private val onAddCategory: () -> Unit,
    private val onDragFinished: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>(), DragCallback.DragHost {

    companion object {
        const val TYPE_NODE         = 0
        const val TYPE_ADD_CHILD    = 1
        const val TYPE_ADD_CATEGORY = 2

        private const val BASE_INDENT_PX = 0
        private const val INDENT_STEP_PX = 56
    }

    // ─── Flat item model ──────────────────────────────────────────────────────

    sealed class DetailItem {
        /** A data row representing a node at a given [depth]. */
        data class NodeRow(
            val node: Node,
            val parent: Node?,
            val depth: Int
        ) : DetailItem()

        /** "+ Add" button shown under an expanded parent. */
        data class AddChildButton(val parent: Node, val depth: Int) : DetailItem()

        /** "+ Add Category" button at the very bottom. */
        object AddCategoryButton : DetailItem()
    }

    internal val items = mutableListOf<DetailItem>()

    /** Controls which rows and buttons are visible. */
    var isEditMode: Boolean = false
        private set

    /** True while a drag gesture is active. */
    private var isDragging = false
    /** The parent node whose children are being reordered during drag. */
    private var dragParent: Node? = null

    internal val reorderHelper = CategoryReorderHelper(task)

    // ─── Public API ───────────────────────────────────────────────────────────

    fun setEditMode(enabled: Boolean) {
        isEditMode = enabled
        refresh()
    }

    fun refresh() {
        items.clear()
        for (category in task.children) {
            buildFlat(category, null, 0)
        }
        if (isEditMode) items.add(DetailItem.AddCategoryButton)
        notifyDataSetChanged()
    }

    /**
     * Recursively flattens [node] and its expanded children into [items].
     */
    private fun buildFlat(node: Node, parent: Node?, depth: Int) {
        items.add(DetailItem.NodeRow(node, parent, depth))
        if (node.hasChildren() && node.id in expandedNodeIds) {
            for (child in node.children) {
                buildFlat(child, node, depth + 1)
            }
            if (isEditMode) items.add(DetailItem.AddChildButton(node, depth + 1))
        }
    }

    // ─── RecyclerView.Adapter ─────────────────────────────────────────────────

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is DetailItem.NodeRow         -> TYPE_NODE
        is DetailItem.AddChildButton  -> TYPE_ADD_CHILD
        is DetailItem.AddCategoryButton -> TYPE_ADD_CATEGORY
    }

    // ─── ViewHolders ──────────────────────────────────────────────────────────

    inner class NodeViewHolder(val row: LinearLayout) : RecyclerView.ViewHolder(row) {
        val arrow:      TextView    = row.getChildAt(0) as TextView
        val badgeFrame: FrameLayout = row.getChildAt(1) as FrameLayout
        val checkBox:   CheckBox    = row.getChildAt(2) as CheckBox
        val nameText:   TextView    = row.getChildAt(3) as TextView
        val addBtn:     Button      = row.getChildAt(4) as Button
        val optionsBtn: Button      = row.getChildAt(5) as Button
        val ringView:   RingView    = badgeFrame.getChildAt(0) as RingView
        val pctText:    TextView    = badgeFrame.getChildAt(1) as TextView
    }

    inner class AddChildViewHolder(val row: LinearLayout) : RecyclerView.ViewHolder(row) {
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
            TYPE_NODE -> {
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = matchWrap
                }
                val arrow = TextView(ctx).apply {
                    textSize = 16f
                    setPadding(0, 0, 8, 0)
                }
                val badgeFrame = makeBadgeFrame(ctx, 38)
                val checkBox = CheckBox(ctx)
                val nameText = TextView(ctx).apply {
                    textSize = 16f
                    layoutParams = LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                    )
                }
                val addBtn     = Button(ctx).apply { text = "+" }
                val optionsBtn = Button(ctx).apply { text = "⋮" }
                row.addView(arrow)
                row.addView(badgeFrame)
                row.addView(checkBox)
                row.addView(nameText)
                row.addView(addBtn)
                row.addView(optionsBtn)
                NodeViewHolder(row)
            }
            TYPE_ADD_CHILD -> {
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = matchWrap
                }
                val addBtn = Button(ctx).apply { text = "+ Add" }
                row.addView(addBtn)
                AddChildViewHolder(row)
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

            is DetailItem.NodeRow -> {
                val vh   = holder as NodeViewHolder
                val node = item.node
                val depth = item.depth
                val indent = BASE_INDENT_PX + depth * INDENT_STEP_PX
                vh.row.setPadding(indent, 8, 0, 8)

                vh.nameText.text = node.name

                val isCategory = (node.nodeType == NodeType.CATEGORY)
                val hasKids    = node.hasChildren()

                if (hasKids || isCategory) {
                    // ── Collapsible header / mini-category mode ────────────────
                    val isExpanded = node.id in expandedNodeIds
                    vh.arrow.text       = if (isExpanded) "▼" else "▶"
                    vh.arrow.visibility = View.VISIBLE
                    vh.badgeFrame.visibility = View.VISIBLE
                    applyBadge(vh.ringView, vh.pctText, node.computeProgress())
                    vh.checkBox.visibility = View.GONE
                    vh.checkBox.setOnCheckedChangeListener(null)

                    val toggle = { _: View -> onNodeToggle(node) }
                    vh.arrow.setOnClickListener(toggle)
                    vh.nameText.setOnClickListener(toggle)

                    // Bold for depth 0 or any node with children
                    vh.nameText.setTypeface(null, Typeface.BOLD)
                    vh.nameText.textSize = if (isCategory) 18f else 16f
                } else {
                    // ── Leaf checkbox mode ─────────────────────────────────────
                    vh.arrow.visibility     = View.GONE
                    vh.arrow.setOnClickListener(null)
                    vh.badgeFrame.visibility = View.GONE
                    vh.checkBox.visibility  = View.VISIBLE
                    vh.nameText.setOnClickListener(null)
                    vh.nameText.setTypeface(null, Typeface.NORMAL)
                    vh.nameText.textSize = 16f

                    vh.checkBox.setOnCheckedChangeListener(null)
                    vh.checkBox.isChecked = node.isCompleted
                    vh.checkBox.setOnCheckedChangeListener { _, checked ->
                        onNodeChecked(node, checked)
                    }
                }

                vh.addBtn.visibility = if (isEditMode) View.VISIBLE else View.GONE
                vh.addBtn.setOnClickListener { onAddChild(node) }
                vh.optionsBtn.visibility = if (isEditMode) View.VISIBLE else View.GONE
                vh.optionsBtn.setOnClickListener { onNodeOptions(node, item.parent) }
            }

            is DetailItem.AddChildButton -> {
                val vh = holder as AddChildViewHolder
                val indent = BASE_INDENT_PX + item.depth * INDENT_STEP_PX
                vh.row.setPadding(indent, 4, 0, 12)
                vh.addBtn.setOnClickListener { onAddChild(item.parent) }
            }

            is DetailItem.AddCategoryButton -> {
                (holder as AddCategoryViewHolder).addBtn.setOnClickListener { onAddCategory() }
            }
        }
    }

    // ─── DragHost — siblings-only reorder ─────────────────────────────────────

    override fun onDragStarting(position: Int) {
        if (getItemViewType(position) != TYPE_NODE) return
        val item = items[position] as DetailItem.NodeRow
        isDragging = true
        dragParent = item.parent

        // Collapse the list so only siblings (same parent, same depth) and
        // any non-sibling rows above/below remain — but for simplicity we
        // keep all rows visible and just restrict canDrop.
    }

    override fun canDrag(position: Int): Boolean {
        if (getItemViewType(position) != TYPE_NODE) return false
        val item = items[position] as DetailItem.NodeRow
        // Need at least 2 siblings to reorder
        val siblings = if (item.parent == null) task.children else item.parent.children
        return siblings.size >= 2
    }

    override fun canDrop(position: Int): Boolean {
        if (!isDragging) return false
        if (getItemViewType(position) != TYPE_NODE) return false
        val target = items[position] as DetailItem.NodeRow
        // Only allow dropping onto siblings (same parent)
        return target.parent?.id == dragParent?.id
    }

    override fun onItemMoved(from: Int, to: Int) {
        if (from < 0 || to < 0 || from >= items.size || to >= items.size) return
        val item = items.removeAt(from)
        items.add(to, item)
        notifyItemMoved(from, to)
    }

    override fun onDragFinished() {
        if (!isDragging) return
        isDragging = false

        // Rebuild the parent's children list from the flat list order.
        val parent = dragParent
        val childrenList = if (parent == null) task.children else parent.children
        val newOrder = mutableListOf<Node>()

        for (flatItem in items) {
            if (flatItem is DetailItem.NodeRow) {
                if (flatItem.parent?.id == parent?.id) {
                    newOrder.add(flatItem.node)
                }
            }
        }

        childrenList.clear()
        childrenList.addAll(newOrder)
        dragParent = null

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

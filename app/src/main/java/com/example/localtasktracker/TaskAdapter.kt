package com.example.localtasktracker

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * RecyclerView adapter for Screen 1 — the flat task list.
 *
 * View types:
 *   TYPE_TASK    — a task row (draggable)
 *   TYPE_ADD     — the "+ Add Checklist" button (not draggable)
 */
class TaskAdapter(
    private val tasks: MutableList<Task>,
    private val onTaskClick: (Task) -> Unit,
    private val onOptionsClick: (Task) -> Unit,
    private val onAddClick: () -> Unit,
    private val onDragFinished: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>(), DragCallback.DragHost {

    companion object {
        const val TYPE_TASK = 0
        const val TYPE_ADD  = 1
    }

    // ─── Item count: all tasks + 1 add-button row ─────────────────────────────

    override fun getItemCount(): Int = tasks.size + 1

    override fun getItemViewType(position: Int): Int =
        if (position < tasks.size) TYPE_TASK else TYPE_ADD

    // ─── ViewHolders ──────────────────────────────────────────────────────────

    inner class TaskViewHolder(val row: LinearLayout) : RecyclerView.ViewHolder(row) {
        val nameText:   TextView = row.getChildAt(0) as TextView
        val badgeView:  TextView = row.getChildAt(1) as TextView
        val optionsBtn: Button   = row.getChildAt(2) as Button
    }

    inner class AddViewHolder(val row: LinearLayout) : RecyclerView.ViewHolder(row) {
        val addBtn: Button = row.getChildAt(0) as Button
    }

    // ─── Create views ─────────────────────────────────────────────────────────

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val ctx = parent.context
        return when (viewType) {
            TYPE_TASK -> {
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, 24, 0, 24)
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }
                val nameText = TextView(ctx).apply {
                    textSize = 20f
                    layoutParams = LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                    )
                }
                val badgeView = makeBadgeView(ctx)
                val optionsBtn = Button(ctx).apply { text = "⋮" }
                row.addView(nameText)
                row.addView(badgeView)
                row.addView(optionsBtn)
                TaskViewHolder(row)
            }
            else -> {
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, 8, 0, 16)
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }
                val addBtn = Button(ctx).apply { text = "+ Add Checklist" }
                row.addView(addBtn)
                AddViewHolder(row)
            }
        }
    }

    // ─── Bind data ────────────────────────────────────────────────────────────

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is TaskViewHolder -> {
                val task = tasks[position]
                holder.nameText.text = task.title
                holder.nameText.setOnClickListener { onTaskClick(task) }
                holder.optionsBtn.setOnClickListener { onOptionsClick(task) }

                // Task progress = average of all category completion percentages
                val progress = if (task.categories.isEmpty()) 0
                    else task.categories.map { it.computeProgress() }.average().toInt()
                applyBadge(holder.badgeView, progress)
            }
            is AddViewHolder -> {
                holder.addBtn.setOnClickListener { onAddClick() }
            }
        }
    }

    // ─── Badge helpers ────────────────────────────────────────────────────────

    private fun makeBadgeView(ctx: android.content.Context): TextView {
        val size = (48 * ctx.resources.displayMetrics.density).toInt()
        return TextView(ctx).apply {
            gravity = Gravity.CENTER
            textSize = 10f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(size, size).also {
                it.marginStart = 8
                it.marginEnd = 8
            }
        }
    }

    private fun applyBadge(badge: TextView, percent: Int) {
        badge.text = "$percent%"
        val color = when {
            percent >= 100 -> Color.parseColor("#4CAF50") // green
            percent > 50   -> Color.parseColor("#FFC107") // yellow/amber
            else           -> Color.parseColor("#F44336") // red
        }
        val circle = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
        badge.background = circle
    }

    // ─── DragHost — reorder tasks list ───────────────────────────────────────

    override fun canDrag(position: Int): Boolean = getItemViewType(position) == TYPE_TASK
    override fun canDrop(position: Int): Boolean = getItemViewType(position) == TYPE_TASK

    override fun onItemMoved(from: Int, to: Int) {
        val item = tasks.removeAt(from)
        tasks.add(to, item)
        notifyItemMoved(from, to)
    }

    override fun onDragFinished() {
        onDragFinished.invoke()
    }
}

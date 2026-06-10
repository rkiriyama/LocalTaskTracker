package com.example.localtasktracker

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * RecyclerView adapter for Screen 1 — the flat task list.
 *
 * View types:
 *   TYPE_TASK — a task row (draggable)
 *   TYPE_ADD  — the "+ Add Checklist" button (not draggable)
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

    override fun getItemCount(): Int = tasks.size + 1

    override fun getItemViewType(position: Int): Int =
        if (position < tasks.size) TYPE_TASK else TYPE_ADD

    // ─── ViewHolders ──────────────────────────────────────────────────────────

    inner class TaskViewHolder(val row: LinearLayout) : RecyclerView.ViewHolder(row) {
        // order: badgeFrame | nameText | optionsBtn
        val badgeFrame: FrameLayout = row.getChildAt(0) as FrameLayout
        val nameText:   TextView    = row.getChildAt(1) as TextView
        val optionsBtn: Button      = row.getChildAt(2) as Button
        val ringView:   RingView    = badgeFrame.getChildAt(0) as RingView
        val pctText:    TextView    = badgeFrame.getChildAt(1) as TextView
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
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, 24, 0, 24)
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }
                val badgeFrame = makeBadgeFrame(ctx)
                val nameText = TextView(ctx).apply {
                    textSize = 20f
                    layoutParams = LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                    )
                }
                val optionsBtn = Button(ctx).apply { text = "⋮" }
                row.addView(badgeFrame)
                row.addView(nameText)
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
                holder.row.setOnClickListener { onTaskClick(task) }
                holder.optionsBtn.setOnClickListener { onOptionsClick(task) }
                val progress = if (task.categories.isEmpty()) 0
                    else task.categories.map { it.computeProgress() }.average().toInt()
                applyBadge(holder.ringView, holder.pctText, progress)
            }
            is AddViewHolder -> holder.addBtn.setOnClickListener { onAddClick() }
        }
    }

    // ─── Badge helpers ────────────────────────────────────────────────────────

    private fun makeBadgeFrame(ctx: android.content.Context): FrameLayout {
        val dp = ctx.resources.displayMetrics.density
        val size = (52 * dp).toInt()
        val params = LinearLayout.LayoutParams(size, size).apply {
            marginEnd = 12
        }
        val frame = FrameLayout(ctx).apply { layoutParams = params }

        val ring = RingView(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        val label = TextView(ctx).apply {
            gravity = Gravity.CENTER
            textSize = 9f
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
        val color = badgeColor(percent)
        ring.setProgress(percent, color)
    }

    // ─── DragHost ─────────────────────────────────────────────────────────────

    override fun onDragStarting(position: Int) { /* tasks are always single-row — nothing to collapse */ }
    override fun canDrag(position: Int): Boolean = getItemViewType(position) == TYPE_TASK
    override fun canDrop(position: Int): Boolean = getItemViewType(position) == TYPE_TASK

    override fun onItemMoved(from: Int, to: Int) {
        if (from < 0 || to < 0 || from >= tasks.size || to >= tasks.size) return
        val item = tasks.removeAt(from)
        tasks.add(to, item)
        notifyItemMoved(from, to)
    }

    override fun onDragFinished() {
        onDragFinished.invoke()
    }
}

// ─── Shared color helper (file-level, used by both adapters) ──────────────────

fun badgeColor(percent: Int): Int = when {
    percent >= 100 -> Color.parseColor("#4CAF50") // green
    percent >= 50  -> Color.parseColor("#FFC107") // amber
    else           -> Color.parseColor("#F44336") // red
}

// ─── Custom view: circular progress ring ─────────────────────────────────────

class RingView(ctx: android.content.Context) : View(ctx) {

    private var sweepAngle = 0f
    private var arcColor   = Color.parseColor("#F44336")

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style  = Paint.Style.STROKE
        color  = Color.parseColor("#44FFFFFF") // translucent white track
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style  = Paint.Style.STROKE
    }

    fun setProgress(percent: Int, color: Int) {
        sweepAngle = percent / 100f * 360f
        arcColor   = color
        arcPaint.color = color
        trackPaint.color = Color.parseColor("#33000000")
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val stroke = width * 0.14f
        trackPaint.strokeWidth = stroke
        arcPaint.strokeWidth   = stroke

        val inset = stroke / 2f
        val oval  = RectF(inset, inset, width - inset, height - inset)

        // Background filled circle (dark tint of arc color)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(60,
                Color.red(arcColor), Color.green(arcColor), Color.blue(arcColor))
        }
        canvas.drawOval(oval, fillPaint)

        // Grey track ring
        canvas.drawArc(oval, -90f, 360f, false, trackPaint)

        // Colored progress arc
        if (sweepAngle > 0f) {
            canvas.drawArc(oval, -90f, sweepAngle, false, arcPaint)
        }
    }
}

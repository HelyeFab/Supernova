package com.supernova.launcher.jp.list

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.supernova.launcher.R
import com.supernova.launcher.data.LabelOrder
import com.supernova.launcher.data.LaunchableApp
import com.supernova.launcher.notification.NotificationPreview
import com.supernova.launcher.notification.SupernovaNotificationListener
import kotlin.math.abs
import kotlin.math.exp

// JP fork, list mode: the home page as a list. Favourites first (chosen and
// ordered from each app's menu), then every app in sections - A-Z, #, the
// kana rows あ-わ and 漢 (ListSections). A scrubber on the right edge jumps to
// a section, and the rows near the finger bow out of its way. Unread
// notifications sit under their app: tap opens, swipe left dismisses. Tap an
// app to open it, long-press for its menu, swipe right for its shortcuts.
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class ListHomeView(context: Context) : FrameLayout(context) {

    private sealed interface Row {
        data object Clock : Row
        data class App(val app: LaunchableApp, val favourite: Boolean) : Row
        data class Note(val app: LaunchableApp, val note: NotificationPreview) : Row
        data class Header(val section: String) : Row
        data object Gap : Row
    }

    private val host = context as? ListHost
    private val recycler = RecyclerView(context)
    private val adapter = Adapter()
    private val scrubber = Scrubber()
    private val bubble = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 34f)
        setTextColor(FG)
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        visibility = GONE
        setShadowLayer(dp(6).toFloat(), 0f, 0f, SHADOW)
    }
    private var rows: List<Row> = emptyList()
    private var sectionAt: Map<String, Int> = emptyMap()
    private val onBus: () -> Unit = { rebuild() }
    private val onTop: () -> Unit = { recycler.smoothScrollToPosition(0) }
    // The serif clock heads the list (date, time, battery, moon, paw, weather).
    private val clock by lazy { com.supernova.launcher.jp.home.SerifClockView(context) }
    private val slop = ViewConfiguration.get(context).scaledTouchSlop

    init {
        recycler.layoutManager = LinearLayoutManager(context)
        recycler.adapter = adapter
        recycler.itemAnimator = null
        recycler.clipToPadding = false
        recycler.setPadding(dp(12), dp(8), dp(SCRUB_DP + 8), dp(24))
        recycler.isVerticalScrollBarEnabled = false
        addView(recycler, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(scrubber, LayoutParams(dp(SCRUB_DP), LayoutParams.MATCH_PARENT, Gravity.END))
        addView(bubble, LayoutParams(dp(64), dp(64), Gravity.END or Gravity.TOP).apply { marginEnd = dp(SCRUB_DP + 24) })
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ListBus.add(onBus)
        ListBus.addTop(onTop)
        rebuild()
    }

    override fun onDetachedFromWindow() {
        ListBus.remove(onBus)
        ListBus.removeTop(onTop)
        super.onDetachedFromWindow()
    }

    // True when a touch at this screen point starts on an app row: its
    // rightward swipe is the row's (shortcuts), not the feed pane's.
    fun rowAt(rawX: Float, rawY: Float): Boolean {
        if (!isShown) return false
        val at = IntArray(2)
        for (i in 0 until recycler.childCount) {
            val child = recycler.getChildAt(i)
            if (child !is AppRow) continue
            child.getLocationOnScreen(at)
            if (rawX >= at[0] && rawX < at[0] + child.width && rawY >= at[1] && rawY < at[1] + child.height) return true
        }
        return false
    }

    private fun rebuild() {
        val host = host ?: return
        // One row per app: a work-profile or App Twin copy only when there is
        // no personal one. Favourites still find their app by package.
        val apps = host.listApps()
            .sortedBy { if (it.userSerial == 0L) 0 else 1 }
            .distinctBy { it.packageName + "/" + it.className }
        val byKey = apps.associateBy(host::appKey)
        val byComponent = apps.associateBy { it.packageName + "/" + it.className }
        val out = ArrayList<Row>()
        out += Row.Clock
        fun addApp(app: LaunchableApp, fav: Boolean) {
            out += Row.App(app, fav)
            host.notificationsFor(app).take(MAX_NOTES).forEach { out += Row.Note(app, it) }
        }
        host.listFavourites()
            .mapNotNull { key -> byKey[key] ?: byComponent[key.substringAfter('/')] }
            .distinct()
            .forEach { addApp(it, true) }
        out += Row.Gap
        val sections = LinkedHashMap<String, Int>()
        apps.groupBy { ListSections.sectionOf(it.label) }
            .toSortedMap(compareBy(ListSections::rank))
            .forEach { (section, inSection) ->
                sections[section] = out.size
                out += Row.Header(section)
                LabelOrder.sorted(inSection) { it.label }.forEach { addApp(it, false) }
            }
        rows = out
        sectionAt = sections
        adapter.notifyDataSetChanged()
        scrubber.labels = listOf(STAR) + sections.keys
        scrubber.invalidate()
    }

    // ---- rows

    private inner class Adapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = rows.size
        override fun getItemViewType(position: Int) = when (rows[position]) {
            is Row.App -> 0; is Row.Note -> 1; is Row.Header -> 2; Row.Gap -> 3; Row.Clock -> 4
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view: View = when (viewType) {
                0 -> AppRow()
                1 -> NoteRow()
                4 -> FrameLayout(context).apply {
                    // The clock keeps its own size; it moves between rebinds.
                    (clock.parent as? ViewGroup)?.removeView(clock)
                    addView(clock, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(CLOCK_DP)))
                }
                2 -> text(13f, SUB).apply {
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(dp(6), dp(14), 0, dp(4))
                }
                else -> View(context).apply { minimumHeight = dp(18) }
            }
            view.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            return object : RecyclerView.ViewHolder(view) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            holder.itemView.translationX = 0f
            when (val row = rows[position]) {
                is Row.App -> (holder.itemView as AppRow).bind(row.app, row.favourite)
                is Row.Note -> (holder.itemView as NoteRow).bind(row.note)
                is Row.Header -> (holder.itemView as TextView).text = row.section
                Row.Gap, Row.Clock -> Unit
            }
        }
    }

    private inner class AppRow : LinearLayout(context) {
        private val icon = ImageView(context)
        private val label = text(18f, FG).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        private var app: LaunchableApp? = null
        private var downX = 0f
        private var downY = 0f
        private var claimed = false

        private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                app?.let { host?.launchFromList(it, this@AppRow) }
                return true
            }
            override fun onLongPress(e: MotionEvent) {
                if (!claimed) app?.let { host?.showListAppMenu(this@AppRow, it) }
            }
        })

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(7), dp(6), dp(7))
            addView(icon, LayoutParams(dp(36), dp(36)))
            addView(label, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(14) })
            setOnTouchListener { _, ev ->
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { downX = ev.rawX; downY = ev.rawY; claimed = false }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = ev.rawX - downX
                        // A rightward drag is ours (shortcuts), not the pager's.
                        if (!claimed && dx > slop / 2 && dx > abs(ev.rawY - downY)) {
                            claimed = true
                            parent.requestDisallowInterceptTouchEvent(true)
                            // No long-press menu once the swipe is ours.
                            val cancel = MotionEvent.obtain(ev).apply { action = MotionEvent.ACTION_CANCEL }
                            gestures.onTouchEvent(cancel)
                            cancel.recycle()
                        }
                        if (claimed) translationX = (dx * 0.5f).coerceAtMost(dp(SWIPE_DP).toFloat())
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (claimed) {
                        val far = ev.rawX - downX > dp(SWIPE_DP)
                        animate().translationX(0f).setDuration(ANIM_MS).start()
                        if (far && ev.actionMasked == MotionEvent.ACTION_UP) app?.let { host?.showListShortcuts(this, it) }
                        claimed = false
                        return@setOnTouchListener true
                    }
                }
                if (claimed) true else gestures.onTouchEvent(ev)
            }
        }

        fun bind(app: LaunchableApp, favourite: Boolean) {
            this.app = app
            icon.setImageDrawable(host?.appIcon(app))
            label.text = app.label
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (favourite) 22f else 18f)
            icon.layoutParams = (icon.layoutParams as LayoutParams).apply {
                width = dp(if (favourite) 42 else 34); height = width
            }
        }
    }

    private inner class NoteRow : LinearLayout(context) {
        private val title = text(13f, FG).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END; typeface = Typeface.DEFAULT_BOLD }
        private val body = text(12f, SUB).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }
        private var note: NotificationPreview? = null
        private var downX = 0f
        private var downY = 0f
        private var claimed = false

        init {
            orientation = VERTICAL
            setPadding(dp(56), dp(1), dp(6), dp(6))
            addView(title)
            addView(body)
            setOnTouchListener { _, ev ->
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { downX = ev.rawX; downY = ev.rawY; claimed = false; return@setOnTouchListener true }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = ev.rawX - downX
                        // A leftward drag dismisses the notification.
                        if (!claimed && dx < -slop / 2 && -dx > abs(ev.rawY - downY)) {
                            claimed = true
                            parent.requestDisallowInterceptTouchEvent(true)
                        }
                        if (claimed) { translationX = dx; alpha = 1f + dx / width }
                    }
                    MotionEvent.ACTION_UP -> {
                        val dx = ev.rawX - downX
                        if (claimed && -dx > width / 3) {
                            note?.key?.let(SupernovaNotificationListener::dismiss)
                            animate().translationX(-width.toFloat()).alpha(0f).setDuration(ANIM_MS).start()
                        } else if (claimed) {
                            animate().translationX(0f).alpha(1f).setDuration(ANIM_MS).start()
                        } else if (abs(dx) < slop && abs(ev.rawY - downY) < slop) {
                            note?.contentIntent?.let { runCatching { com.supernova.launcher.ui.PendingStarts.send(it) } }
                        }
                        claimed = false
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        animate().translationX(0f).alpha(1f).setDuration(ANIM_MS).start(); claimed = false
                    }
                }
                true
            }
        }

        fun bind(note: NotificationPreview) {
            this.note = note
            alpha = 1f
            title.text = note.title
            body.text = note.text
            body.visibility = if (note.text.isBlank()) GONE else VISIBLE
        }
    }

    // ---- scrubber: ★ (favourites) then the sections present

    private inner class Scrubber : View(context) {
        var labels: List<String> = emptyList()
        private var active = -1
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

        init {
            setOnTouchListener { _, ev -> scrub(ev); true }
        }

        private fun indexAt(y: Float): Int {
            if (labels.isEmpty()) return -1
            val top = paddingTopOf()
            val step = stepOf()
            return ((y - top) / step).toInt().coerceIn(0, labels.size - 1)
        }

        private fun paddingTopOf() = (height - stepOf() * labels.size) / 2f
        private fun stepOf() = minOf(dp(STEP_DP).toFloat(), height / labels.size.coerceAtLeast(1).toFloat())

        private fun scrub(ev: MotionEvent) {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    parent.requestDisallowInterceptTouchEvent(true)
                    val i = indexAt(ev.y)
                    if (i != active) {
                        active = i
                        val label = labels.getOrNull(i) ?: return
                        val pos = if (label == STAR) 0 else sectionAt[label] ?: return
                        (recycler.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(pos, 0)
                        bubble.text = label
                        performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                        invalidate()
                    }
                    bubble.visibility = VISIBLE
                    bubble.translationY = (ev.y - dp(32)).coerceIn(0f, (height - dp(64)).toFloat())
                    wave(ev.y)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    active = -1
                    bubble.visibility = GONE
                    invalidate()
                    for (i in 0 until recycler.childCount) {
                        recycler.getChildAt(i).animate().translationX(0f).setDuration(ANIM_MS).start()
                    }
                }
            }
        }

        // Rows near the finger bow out to the left, like a wave under the thumb.
        private fun wave(y: Float) {
            val sigma = dp(WAVE_SIGMA_DP).toFloat()
            val amp = dp(WAVE_DP).toFloat()
            for (i in 0 until recycler.childCount) {
                val child = recycler.getChildAt(i)
                val center = recycler.top + child.top + child.height / 2f
                val d = (center - y) / sigma
                child.translationX = -amp * exp(-d * d)
            }
        }

        override fun onDraw(canvas: Canvas) {
            if (labels.isEmpty()) return
            val step = stepOf()
            val top = paddingTopOf()
            labels.forEachIndexed { i, label ->
                val on = i == active
                paint.textSize = if (on) step * 0.9f else step * 0.62f
                paint.color = if (on) ACCENT else SUB
                paint.typeface = if (on) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                paint.setShadowLayer(dp(2).toFloat(), 0f, 0f, SHADOW)
                val cy = top + step * (i + 0.5f)
                canvas.drawText(label, width / 2f, cy - (paint.ascent() + paint.descent()) / 2, paint)
            }
        }
    }

    private fun text(sp: Float, color: Int) = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
        setShadowLayer(dp(3).toFloat(), 0f, dp(1).toFloat(), SHADOW)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        const val KIND = "jp-list"
        private const val STAR = "★"
        private const val CLOCK_DP = 210
        private const val MAX_NOTES = 2
        private const val SCRUB_DP = 30
        private const val STEP_DP = 22
        private const val SWIPE_DP = 72
        private const val WAVE_DP = 26
        private const val WAVE_SIGMA_DP = 70
        private const val ANIM_MS = 180L
        private val FG = 0xFFF2F2EE.toInt()
        private val SUB = 0xFFCFCFCA.toInt()
        private val ACCENT = 0xFFF4B3A6.toInt()
        private val SHADOW = 0x99000000.toInt()
    }
}

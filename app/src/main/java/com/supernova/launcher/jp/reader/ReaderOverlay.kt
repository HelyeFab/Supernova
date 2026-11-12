package com.supernova.launcher.jp.reader

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.supernova.launcher.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.abs

/** Explicit capture only. No accessibility node scraping or background screen monitoring. */
class ReaderOverlay(private val service: AccessibilityService) {
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val prefs = ReaderPrefs(service)
    private var manager: WindowManager? = null
    private var button: View? = null
    private var selector: RegionView? = null
    private var busy = false
    private var destroyed = false
    // Double knock on the back of the phone opens the region selector (the default way in).
    private val displays = service.getSystemService(DisplayManager::class.java)
    private val knockLog = BackTapLog(service)
    private val backTap = BackTapSensor(service,
        onDoubleTap = {
            val outcome = when { calibrating -> "dbl-test"; busy -> "dbl-busy"; else -> "DOUBLE" }
            knockLog.add(outcome, screen = screenName(), threshold = prefs.backTapThreshold)
            if (!calibrating) { buzz(); capture() }
        },
        onKnock = { peak -> knockLog.add("knock", peak, screenName(), prefs.backTapThreshold) })

    /** Which screen is on: the Honor's outer (folded) or inner (unfolded) display, by width. */
    private fun screenName(): String {
        val display = displays.displays.firstOrNull { it.state == Display.STATE_ON } ?: return "off"
        val metrics = android.util.DisplayMetrics().also { @Suppress("DEPRECATION") display.getRealMetrics(it) }
        val shortSide = minOf(metrics.widthPixels, metrics.heightPixels) / metrics.density
        return if (shortSide > 600) "unfolded" else "folded"
    }
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = refresh()
        override fun onDisplayRemoved(displayId: Int) = refresh()
        override fun onDisplayChanged(displayId: Int) { if (!busy) refresh() }
    }

    fun connect() {
        displays.registerDisplayListener(displayListener, handler)
        refresh()
    }

    fun refresh() {
        if (destroyed) return
        if (prefs.backTap && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) backTap.start(prefs.backTapThreshold)
        else backTap.stop()
        if (busy) return
        removeButton()
        if (!prefs.floating || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val display = displays.displays.firstOrNull { it.state == Display.STATE_ON } ?: return
        val context = service.createDisplayContext(display)
            .createWindowContext(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, null)
        manager = context.getSystemService(WindowManager::class.java)
        val density = context.resources.displayMetrics.density
        val size = (48 * density).toInt()
        val params = WindowManager.LayoutParams(size, size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.END
            x = (8 * density).toInt()
            y = (prefs.buttonY * density).toInt().coerceIn(0,
                (manager!!.currentWindowMetrics.bounds.height() - size * 2).coerceAtLeast(0))
        }
        val view = FloatingReadButton(context).apply {
            text = "読"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(0xFFF4EBDC.toInt())
            contentDescription = "Read Japanese on this screen. Drag to move; hold for settings."
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xF02C4944.toInt())
                setStroke((density * 1).toInt(), 0xFFBFC8AE.toInt())
            }
            setOnClickListener { capture() }
            setOnLongClickListener { openSettings(); true }
        }
        var downY = 0f
        var downX = 0f
        var originY = 0
        var started = 0L
        var moved = false
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downY = event.rawY; downX = event.rawX; originY = params.y
                    started = event.eventTime; moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (abs(event.rawY - downY) > 8 * density || abs(event.rawX - downX) > 8 * density) moved = true
                    if (moved) {
                        params.y = (originY + event.rawY - downY).toInt().coerceIn(0,
                            (manager!!.currentWindowMetrics.bounds.height() - size * 2).coerceAtLeast(0))
                        runCatching { manager?.updateViewLayout(view, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) prefs.buttonY = (params.y / density).toInt()
                    else if (event.eventTime - started > 600) view.performLongClick() else view.performClick()
                    true
                }
                else -> true
            }
        }
        runCatching { manager?.addView(view, params); button = view }
            .onFailure { message("Could not show the reading button. Re-enable Supernova accessibility.") }
    }

    fun capture() {
        if (destroyed || busy) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            message("Screen reading needs Android 11 or later. You can still share selected Japanese to the reader.")
            return
        }
        val display = displays.displays.firstOrNull { it.state == Display.STATE_ON }
        if (display == null) { message("Turn on the screen before reading."); return }
        busy = true
        removeButton()
        // Wait for SurfaceFlinger to remove our button before capturing the other app.
        handler.postDelayed({ if (!destroyed) screenshot(display) }, 180)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun screenshot(display: Display) {
        try {
            service.takeScreenshot(display.displayId, service.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        val bitmap = try {
                            Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)?.let { hardware ->
                                try { hardware.copy(Bitmap.Config.ARGB_8888, false) } finally { hardware.recycle() }
                            }
                        } finally { result.hardwareBuffer.close() }
                        if (destroyed) { bitmap?.recycle(); return }
                        if (bitmap == null) { failed("Could not capture this screen."); return }
                        runCatching { showRegion(display, bitmap) }.onFailure {
                            if (!bitmap.isRecycled) bitmap.recycle()
                            failed("The display changed during capture. Try again.")
                        }
                    }
                    override fun onFailure(errorCode: Int) {
                        failed(if (errorCode == 6) "This app protects its screen from capture. Try sharing selected text."
                            else "Screen capture failed ($errorCode). Try again with the screen unlocked.")
                    }
                })
        } catch (_: Exception) { failed("Screen capture is unavailable. Re-enable Supernova accessibility.") }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun showRegion(display: Display, bitmap: Bitmap) {
        val context = service.createDisplayContext(display)
            .createWindowContext(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, null)
        manager = context.getSystemService(WindowManager::class.java)
        val view = RegionView(context, bitmap, { rect ->
            val crop = Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width(), rect.height())
                .let { if (it === bitmap) it.copy(Bitmap.Config.ARGB_8888, false) else it }
            removeSelector()
            scope.launch {
                try {
                    val id = UUID.randomUUID().toString()
                    withContext(Dispatchers.IO) {
                        val dir = File(service.cacheDir, "reader").apply { mkdirs() }
                        // Captures are ephemeral, never part of launcher backups.
                        dir.listFiles()?.filter { it.lastModified() < System.currentTimeMillis() - 3_600_000 }
                            ?.forEach { it.delete() }
                        File(dir, "$id.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    }
                    service.startActivity(Intent(service, ReaderActivity::class.java)
                        .putExtra(ReaderActivity.EXTRA_CAPTURE, id)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) { message("Could not open this capture. Please try again.") }
                finally { crop.recycle(); busy = false; refresh() }
            }
        }, { removeSelector(); busy = false; refresh() })
        selector = view
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT).apply {
            // Capture coordinates cover system bars too. Don't inset/stretch the frozen image.
            setFitInsetsTypes(0)
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        runCatching { manager?.addView(view, params); view.requestFocus() }
            .onFailure { removeSelector(); failed("Could not open region selection.") }
    }

    private fun openSettings() {
        service.startActivity(Intent(service, ReaderActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    private fun buzz() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            service.getSystemService(android.os.VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") service.getSystemService(android.os.Vibrator::class.java)
        runCatching { vibrator?.vibrate(android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_CLICK)) }
    }
    private fun message(text: String) { if (!destroyed) Toast.makeText(service, text, Toast.LENGTH_LONG).show() }
    private fun failed(text: String) { busy = false; message(text); refresh() }
    private fun removeButton() { button?.let { runCatching { manager?.removeViewImmediate(it) } }; button = null }
    private fun removeSelector() {
        selector?.let { runCatching { manager?.removeViewImmediate(it) }; it.release() }
        selector = null
    }
    companion object {
        /** True while Reader settings' knock test is open: knocks there are tests, not captures. */
        @Volatile var calibrating = false
    }

    fun destroy() {
        destroyed = true
        handler.removeCallbacksAndMessages(null)
        displays.unregisterDisplayListener(displayListener)
        backTap.stop()
        knockLog.close()
        removeButton(); removeSelector(); scope.cancel()
    }
}

/** Frozen source avoids capturing a different frame after the user selects a region. */
// Built only by the capture service with a bitmap; never inflated from XML.
@SuppressLint("ViewConstructor")
private class RegionView(
    context: Context,
    private val bitmap: Bitmap,
    private val onRead: (Rect) -> Unit,
    private val onCancel: () -> Unit
) : android.widget.FrameLayout(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val destination = Rect()
    private val selection = RectF()
    private var startX = 0f
    private var startY = 0f
    private var dragging = false
    // What the current drag does: draw a new box, move the box, or pull some of its edges.
    private enum class Mode { DRAW, MOVE, RESIZE }
    private var mode = Mode.DRAW
    private val origin = RectF()
    private var pullLeft = false
    private var pullTop = false
    private var pullRight = false
    private var pullBottom = false
    private var submitted = false
    private val density = resources.displayMetrics.density
    private val read: TextView

    init {
        setWillNotDraw(false)
        isFocusableInTouchMode = true
        // Same sumi sheet and pills as the reader, floating clear of the system bars.
        val help = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            background = ReaderUi.rounded(ReaderUi.SHEET, dp(22).toFloat(), dp(1), ReaderUi.LINE)
            setPadding(dp(18), dp(12), dp(18), dp(12))
            addView(ReaderUi.text(context, 16f, ReaderUi.INK, context.getString(R.string.jp_reader_region_help)).apply {
                typeface = ReaderUi.MEDIUM
            })
            addView(ReaderUi.text(context, 13f, ReaderUi.MUTED, context.getString(R.string.jp_reader_region_hint)))
        }
        addView(help, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.TOP).apply {
            topMargin = dp(52); leftMargin = dp(16); rightMargin = dp(16)
        })
        val actions = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ReaderUi.rounded(ReaderUi.SHEET, dp(30).toFloat(), dp(1), ReaderUi.LINE)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        fun gap() = View(context).apply { layoutParams = android.widget.LinearLayout.LayoutParams(dp(8), 1) }
        actions.addView(ReaderUi.pill(context, "Cancel", ReaderUi.Pill.QUIET) { onCancel() },
            android.widget.LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        actions.addView(gap())
        actions.addView(ReaderUi.pill(context, "Whole screen") { selection.set(0f, 0f, width.toFloat(), height.toFloat()); submit() },
            android.widget.LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        actions.addView(gap())
        read = ReaderUi.pill(context, "Read", ReaderUi.Pill.PRIMARY) { submit() }.apply { isEnabled = false; alpha = 0.4f }
        actions.addView(read, android.widget.LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(actions, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
            bottomMargin = dp(36); leftMargin = dp(16); rightMargin = dp(16)
        })
        setOnKeyListener { _, key, event ->
            if (key == android.view.KeyEvent.KEYCODE_BACK && event.action == android.view.KeyEvent.ACTION_UP) {
                onCancel(); true
            } else false
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        destination.set(0, 0, width, height)
        canvas.drawBitmap(bitmap, null, destination, paint)
        paint.color = 0x66000000
        canvas.drawRect(0f, 0f, width.toFloat(), selection.top, paint)
        canvas.drawRect(0f, selection.bottom, width.toFloat(), height.toFloat(), paint)
        canvas.drawRect(0f, selection.top, selection.left, selection.bottom, paint)
        canvas.drawRect(selection.right, selection.top, width.toFloat(), selection.bottom, paint)
        if (!selection.isEmpty) {
            paint.color = ReaderUi.ACCENT; paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(2).toFloat()
            canvas.drawRoundRect(selection, dp(4).toFloat(), dp(4).toFloat(), paint)
            paint.style = Paint.Style.FILL
            // Corner handles: hints that the box can be pulled and moved.
            val r = dp(7).toFloat()
            canvas.drawCircle(selection.left, selection.top, r, paint)
            canvas.drawCircle(selection.right, selection.top, r, paint)
            canvas.drawCircle(selection.left, selection.bottom, r, paint)
            canvas.drawCircle(selection.right, selection.bottom, r, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (submitted) return true
        val w = width.toFloat(); val h = height.toFloat()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x; startY = event.y; dragging = true
                origin.set(selection)
                mode = pickMode(event.x, event.y)
            }
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> if (dragging) {
                val dx = event.x - startX; val dy = event.y - startY
                when (mode) {
                    Mode.DRAW -> selection.set(minOf(startX, event.x).coerceIn(0f, w), minOf(startY, event.y).coerceIn(0f, h),
                        maxOf(startX, event.x).coerceIn(0f, w), maxOf(startY, event.y).coerceIn(0f, h))
                    // The whole box follows the finger and stops flush against any screen edge.
                    Mode.MOVE -> {
                        val ox = dx.coerceIn(-origin.left, w - origin.right)
                        val oy = dy.coerceIn(-origin.top, h - origin.bottom)
                        selection.set(origin.left + ox, origin.top + oy, origin.right + ox, origin.bottom + oy)
                    }
                    // Pulling an edge or corner outward reaches the very edge of the screen, which a
                    // fresh drag can't (MagicOS keeps edge swipes for its back gesture).
                    Mode.RESIZE -> {
                        val min = dp(24).toFloat()
                        val left = if (pullLeft) (origin.left + dx).coerceIn(0f, origin.right - min) else origin.left
                        val top = if (pullTop) (origin.top + dy).coerceIn(0f, origin.bottom - min) else origin.top
                        val right = if (pullRight) (origin.right + dx).coerceIn(origin.left + min, w) else origin.right
                        val bottom = if (pullBottom) (origin.bottom + dy).coerceIn(origin.top + min, h) else origin.bottom
                        selection.set(left, top, right, bottom)
                    }
                }
                read.isEnabled = selection.width() > dp(8) && selection.height() > dp(8)
                read.alpha = if (read.isEnabled) 1f else 0.4f
                if (event.actionMasked == MotionEvent.ACTION_UP) { dragging = false; performClick() }
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> dragging = false
        }
        return true
    }

    /** Near an edge or corner of the box resizes it, inside moves it, anywhere else draws anew. */
    private fun pickMode(x: Float, y: Float): Mode {
        if (selection.width() < dp(8) || selection.height() < dp(8)) return Mode.DRAW
        val grab = dp(28).toFloat()
        val withinY = y in selection.top - grab..selection.bottom + grab
        val withinX = x in selection.left - grab..selection.right + grab
        pullLeft = withinY && abs(x - selection.left) < grab
        pullRight = withinY && !pullLeft && abs(x - selection.right) < grab
        pullTop = withinX && abs(y - selection.top) < grab
        pullBottom = withinX && !pullTop && abs(y - selection.bottom) < grab
        return when {
            pullLeft || pullRight || pullTop || pullBottom -> Mode.RESIZE
            selection.contains(x, y) -> Mode.MOVE
            else -> Mode.DRAW
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Ask for the edges back from the system back gesture (Android grants up to 200 dp
        // per edge); the middle of each edge, where the text usually is.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val strip = dp(40); val span = dp(200).coerceAtMost(height)
            val top = (height - span) / 2
            systemGestureExclusionRects = listOf(Rect(0, top, strip, top + span), Rect(width - strip, top, width, top + span))
        }
    }

    override fun performClick(): Boolean { super.performClick(); return true }
    private fun submit() {
        if (submitted || selection.isEmpty || width == 0 || height == 0) return
        submitted = true
        val left = (selection.left * bitmap.width / width).toInt().coerceIn(0, bitmap.width - 1)
        val top = (selection.top * bitmap.height / height).toInt().coerceIn(0, bitmap.height - 1)
        onRead(Rect(left, top, (selection.right * bitmap.width / width).toInt().coerceIn(left + 1, bitmap.width),
            (selection.bottom * bitmap.height / height).toInt().coerceIn(top + 1, bitmap.height)))
    }
    private fun dp(value: Int) = (value * density).toInt()
    fun release() { bitmap.recycle() }
}

private class FloatingReadButton(context: Context) : androidx.appcompat.widget.AppCompatTextView(context) {
    override fun performClick(): Boolean { super.performClick(); return true }
}

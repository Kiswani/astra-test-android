package dev.astratest

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.sqrt

private const val COBALT = 0xFF2F55D4.toInt()
private const val MIDNIGHT = 0xFF0E1A3D.toInt()

private fun Context.dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()
private fun pill(color: Int, radius: Float) = GradientDrawable().apply { setColor(color); cornerRadius = radius }

internal object Ui {
    private const val BUTTON_TAG = "astra-test-button"
    private var state: SheetState? = null

    private class SheetState(val screenshot: ByteArray?) { var text = ""; var picked: Picked? = null }

    @SuppressLint("ClickableViewAccessibility")
    fun attachButton(a: Activity) {
        val root = a.window?.decorView as? ViewGroup ?: return
        if (root.findViewWithTag<View>(BUTTON_TAG) != null) return
        val b = TextView(a).apply {
            tag = BUTTON_TAG
            text = "✦  Report"
            setTextColor(Color.WHITE); textSize = 14f; typeface = android.graphics.Typeface.DEFAULT_BOLD
            background = pill(MIDNIGHT, a.dp(20).toFloat())
            setPadding(a.dp(16), a.dp(10), a.dp(16), a.dp(10))
            elevation = a.dp(6).toFloat()
            contentDescription = "Report an issue"
        }
        val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END).apply { setMargins(0, 0, a.dp(16), a.dp(48)) }
        var dx = 0f; var dy = 0f; var moved = false
        b.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { dx = v.x - e.rawX; dy = v.y - e.rawY; moved = false }
                MotionEvent.ACTION_MOVE -> { if (moved || kotlin.math.abs(e.rawX + dx - v.x) > 8) { moved = true; v.x = e.rawX + dx; v.y = e.rawY + dy } }
                MotionEvent.ACTION_UP -> if (!moved) showReport(a)
            }
            true
        }
        root.addView(b, lp)
    }

    fun showReport(a: Activity) {
        if (a.isFinishing) return
        if (state == null) {
            Screenshot.capture(a) { shot -> state = SheetState(shot); openSheet(a) }
        } else openSheet(a)
    }

    private fun openSheet(a: Activity) {
        val s = state ?: return
        val d = Dialog(a)
        val pad = a.dp(20)
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad); background = pill(Color.WHITE, a.dp(18).toFloat()) }
        fun label(t: String) = TextView(a).apply { text = t; textSize = 12f; setTextColor(0xFF646B78.toInt()); setPadding(0, a.dp(12), 0, a.dp(6)) }
        val title = TextView(a).apply { text = "Report an issue"; textSize = 18f; setTextColor(0xFF0F1115.toInt()); typeface = android.graphics.Typeface.DEFAULT_BOLD }
        col.addView(title)
        col.addView(label("What's wrong? Point at it"))
        val pickRow = TextView(a).apply {
            text = s.picked?.selector?.let { "$it   ·   Change" } ?: "Nothing selected   ·   Point at it"
            textSize = 13f; setTextColor(if (s.picked == null) 0xFF646B78.toInt() else COBALT)
            background = pill(0x142F55D4, a.dp(8).toFloat()); setPadding(a.dp(12), a.dp(10), a.dp(12), a.dp(10))
            setOnClickListener { s.text = input().text.toString(); d.dismiss(); pick(a) }
        }
        col.addView(pickRow)
        col.addView(label("What happened?"))
        val field = EditText(a).apply {
            setText(s.text); hint = "Say it in your own words"; minLines = 3; gravity = Gravity.TOP
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            id = View.generateViewId()
        }
        col.addView(field)
        val attached = TextView(a).apply {
            val n = Capture.network.all().size
            text = "Attached: " + listOfNotNull(if (s.screenshot != null) "Screenshot" else null, if (n > 0) "$n requests" else null, "app log", "device details").joinToString(" · ")
            textSize = 12f; setTextColor(0xFF646B78.toInt()); setPadding(0, a.dp(10), 0, a.dp(12))
        }
        col.addView(attached)
        val status = TextView(a).apply { textSize = 13f; setTextColor(0xFFB42318.toInt()); visibility = View.GONE }
        col.addView(status)
        val send = Button(a).apply {
            text = "Send report"; setTextColor(Color.WHITE); isAllCaps = false; background = pill(COBALT, a.dp(10).toFloat())
            setOnClickListener {
                val t = field.text.toString().trim()
                if (t.length < 3) { status.text = "Say what happened first."; status.visibility = View.VISIBLE; return@setOnClickListener }
                isEnabled = false; text = "Sending…"
                Sender.send(t, s.picked, s.screenshot) { r ->
                    r.onSuccess { sent ->
                        state = null
                        col.removeAllViews()
                        col.addView(TextView(a).apply { text = "✓  Sent as ${sent.ref}"; textSize = 17f; setTextColor(0xFF0E7C66.toInt()); typeface = android.graphics.Typeface.DEFAULT_BOLD })
                        col.addView(TextView(a).apply { text = "Thanks. We'll let you know when it's fixed."; textSize = 14f; setTextColor(0xFF5B6270.toInt()); setPadding(0, a.dp(8), 0, 0) })
                        AstraTest.main.postDelayed({ runCatching { d.dismiss() } }, 1800)
                    }.onFailure { e -> isEnabled = true; text = "Send report"; status.text = e.message; status.visibility = View.VISIBLE }
                }
            }
        }
        col.addView(send, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, a.dp(48)))
        val cancel = TextView(a).apply { text = "Cancel"; textSize = 14f; setTextColor(0xFF5B6270.toInt()); gravity = Gravity.CENTER; setPadding(0, a.dp(14), 0, 0); setOnClickListener { state = null; d.dismiss() } }
        col.addView(cancel)
        inputRef = field
        d.setContentView(col)
        d.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout((a.resources.displayMetrics.widthPixels * 0.92).toInt().coerceAtMost(a.dp(460)), ViewGroup.LayoutParams.WRAP_CONTENT)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        d.setOnCancelListener { state = null }
        val btn = (a.window?.decorView as? ViewGroup)?.findViewWithTag<View>(BUTTON_TAG)
        btn?.visibility = View.INVISIBLE
        d.setOnDismissListener { btn?.visibility = View.VISIBLE }
        d.show()
    }

    private var inputRef: EditText? = null
    private fun input(): EditText = inputRef!!

    /** Let the tester tap the thing that's wrong, then bring the sheet back with it selected. */
    @SuppressLint("ClickableViewAccessibility")
    private fun pick(a: Activity) {
        val root = a.window?.decorView as? ViewGroup ?: return
        val layer = object : View(a) {
            val box = RectF(); var showBox = false
            val dim = Paint().apply { color = 0x1F000000 }
            val line = Paint().apply { color = COBALT; style = Paint.Style.STROKE; strokeWidth = a.dp(2).toFloat() }
            val tip = Paint().apply { color = COBALT }
            val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = a.dp(15).toFloat(); isFakeBoldText = true; textAlign = Paint.Align.CENTER }
            override fun onDraw(c: Canvas) {
                c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dim)
                val w = ink.measureText("Tap what's wrong") + a.dp(32)
                val r = RectF(width / 2f - w / 2, a.dp(56).toFloat(), width / 2f + w / 2, a.dp(92).toFloat())
                c.drawRoundRect(r, a.dp(18).toFloat(), a.dp(18).toFloat(), tip)
                c.drawText("Tap what's wrong", width / 2f, r.centerY() + a.dp(5), ink)
                if (showBox) c.drawRoundRect(box, a.dp(6).toFloat(), a.dp(6).toFloat(), line)
            }
        }
        layer.setOnTouchListener { v, e ->
            if (e.actionMasked == MotionEvent.ACTION_UP) {
                v.setOnTouchListener(null)
                val hit = Element.find(root, e.rawX.toInt(), e.rawY.toInt(), exclude = v)
                if (hit != null) {
                    state?.picked = Element.describe(hit)
                    val loc = IntArray(2); hit.getLocationOnScreen(loc); val me = IntArray(2); v.getLocationOnScreen(me)
                    layer.box.set((loc[0] - me[0]).toFloat(), (loc[1] - me[1]).toFloat(), (loc[0] - me[0] + hit.width).toFloat(), (loc[1] - me[1] + hit.height).toFloat())
                    layer.showBox = true; layer.invalidate()
                }
                AstraTest.main.postDelayed({ root.removeView(layer); openSheet(a) }, 450)
            }
            true
        }
        root.addView(layer, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }
}

internal object Element {
    fun find(v: View, x: Int, y: Int, exclude: View): View? {
        if (v === exclude || v.visibility != View.VISIBLE || v.tag == "astra-test-button") return null
        val r = Rect(); if (!v.getGlobalVisibleRect(r) || !r.contains(x, y)) return null
        if (v is ViewGroup) for (i in v.childCount - 1 downTo 0) find(v.getChildAt(i), x, y, exclude)?.let { return it }
        return v
    }
    fun describe(v: View): Picked {
        var name = v.javaClass.simpleName.ifEmpty { "View" }
        if (v.id != View.NO_ID) runCatching { name += "#" + v.resources.getResourceEntryName(v.id) }
        return Picked(name, text(v, 0))
    }
    private fun text(v: View, depth: Int): String? {
        if (v is EditText && (v.inputType and InputType.TYPE_TEXT_VARIATION_PASSWORD) != 0) return null
        if (v is TextView && !v.text.isNullOrBlank()) return v.text.toString()
        v.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { return it }
        if (v is ViewGroup && depth < 3) for (i in 0 until v.childCount) text(v.getChildAt(i), depth + 1)?.let { return it }
        return null
    }
}

internal object Shake : SensorEventListener {
    private var sm: SensorManager? = null
    private var last = 0L
    fun start(a: Activity) {
        if (sm != null) return
        sm = (a.getSystemService(Context.SENSOR_SERVICE) as? SensorManager)?.also { m ->
            m.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { m.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        }
    }
    fun stop() { sm?.unregisterListener(this); sm = null }
    override fun onSensorChanged(e: SensorEvent) {
        val (x, y, z) = Triple(e.values[0], e.values[1], e.values[2])
        val g = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
        val now = System.currentTimeMillis()
        if (g > 2.7f && now - last > 2000) { last = now; AstraTest.showReport() }
    }
    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
}

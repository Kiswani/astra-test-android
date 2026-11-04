package dev.astratest

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import java.io.ByteArrayOutputStream

internal object Screenshot {
    /** Captures the activity's window (PixelCopy on API 26+, so maps and video show), with private views blacked out. */
    fun capture(a: Activity, done: (ByteArray?) -> Unit) {
        val root = a.window?.decorView ?: return done(null)
        if (root.width == 0 || root.height == 0) return done(null)
        val scale = (1080f / maxOf(root.width, root.height)).coerceAtMost(1f).let { if (root.width > root.height) it else (1600f / root.height).coerceAtMost(1f) }
        val full = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        // Our own Report button stays out of the picture.
        val button = root.findViewWithTag<View>("astra-test-button")
        val wasVisible = button?.visibility == View.VISIBLE
        if (wasVisible) button?.visibility = View.INVISIBLE
        val finish = { ok: Boolean ->
            if (!ok) root.draw(Canvas(full))
            if (wasVisible) button?.visibility = View.VISIBLE
            mask(root, full)
            val out = if (scale < 1f) Bitmap.createScaledBitmap(full, (full.width * scale).toInt(), (full.height * scale).toInt(), true) else full
            val bytes = ByteArrayOutputStream().also { out.compress(Bitmap.CompressFormat.JPEG, 70, it) }.toByteArray()
            done(bytes)
        }
        val copy = {
            if (Build.VERSION.SDK_INT >= 26) {
                runCatching { PixelCopy.request(a.window, full, { r -> finish(r == PixelCopy.SUCCESS) }, Handler(Looper.getMainLooper())) }
                    .onFailure { finish(false) }
            } else finish(false)
        }
        // Give the hidden button a frame to disappear before copying the screen.
        if (wasVisible) root.postOnAnimation { root.postOnAnimation { copy() } } else copy()
    }

    private fun mask(root: View, bmp: Bitmap) {
        val c = Canvas(bmp); val p = Paint().apply { color = Color.BLACK }
        val origin = IntArray(2); root.getLocationOnScreen(origin)
        fun walk(v: View) {
            if (v.visibility != View.VISIBLE) return
            val secret = v in AstraTest.privateViews || (v is EditText && isPassword(v.inputType))
            if (secret) {
                val l = IntArray(2); v.getLocationOnScreen(l)
                c.drawRect((l[0] - origin[0]).toFloat(), (l[1] - origin[1]).toFloat(), (l[0] - origin[0] + v.width).toFloat(), (l[1] - origin[1] + v.height).toFloat(), p)
                return
            }
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(root)
    }

    private fun isPassword(t: Int): Boolean {
        val v = t and InputType.TYPE_MASK_VARIATION
        return v == InputType.TYPE_TEXT_VARIATION_PASSWORD || v == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            v == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD || v == InputType.TYPE_NUMBER_VARIATION_PASSWORD
    }
}

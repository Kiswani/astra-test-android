package dev.astratest

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap

/**
 * Astra Test for Android.
 *
 * ```kotlin
 * class App : Application() {
 *   override fun onCreate() { super.onCreate(); AstraTest.start(this, "pk_test_…") }
 * }
 * ```
 * Testers press the Report button or shake the phone, point at what's wrong and say it in their own words.
 * A screenshot, recent requests, the app's log and device details are attached.
 */
object AstraTest {
    /** This SDK's version (set by sdks/publish.sh). */
    const val VERSION = "0.1.1"

    data class Options(
        /** Your app's version; defaults to versionName. */
        val appVersion: String? = null,
        /** Your build number; defaults to versionCode. */
        val build: String? = null,
        /** Show the floating Report button. Defaults to true for pk_test_ keys, false for pk_live_. */
        val showButton: Boolean? = null,
        /** Open the report sheet when the phone is shaken. */
        val shakeToReport: Boolean = true,
        /** Attach this app's own logcat lines to reports. */
        val captureLogs: Boolean = true,
        /** Where reports are sent. */
        val host: String = "https://astratest.dev",
    )

    data class SentReport(val ref: String, val url: String?, val title: String?)

    internal var key: String? = null
    internal var options = Options()
    internal var reporter: Map<String, String> = emptyMap()
    internal var testCase: String? = null
    internal var screenName: String? = null
    internal var app: Application? = null
    internal var current: WeakReference<Activity>? = null
    internal val privateViews: MutableSet<View> = Collections.newSetFromMap(WeakHashMap())
    internal val main = Handler(Looper.getMainLooper())
    private var started = false

    /** Starts Astra Test. Call once from Application.onCreate. */
    @JvmStatic @JvmOverloads
    fun start(application: Application, key: String, options: Options = Options()) {
        if (started) return
        if (!Regex("^pk_(test|live)_[0-9a-f]{24}$").matches(key)) {
            Log.w("AstraTest", "That isn't a project key. Copy it from SDK setup in Astra Test (pk_test_… or pk_live_…).")
            return
        }
        started = true
        this.key = key; this.options = options; this.app = application
        val showButton = options.showButton ?: key.startsWith("pk_test_")
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(a: Activity) {
                current = WeakReference(a)
                if (showButton) Ui.attachButton(a)
                if (options.shakeToReport) Shake.start(a)
            }
            override fun onActivityPaused(a: Activity) { Shake.stop() }
            override fun onActivityCreated(a: Activity, b: Bundle?) {}
            override fun onActivityStarted(a: Activity) {}
            override fun onActivityStopped(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
        Sender.announceBuild(application)
    }

    /** Says who is testing, so reports show their name and they can be asked if a fix works. */
    @JvmStatic @JvmOverloads
    fun identify(id: String? = null, email: String? = null, name: String? = null) {
        reporter = buildMap { id?.let { put("id", it) }; email?.let { put("email", it) }; name?.let { put("name", it) } }
    }

    /** The test case being run, such as "UAT-02". */
    @JvmStatic fun setTestCase(ref: String?) { testCase = ref }

    /** The screen the user is on, such as "Checkout". Defaults to the activity's name. */
    @JvmStatic fun setScreen(name: String?) { screenName = name }

    /** Opens the report sheet, for example from your own "Report a problem" menu item. */
    @JvmStatic fun showReport() { main.post { current?.get()?.let { Ui.showReport(it) } } }

    /** Adds a line to the log attached to reports. */
    @JvmStatic @JvmOverloads
    fun log(message: String, level: String = "log") { Capture.log(level, message) }

    /** Blacks this view out in report screenshots. Password fields are always hidden. */
    @JvmStatic fun markPrivate(view: View) { privateViews.add(view) }

    /** Records OkHttp requests: add with `OkHttpClient.Builder().addInterceptor(AstraTest.okHttpInterceptor())`. */
    @JvmStatic fun okHttpInterceptor(): okhttp3.Interceptor = AstraInterceptor()

    /** Sends a report without the sheet, for example from your own feedback form. */
    @JvmStatic
    fun send(description: String, callback: (Result<SentReport>) -> Unit) {
        val a = current?.get()
        if (a == null) { Sender.send(description, null, null, callback); return }
        Screenshot.capture(a) { shot -> Sender.send(description, null, shot, callback) }
    }
}

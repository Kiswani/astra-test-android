package dev.astratest

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors

internal data class Picked(val selector: String, val text: String?)

internal object Device {
    fun info(ctx: Context): JSONObject {
        val dm = ctx.resources.displayMetrics
        val tablet = (ctx.resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE
        return JSONObject().apply {
            put("platform", "android")
            put("os", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            put("browser", "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}${if (tablet) " · tablet" else ""}")
            put("viewport", "${(dm.widthPixels / dm.density).toInt()} × ${(dm.heightPixels / dm.density).toInt()} dp")
            put("locale", Locale.getDefault().toLanguageTag())
            version(ctx)?.let { put("appVersion", it) }
            build(ctx)?.let { put("build", it) }
        }
    }
    fun version(ctx: Context): String? = AstraTest.options.appVersion ?: runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull()
    @Suppress("DEPRECATION")
    fun build(ctx: Context): String? = AstraTest.options.build ?: runCatching {
        val p = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) p.longVersionCode.toString() else p.versionCode.toString()
    }.getOrNull()
    fun appName(ctx: Context): String = runCatching { ctx.applicationInfo.loadLabel(ctx.packageManager).toString() }.getOrDefault("App")
    fun screen(a: Activity?): String? = AstraTest.screenName ?: a?.let { it.title?.toString()?.takeIf { t -> t.isNotBlank() && t != appName(it) } ?: it.javaClass.simpleName.removeSuffix("Activity") }
}

internal object Sender {
    private val io = Executors.newSingleThreadExecutor()

    fun announceBuild(app: Application) {
        val key = AstraTest.key ?: return
        val v = Device.version(app) ?: return
        post("/v1/builds", JSONObject().put("key", key).put("platform", "android").put("version", v).put("build", Device.build(app) ?: "").put("sdk", "android").put("sdkVersion", AstraTest.VERSION)) {}
    }

    fun payload(description: String, picked: Picked?, screenshot: ByteArray?): JSONObject? {
        val ctx = AstraTest.app ?: return null
        val a = AstraTest.current?.get()
        val screen = Device.screen(a)
        return JSONObject().apply {
            put("key", AstraTest.key)
            put("description", description)
            put("page", JSONObject().apply {
                put("url", "app://${Device.appName(ctx)}/${screen ?: ""}")
                put("title", screen ?: Device.appName(ctx))
                picked?.let { put("selector", it.selector); it.text?.let { t -> put("selectorText", t.take(120)) } }
            })
            put("device", Device.info(ctx))
            put("console", JSONArray(Capture.recentLogs().map { JSONObject().put("level", it.level).put("message", it.message).put("at", it.at) }))
            put("network", JSONArray(Capture.network.all().takeLast(60).map { JSONObject().put("method", it.method).put("url", it.url).put("status", it.status).put("durationMs", it.durationMs).put("at", it.at) }))
            if (AstraTest.reporter.isNotEmpty()) put("reporter", JSONObject(AstraTest.reporter as Map<*, *>))
            AstraTest.testCase?.let { put("testCaseRef", it) }
            screenshot?.let { put("screenshot", "data:image/jpeg;base64," + Base64.encodeToString(it, Base64.NO_WRAP)) }
        }
    }

    fun send(description: String, picked: Picked?, screenshot: ByteArray?, callback: (Result<AstraTest.SentReport>) -> Unit) {
        io.execute {
            val body = payload(description, picked, screenshot)
            if (body == null) { AstraTest.main.post { callback(Result.failure(IllegalStateException("Astra Test isn't started"))) }; return@execute }
            post("/v1/reports", body) { r ->
                val out = r.mapCatching { j ->
                    val ref = j.optString("ref").takeIf { it.isNotEmpty() } ?: error(j.optString("error", "Couldn't send the report"))
                    AstraTest.SentReport(ref, j.optString("url").ifEmpty { null }, j.optString("title").ifEmpty { null })
                }
                AstraTest.main.post { callback(out) }
            }
        }
    }

    fun post(path: String, body: JSONObject, done: (Result<JSONObject>) -> Unit) {
        io.execute {
            val r = runCatching {
                val c = URL(AstraTest.options.host.trimEnd('/') + path).openConnection() as HttpURLConnection
                c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 15000; c.readTimeout = 60000
                c.setRequestProperty("content-type", "application/json")
                c.outputStream.use { it.write(body.toString().toByteArray()) }
                val code = c.responseCode
                val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty()
                val j = runCatching { JSONObject(text) }.getOrDefault(JSONObject())
                if (code !in 200..299) error(j.optString("error", "Couldn't send the report ($code)"))
                j
            }
            done(r)
        }
    }
}

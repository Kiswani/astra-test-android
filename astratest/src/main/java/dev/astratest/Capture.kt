package dev.astratest

import android.os.Process
import okhttp3.Interceptor
import okhttp3.Response
import java.util.ArrayDeque

internal data class LogLine(val level: String, val message: String, val at: Long)
internal data class NetCall(val method: String, val url: String, val status: Int, val durationMs: Long, val at: Long)

internal class Ring<T>(private val cap: Int) {
    private val q = ArrayDeque<T>()
    @Synchronized fun push(x: T) { q.addLast(x); while (q.size > cap) q.removeFirst() }
    @Synchronized fun all(): List<T> = q.toList()
}

internal object Capture {
    val logs = Ring<LogLine>(200)
    val network = Ring<NetCall>(100)

    fun log(level: String, message: String) = logs.push(LogLine(level, message.take(2000), System.currentTimeMillis()))

    fun recordCall(method: String, url: String, status: Int, durationMs: Long) {
        val clean = url.substringBefore('?').substringBefore('#').take(500)
        network.push(NetCall(method, clean, status, durationMs, System.currentTimeMillis()))
    }

    /** This app's own recent logcat lines (an app may always read its own), merged with AstraTest.log() lines. */
    fun recentLogs(): List<LogLine> {
        val mine = logs.all()
        if (!AstraTest.options.captureLogs) return mine
        val fromLogcat = runCatching {
            val p = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-t", "150", "-v", "epoch", "--pid=${Process.myPid()}"))
            p.inputStream.bufferedReader().readLines().mapNotNull(::parseLogcat).also { p.destroy() }
        }.getOrDefault(emptyList())
        return (fromLogcat + mine).sortedBy { it.at }.takeLast(60)
    }

    // "  1730000000.123  1234  1234 E Tag: message"
    fun parseLogcat(line: String): LogLine? {
        val m = Regex("""^\s*(\d+)\.(\d{3})\s+\d+\s+\d+\s+([VDIWEF])\s+(.*)$""").find(line) ?: return null
        val (sec, ms, lv, rest) = m.destructured
        if (rest.startsWith("AstraTest")) return null
        val level = when (lv) { "E", "F" -> "error"; "W" -> "warn"; else -> "log" }
        return LogLine(level, rest.take(2000), sec.toLong() * 1000 + ms.toLong())
    }
}

internal class AstraInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        val t0 = System.nanoTime()
        val host = runCatching { java.net.URI(AstraTest.options.host).host }.getOrNull()
        return try {
            val res = chain.proceed(req)
            if (req.url.host != host) Capture.recordCall(req.method, req.url.toString(), res.code, (System.nanoTime() - t0) / 1_000_000)
            res
        } catch (e: Exception) {
            if (req.url.host != host) Capture.recordCall(req.method, req.url.toString(), 0, (System.nanoTime() - t0) / 1_000_000)
            throw e
        }
    }
}

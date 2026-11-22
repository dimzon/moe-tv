package tv.own.owntv.core.util

import android.content.Context
import android.util.Log
import org.json.JSONObject
import tv.own.owntv.core.CoreBuildInfo
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Sends the last recorded crash ([CrashRecorder]) to the maintainer through a public FormSubmit
 * endpoint (form-to-email), so a crash on a television in the living room reaches the developer
 * without anyone holding a laptop or opening adb. The endpoint is a hash, not an address — nothing
 * identifying is embedded in the APK.
 *
 * **The email subject carries the app name and version** — the point of the port: the inbox shows
 * which build crashed before the report is even opened. The body is the crash trace.
 *
 * Uploaded once per crash: a sibling marker records the crash file's size+mtime, so relaunching does
 * not re-send the same report. The crash file itself is kept (Settings → Error log → Export still
 * works). Called from `Application.onCreate` on a daemon thread — network is never on the launch path.
 */
object CrashUploader {
    private const val TAG = "OwnTV-CrashUploader"

    // Maintainer's FormSubmit inbox (the same public hash SmartRuTube reports to). Swap this hash to
    // route МОЁ ТВ crash mail to a dedicated address.
    private const val ENDPOINT = "https://formsubmit.co/ajax/44e36d25fc0b0a147876d92d6807267f"
    private const val TIMEOUT_MS = 8_000
    private const val MAX_CHARS = 14_000

    /** Upload a pending, not-yet-sent crash report in the background. Safe to call every launch. */
    fun uploadPending(context: Context) {
        val app = context.applicationContext
        Thread({ runCatching { send(app) }.onFailure { Log.w(TAG, "crash upload skipped: ${it.message}") } }, "crash-upload")
            .apply { isDaemon = true }
            .start()
    }

    private fun send(app: Context) {
        val crash = CrashRecorder.crashFile(app)
        if (!crash.isFile || crash.length() <= 0) return
        val marker = File(crash.parentFile, crash.name + ".uploaded")
        val stamp = "${crash.length()}:${crash.lastModified()}"
        // Already sent this exact crash? Leave it (still exportable), don't re-mail.
        if (marker.isFile && marker.readText() == stamp) return

        val report = CrashRecorder.read(app) ?: return
        val signature = report.lineSequence()
            .firstOrNull { it.startsWith("signature ") }
            ?.substringAfter(' ')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: "unknown"
        val body = if (report.length > MAX_CHARS) report.substring(0, MAX_CHARS) + "\n...[truncated]" else report

        val json = JSONObject()
            // Name, version and crash signature in the subject: the version separates releases and
            // the signature threads repeated reports of one crash in a mail client's conversation view.
            .put("_subject", subject(app, signature))
            .put("_captcha", "false")
            .put("_template", "table")
            .put("report", body)
        val bytes = json.toString().toByteArray(Charsets.UTF_8)

        var connection: HttpURLConnection? = null
        try {
            connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "POST"
                doOutput = true
                setFixedLengthStreamingMode(bytes.size)
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Origin", "https://github.com")
                setRequestProperty("Referer", "https://github.com/dimzon/moe-tv-site")
                setRequestProperty("User-Agent", "MOETV/" + CoreBuildInfo.versionName)
            }
            connection.outputStream.use { it.write(bytes) }
            val code = connection.responseCode
            val input = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = input?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code == HttpURLConnection.HTTP_OK && JSONObject(response).optString("success") == "true") {
                marker.writeText(stamp)
                Log.i(TAG, "crash report uploaded")
            } else {
                Log.w(TAG, "crash upload rejected: $code $response")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "crash upload failed: ${t.message}")
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * "МОЁ ТВ 1.9 (114) crash 1a2b3c4d" — the app's launcher label, the version it was built with,
     * and the crash signature. Mail clients thread identical subjects, so every report of the same
     * crash on the same release lands in one conversation.
     */
    private fun subject(app: Context, signature: String): String {
        val name = runCatching { app.packageManager.getApplicationLabel(app.applicationInfo).toString() }
            .getOrDefault("МОЁ ТВ")
        return "$name ${CoreBuildInfo.versionName} (${CoreBuildInfo.versionCode}) crash $signature"
    }
}

package tv.own.owntv.core.util

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import tv.own.owntv.core.CoreBuildInfo
import java.io.File
import kotlin.system.exitProcess

/**
 * Keeps the last crash on disk so it survives the process dying.
 *
 * The 4.2.3 Live TV crash took days to find because the only way to see the stack trace was
 * `adb logcat`, and the people hitting it were watching television, not holding a laptop. A crash
 * that leaves no trace behind is a crash that has to be guessed at.
 *
 * This writes the trace to the app's own files directory the moment the process goes down, and
 * [PlaybackErrorLog.export] picks it up on the next launch — so the whole handover is
 * *More → Error log → Export*, and a file in Downloads that can be sent.
 *
 * The trace from a release build is obfuscated: retrace it with the `mapping.txt` of the matching
 * release. Line numbers are kept (`-keepattributes SourceFile,LineNumberTable`), so a retraced
 * trace names the exact line.
 *
 * One documented exception to "every crash dies": a recognized platform crash on the main thread
 * ([isRecoverablePlatformCrash]) is recorded with a `survived:` marker and the main loop is
 * re-entered, so the process keeps running — a crash that has to be guessed at is also one that
 * does not have to be died to, when the bug is the platform's and the app is only the bystander.
 */
object CrashRecorder {

    private const val FILE_NAME = "last_crash.txt"

    /**
     * Extra text appended to a recorded crash, supplied by whoever has diagnostics worth keeping —
     * today the player's live diagnostics ring. Core cannot reach into the player, so the player
     * hands its snapshot in here instead; unset, a crash is simply recorded without the extra
     * section. Set it alongside [install], before anything can throw.
     */
    var diagnostics: (() -> String)? = null

    /**
     * Fuse for the platform-crash recovery in [install]: a process survives at most this many
     * recognized platform crashes. Each recovery re-enters the main message loop, nesting one more
     * loop on the main thread's stack — bounded on purpose.
     */
    private const val MAX_PLATFORM_RECOVERIES = 3

    private val platformRecoveries = java.util.concurrent.atomic.AtomicInteger()

    /**
     * The line [write] marks a survived platform crash with. [CrashUploader] keys off it to keep
     * these reports out of the mail entirely — a diagnosed platform bug is not actionable, the
     * record stays on disk for manual exports only.
     */
    const val SURVIVED_LINE =
        "survived: platform crash recognized — main loop re-entered, the app kept running"

    /** Install from `Application.onCreate`, before anything else can throw. */
    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            // The one documented exception to "the original handler still gets the original
            // throwable": a recognized platform crash on the main thread is survived instead of
            // delegating, because delegating means dying to a framework cleanup race we cannot
            // prevent (see [isRecoverablePlatformCrash]).
            if (
                thread === Looper.getMainLooper().thread &&
                platformRecoveries.get() < MAX_PLATFORM_RECOVERIES &&
                isRecoverablePlatformCrash(error)
            ) {
                platformRecoveries.incrementAndGet()
                runCatching { write(app, thread, error, survived = true) }
                // The message that crashed is already dispatched and gone; everything queued after
                // it is healthy. The loop does not return — the exit below is a safety net.
                Looper.loop()
                exitProcess(2)
            }
            // Never let the recorder itself be the reason the process dies differently than it would
            // have: whatever happens here, the original handler still gets the original throwable.
            runCatching { write(app, thread, error) }
            if (previous != null) previous.uncaughtException(thread, error) else exitProcess(2)
        }
    }

    /** The recorded crash, or null when the app has never gone down on this device. */
    fun read(context: Context): String? =
        runCatching { crashFile(context).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() } }.getOrNull()

    /** The on-disk crash file — also the source [CrashUploader] sends and the export reads. */
    fun crashFile(context: Context): File =
        File(File(context.applicationContext.filesDir, "diagnostics"), FILE_NAME)

    /**
     * Device facts an anonymous report otherwise cannot supply: the crash mail has no reply channel,
     * so the ROM fingerprint, ABI/bitness, heap class and RAM are recorded at write time. They tell
     * "the same vendor firmware again" apart from a random sample, and show whether the universal
     * APK ran its 32-bit slice.
     */
    private fun environment(context: Context): String = runCatching {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val processBits = if (Process.is64Bit()) "64-bit" else "32-bit"
        val heapMiB = Runtime.getRuntime().maxMemory() shr 20
        "ABIs ${Build.SUPPORTED_ABIS.joinToString()} · $processBits process · heap ${heapMiB} MiB" +
            " (class ${am.memoryClass}/${am.largeMemoryClass} MiB, lowRam=${am.isLowRamDevice})" +
            " · RAM ${mem.totalMem shr 20} MiB, free ${mem.availMem shr 20} MiB" +
            " · uptime ${SystemClock.elapsedRealtime() / 1000} s"
    }.getOrElse { "environment unavailable: ${it.javaClass.simpleName}" }

    /**
     * Short grouping key for the crash mail: the throwable class plus the first frames of its stack.
     * Obfuscated names are stable within a release, and the version in the mail subject separates
     * releases, so identical reports thread together without exposing the whole trace in the subject.
     */
    private fun signature(error: Throwable): String {
        val key = buildString {
            append(error.javaClass.name)
            for (frame in error.stackTrace.take(8)) {
                append('|').append(frame.className).append('#').append(frame.methodName)
            }
        }
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(key.toByteArray(Charsets.UTF_8))
        return java.math.BigInteger(1, digest).toString(16).padStart(64, '0').take(8)
    }

    private fun write(context: Context, thread: Thread, error: Throwable, survived: Boolean = false) {
        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date())
        val appName = runCatching {
            context.packageManager.getApplicationLabel(context.applicationInfo).toString()
        }.getOrDefault("МОЁ ТВ")
        val text = buildString {
            appendLine("$appName ${CoreBuildInfo.versionName} (${CoreBuildInfo.versionCode})")
            appendLine("${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE}) · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}, patch ${Build.VERSION.SECURITY_PATCH})")
            appendLine("build ${Build.FINGERPRINT}")
            appendLine(environment(context))
            appendLine("Crashed $stamp on thread \"${thread.name}\"")
            appendLine("signature ${signature(error)}")
            if (survived) {
                appendLine(SURVIVED_LINE)
            }
            appendLine()
            appendLine(android.util.Log.getStackTraceString(error))
            val live = runCatching { diagnostics?.invoke() }.getOrNull().orEmpty()
            if (live.isNotBlank()) {
                appendLine("--- live diagnostics leading up to the crash ---")
                appendLine(live)
            }
        }
        val f = crashFile(context)
        f.parentFile?.mkdirs()
        f.writeText(text)
    }
}

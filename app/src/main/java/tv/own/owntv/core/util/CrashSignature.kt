package tv.own.owntv.core.util

/** Stable crash-mail grouping key, matching SmartRuTube's root-cause algorithm. */
internal object CrashSignature {
    private const val MAX_CAUSE_DEPTH = 10
    private const val MAX_FRAMES = 8

    fun of(error: Throwable): String {
        var root = error
        var depth = 0
        while (root.cause != null && root.cause !== root && depth < MAX_CAUSE_DEPTH) {
            root = root.cause!!
            depth++
        }
        return of(root.javaClass.name, root.stackTrace)
    }

    fun of(rootClassName: String, frames: Array<StackTraceElement>): String = runCatching {
        val key = buildString {
            append(rootClassName)
            for (frame in frames.take(MAX_FRAMES)) {
                append('|').append(frame.className).append('#').append(frame.methodName)
            }
        }
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(key.toByteArray(Charsets.UTF_8))
        java.math.BigInteger(1, digest).toString(16).padStart(64, '0').take(8)
    }.getOrDefault("unknown")
}

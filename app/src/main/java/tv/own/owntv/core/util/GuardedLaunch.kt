package tv.own.owntv.core.util

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * A `launch` that reports a failed operation instead of letting it reach the thread's uncaught
 * exception handler. Used by ViewModels whose work touches the database or bulk user-data edits:
 * one bad row must not take the app down.
 *
 * Cancellation is rethrown — swallowing [CancellationException] would break structured concurrency
 * and leave a job believing it was still running after its scope was cancelled.
 */
fun CoroutineScope.launchGuarded(block: suspend CoroutineScope.() -> Unit) = launch {
    try {
        block()
    } catch (c: CancellationException) {
        throw c
    } catch (t: Exception) {
        // Errors (OOM, LinkageError, …) must still crash — swallowing them leaves the app in an
        // undefined state. Keep the stack: a bare toString() hides where the failure came from.
        Log.w(t.javaClass.simpleName, t.toString(), t)
    }
}

/**
 * `onFailure` for collectors that must keep cancellation intact. A plain `runCatching { … }
 * .onFailure { … }` swallows [CancellationException] and turns an cancelled job into a successful
 * one; this variant rethrows it and logs the rest with the stack attached.
 */
fun Result<*>.onFailureReport(action: (Exception) -> Unit = {}): Result<*> = onFailure { t ->
    if (t is CancellationException) throw t
    if (t is Exception) {
        Log.w(t.javaClass.simpleName, t.toString(), t)
        action(t)
    }
}

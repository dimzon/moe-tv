package tv.own.owntv.core.categorylock

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.own.owntv.core.customize.CategoryUnlockSession
import tv.own.owntv.core.customize.CustomizationStore

/**
 * Closes every category the PIN opened, and stops what plays from one that is now sealed: each app
 * calls [leftApp] when it leaves the screen and sets [guard] while it shows something that plays.
 * Runs in [scope], the app shell's. Main thread only.
 */
class CategoryRelock(
    private val scope: CoroutineScope,
    private val customize: CustomizationStore,
    private val unlocks: CategoryUnlockSession,
) {
    /**
     * Whether the user has a lock that can seal anything; null until read, or when the read failed.
     * Without one, leaving the app skips the check, so leaving and coming back works exactly as it
     * does with no locks: no wait for the check, and no fail-closed stop.
     */
    private val activeLocks: StateFlow<Boolean?> = customize.observeHasLocks()
        .map<Boolean, Boolean?> { it }
        .catch { e -> Log.w(TAG, "category lock state unreadable; relocks check what plays", e); emit(null) }
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** Set by the app while it is up: stops what plays from a category a relock seals. */
    var guard: SealedPlaybackGuard? = null

    private var sealCheck: Job? = null
    private var resumeAfterCheck: Job? = null

    /**
     * The app left the screen. Call it before the player frees its stream: what is playing is read
     * first, then stopped if now sealed.
     */
    fun leftApp() = relock(skipWithoutLocks = true)

    /** Back in front: [resume] what was freed only once the relock check has stopped what it sealed. */
    fun afterSealCheck(resume: () -> Unit) {
        val pending = sealCheck?.takeIf { it.isActive } ?: return resume()
        resumeAfterCheck = scope.launch { pending.join(); resume() }
    }

    /**
     * Relocks everything and checks what plays against the user's locks. Only leaving the app may
     * [skipWithoutLocks].
     */
    private fun relock(skipWithoutLocks: Boolean = false) {
        // Leaving again before the last return's resume ran: it must not start the player in the background.
        resumeAfterCheck?.cancel()
        val guard = guard
        val playing = guard?.snapshot()
        unlocks.relockAll()
        if (guard == null || playing == null || (skipWithoutLocks && nothingToSeal())) return
        val earlier = sealCheck
        sealCheck = scope.launch {
            earlier?.join()
            guard.stopIfSealed(playing) { sealsNow(it) }
        }
    }

    /** The check would read a lock state the user does not have: nothing can be sealed. */
    private fun nothingToSeal(): Boolean = activeLocks.value == false

    /**
     * Whether the user's locks seal [categoryId]. Fails closed: a category whose lock cannot be read
     * is treated as sealed, so its playback stops.
     */
    private suspend fun sealsNow(categoryId: Long): Boolean = try {
        customize.sealsCategory(categoryId)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "relock check failed for category $categoryId; stopping its playback", e)
        true
    }

    private companion object {
        const val TAG = "CategoryRelock"
    }
}

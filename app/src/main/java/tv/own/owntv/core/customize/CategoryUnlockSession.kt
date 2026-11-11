package tv.own.owntv.core.customize

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import tv.own.owntv.core.model.MediaType

/**
 * The locked categories the PIN has opened in this session, per browse section, and whether the
 * Settings PIN has opened the long-press changes.
 *
 * Memory only, never persisted: the app relocks everything when it leaves the screen.
 */
class CategoryUnlockSession {
    private data class State(
        val opened: Map<MediaType, Set<String>> = emptyMap(),
        val epoch: Long = 0,
        val changesOpen: Boolean = false,
    )

    private val state = MutableStateFlow(State())

    fun unlocked(type: MediaType): Flow<Set<String>> =
        state.map { it.opened[type].orEmpty() }.distinctUntilChanged()

    fun isUnlocked(type: MediaType, key: String): Boolean =
        key in state.value.opened[type].orEmpty()

    /** The current relock count. Read it before checking a PIN and pass it to [unlock]. */
    fun epoch(): Long = state.value.epoch

    /**
     * Opens [key] for the rest of the session, unless the session relocked since [since] (the app
     * left the screen while the PIN was being checked). Returns whether it opened.
     */
    fun unlock(type: MediaType, key: String, since: Long = epoch()): Boolean {
        val next = state.updateAndGet { current ->
            if (current.epoch != since) return@updateAndGet current
            current.copy(opened = current.opened + (type to (current.opened[type].orEmpty() + key)))
        }
        return next.epoch == since
    }

    /** Closes one category again — a fresh lock on a category opened earlier in the session. */
    fun relock(type: MediaType, key: String) {
        state.update { current ->
            val keys = current.opened[type] ?: return@update current
            current.copy(opened = current.opened + (type to (keys - key)))
        }
    }

    /** Whether the Settings PIN has opened the long-press changes for this session. */
    fun changesUnlocked(): Boolean = state.value.changesOpen

    /** Opens the long-press changes for the rest of the session, unless it relocked since [since]. */
    fun unlockChanges(since: Long = epoch()): Boolean {
        val next = state.updateAndGet { current ->
            if (current.epoch != since) current else current.copy(changesOpen = true)
        }
        return next.epoch == since
    }

    fun relockAll() {
        state.update { State(epoch = it.epoch + 1) }
    }
}

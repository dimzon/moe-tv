package tv.own.owntv.core.companion

import android.graphics.Bitmap

/**
 * Lifecycle of the Remote companion listener — a tiny embedded HTTP server on the TV whose pages any
 * phone, tablet or PC on the same Wi-Fi can open.
 *
 * The QR encodes only the [Listening.urls] address; the remote device is asked for [Listening.pin] on a
 * gate page before the page is served. The pages are the backup upload/download pair and the
 * background-image upload; local sync has no browser page at all.
 */
sealed interface CompanionFailure {
    data object InvalidPort : CompanionFailure
    data class PortInUse(val port: Int) : CompanionFailure
    data object Unavailable : CompanionFailure
}

sealed interface CompanionServerState {
    data object Idle : CompanionServerState
    data object Starting : CompanionServerState
    data class Listening(
        val port: Int,
        val urls: List<String>,
        val pin: String,
        val qr: Bitmap?,
    ) : CompanionServerState
    data class Failed(val failure: CompanionFailure) : CompanionServerState

    /**
     * Closed by the server itself after [CompanionHttpServer.MAX_PIN_ATTEMPTS] wrong PINs.
     * Distinct from [Failed] because nothing went wrong with the app — the user just needs to start
     * the link again, which mints a fresh PIN.
     */
    data object Locked : CompanionServerState
}

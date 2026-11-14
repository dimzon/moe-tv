package tv.own.owntv.core.setup

import android.content.res.Resources
import tv.own.myowntv.R
import tv.own.owntv.core.util.FriendlySyncFailure

/**
 * The words that go with the onboarding and sync failures. Both apps ask the same questions of the
 * provider and get the same semantic answers back, so the sentences belong next to the answers
 * rather than being written twice in two UI toolkits. Rendering — colour, layout, which line goes
 * where — stays with each app. The per-source "test" probes and the device-model vocabulary they
 * spoke are gone with the other providers.
 */

/** Words a sync failure. Not a plain string resource: [FriendlySyncFailure.Unknown] carries the
 *  provider's own message, which is the only thing there is to show. */
fun FriendlySyncFailure.displayText(res: Resources): String = when (this) {
    FriendlySyncFailure.Offline -> res.getString(R.string.sync_error_offline)
    FriendlySyncFailure.Generic -> res.getString(R.string.sync_error_generic)
    FriendlySyncFailure.Timeout -> res.getString(R.string.sync_error_timeout)
    FriendlySyncFailure.Unreachable -> res.getString(R.string.sync_error_unreachable)
    FriendlySyncFailure.ConnectionFailed -> res.getString(R.string.sync_error_connection)
    FriendlySyncFailure.StreamInterrupted -> res.getString(R.string.sync_error_interrupted)
    FriendlySyncFailure.MacNotAuthorised -> res.getString(R.string.sync_error_mac)
    FriendlySyncFailure.InvalidMac -> res.getString(R.string.sync_error_invalid_mac)
    FriendlySyncFailure.PortalHandshakeFailed -> res.getString(R.string.sync_error_portal)
    FriendlySyncFailure.PortalSessionExpired -> res.getString(R.string.sync_error_session)
    FriendlySyncFailure.AuthenticationRejected -> res.getString(R.string.sync_error_auth)
    FriendlySyncFailure.NotFound -> res.getString(R.string.sync_error_not_found)
    FriendlySyncFailure.ServerError -> res.getString(R.string.sync_error_server)
    FriendlySyncFailure.SecureConnectionFailed -> res.getString(R.string.sync_error_secure)
    FriendlySyncFailure.MalformedGuide -> res.getString(R.string.sync_error_malformed_guide)
    FriendlySyncFailure.GuideEmpty -> res.getString(R.string.sync_error_guide_empty)
    is FriendlySyncFailure.Unknown -> rawMessage
}

/** Words a semantic onboarding failure. */
fun SourceImporter.SetupFailure.displayText(res: Resources): String = when (this) {
    SourceImporter.SetupFailure.BackupRead -> res.getString(R.string.setup_backup_read_failed)
    SourceImporter.SetupFailure.Restore -> res.getString(R.string.setup_restore_failed)
    is SourceImporter.SetupFailure.Sync -> failure.displayText(res)
}

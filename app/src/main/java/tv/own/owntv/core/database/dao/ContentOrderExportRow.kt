package tv.own.owntv.core.database.dao

import tv.own.owntv.core.model.MediaType

/**
 * One manual-order row joined to its content row's stable identity, for the per-source re-sync
 * snapshot ([ContentOrderDao.exportRowsForSource]). Separate from [UserDataExportRow] because order
 * rows carry a context key + position instead of a single timestamp, and never cover episodes.
 * [updatedAt] is the row's LWW stamp — the sync payload carries it as the record's `at`.
 */
data class ContentOrderExportRow(
    val mediaType: MediaType,
    val itemId: Long,
    val contextKey: String,
    val position: Int,
    val updatedAt: Long = 0,
    val sourceId: Long,
    val remoteId: String?,
    val name: String?,
)

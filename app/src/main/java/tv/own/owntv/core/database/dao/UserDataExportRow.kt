package tv.own.owntv.core.database.dao

import tv.own.owntv.core.model.MediaType

data class UserDataExportRow(
    val mediaType: MediaType,
    val itemId: Long,
    val sourceId: Long,
    val remoteId: String?,
    val name: String?,
    val at: Long,
    /** Only watch-history rows carry it ([HistoryDao.exportRowsForSource]); the payload's `dev`. */
    val deviceId: String? = null,
)

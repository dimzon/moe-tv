package tv.own.owntv.core.database.dao

import tv.own.owntv.core.model.MediaType

data class UserDataExportRow(
    val mediaType: MediaType,
    val itemId: Long,
    val sourceId: Long,
    val remoteId: String?,
    val name: String?,
    val at: Long,
)

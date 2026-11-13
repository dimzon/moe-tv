package tv.own.owntv.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A programme reminder (v47): "tell me when this starts". The alarm fires [leadMinutes]
 * before [startMs]; the apps show the prompt, core only keeps time.
 *
 * `channelId` is the channel's hex remoteId (stable across re-syncs) and the row's only content
 * identity — schema v6 dropped the stored `channelName`/`epgChannelId`. A row lives for hours or days
 * and is deleted once its programme has ended or the user dismisses the prompt. It travels between
 * the household's devices as the `prog` kind (identity (channelId, startMs), LWW per row on
 * [updatedAt]) and is part of the backup snapshot.
 */
@Entity(
    tableName = "programme_reminders",
    indices = [
        Index("startMs"),
        // One reminder per programme per channel: pressing Remind me twice updates it.
        Index(value = ["channelId", "startMs"], unique = true),
    ],
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channelId: String,
    val title: String,
    /** The programme's own guide window. */
    val startMs: Long,
    val stopMs: Long,
    /** How long before [startMs] the prompt comes up; 0 = at the start. */
    val leadMinutes: Int,
    val createdAt: Long,
    /**
     * LWW stamp (epoch ms) for the per-row last-write-wins merge (phase ③): stamped on every
     * create/edit ([tv.own.owntv.core.reminder.ReminderManager]) and compared on a sync apply —
     * only an incoming reminder **newer** than the local one replaces it. Sync identity is
     * (channelId, startMs). 0 = a row that predates the column.
     */
    @androidx.room.ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
) {
    val remindAtMs: Long get() = startMs - leadMinutes * 60_000L
}

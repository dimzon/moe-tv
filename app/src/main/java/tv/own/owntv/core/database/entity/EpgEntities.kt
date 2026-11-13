package tv.own.owntv.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import tv.own.owntv.core.parser.EpgDetails

/**
 * A single programme (now/next & guide). Bounded to a rolling window (≈ now → +48h) by the EPG
 * engine, which prunes old rows. Indexed on `(epgChannelId, startMs)` for fast now/next lookups.
 *
 * The columns are exactly what RuTube publishes for a broadcast: the times and the title are
 * always there; [subtitle], [description], [category], [ageRating] and [iconUrl] only when the
 * feed carries them. Anything the feed does not send is null — the screens show what is there.
 *
 * `epgChannelId` is the channel's hex videoId: with one source the guide key IS the catalogue key.
 */
@Entity(
    tableName = "epg_programmes",
    indices = [
        Index(value = ["epgChannelId", "startMs"], unique = true, name = "index_epg_programmes_natural_key"),
        Index("stopMs"),
        // Time-bounded reads (a rail, an "on now" list) seek a range of start times rather than
        // walking the table. Without it a bounded window still examined every row in the database.
        Index(value = ["startMs", "stopMs"]),
    ],
)
data class EpgProgrammeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epgChannelId: String,
    val startMs: Long,
    val stopMs: Long,
    val title: String,
    /** The broadcast's own second line — often the episode ("Сезон 2 Серия 17", "Выпуск 3"). */
    val subtitle: String? = null,
    val description: String? = null,
    /** The one genre RuTube files the programme under (`ext_categories[0].name`). */
    val category: String? = null,
    /** Age restriction exactly as the feed writes it ("16+", "12+", "0+"). */
    val ageRating: String? = null,
    /** `preview_picture_url` — the programme's 600×450 artwork. */
    val iconUrl: String? = null,
) {
    val details: EpgDetails get() = EpgDetails(category, ageRating, subtitle)
}

/** One catalogue channel as the "Match EPG" picker sees it. */
data class GuideChannelName(
    val epgChannelId: String,
    val displayName: String?,
)

package tv.own.owntv.core.backup

import tv.own.owntv.core.database.OwnTVDatabase
import tv.own.owntv.core.database.dao.CustomCategoryDao
import tv.own.owntv.core.database.dao.FavoriteDao
import tv.own.owntv.core.database.dao.HistoryDao
import tv.own.owntv.core.database.transaction
import tv.own.owntv.core.model.MediaType

/**
 * The one place a user deletes their own data — an unfavorite, "Remove from history", taking an item
 * out of a custom category.
 *
 * It exists because a deletion has to be *recorded*, not merely performed (see
 * [tv.own.owntv.core.database.entity.UserDataTombstoneEntity]): local sync merges, so a row that is
 * simply gone here looks to the other device like a row this one has not heard about yet, and comes
 * straight back on the next sync. Every function below writes the marker and does the delete in one
 * transaction, so the two can never come apart.
 *
 * **Only user actions belong here.** The orphan purges after a re-sync go on
 * calling the DAOs directly and deliberately: those rows are being tidied up, not thrown away, and
 * turning "the playlist was refreshed" into "delete this everywhere" would lose real data.
 */
class UserDataWriter(
    private val db: OwnTVDatabase,
    private val favoriteDao: FavoriteDao,
    private val historyDao: HistoryDao,
    private val customCategoryDao: CustomCategoryDao,
    private val userData: UserDataResolver,
) {

    /** Unfavorite one item. */
    suspend fun removeFavorite(type: MediaType, itemId: Long) = db.transaction {
        userData.recordDeletion("fav", type, itemId)
        favoriteDao.remove(type, itemId)
    }

    /** "Remove from history" for one item. */
    suspend fun removeHistory(type: MediaType, itemId: Long) = db.transaction {
        userData.recordDeletion("his", type, itemId)
        historyDao.remove(type, itemId)
    }

    /** Take one item out of one custom category. */
    suspend fun removeCustomCategoryMember(
        type: MediaType,
        contextKey: String,
        itemId: Long,
    ) = db.transaction {
        userData.recordDeletion("member", type, itemId, contextKey = contextKey)
        customCategoryDao.deleteItem(type, contextKey, itemId)
    }

    /**
     * "Clear watch history" — everything, or one media type.
     *
     * Every row is described before it goes, which is thousands of small reads on a large history.
     * That is the price of the deletion actually sticking on the user's other device, and it is a
     * once-in-a-while action taken from a settings screen, not something on a hot path.
     */
    suspend fun clearHistory(type: MediaType? = null) = db.transaction {
        val history = if (type == null) historyDao.getAll() else historyDao.getAllOfType(type)
        history.forEach { userData.recordDeletion("his", it.mediaType, it.itemId) }
        if (type == null) {
            historyDao.clear()
        } else {
            historyDao.clearType(type)
        }
        userData.pruneTombstones()
    }
}

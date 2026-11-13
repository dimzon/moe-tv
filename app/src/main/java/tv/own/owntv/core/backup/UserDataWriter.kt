package tv.own.owntv.core.backup

import kotlinx.coroutines.flow.first
import tv.own.owntv.core.database.OwnTVDatabase
import tv.own.owntv.core.database.dao.CustomCategoryDao
import tv.own.owntv.core.database.dao.FavoriteDao
import tv.own.owntv.core.database.dao.HistoryDao
import tv.own.owntv.core.database.transaction
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.settings.SyncMatrix

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
 * **The tombstone is gated by the sync matrix** (phase ③): a marker is only written when its
 * category's "give" side is on — the device is not going to send that category, so a local deletion
 * of it stays local. With the defaults that means favorites record markers and history does not
 * (its rows merge per-row on `watchedAt` instead; the deletion kind stopped travelling). Custom
 * categories are not a matrix category at all, so removing a member never records a marker either.
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
    private val settings: SettingsRepository,
) {

    /** Unfavorite one item. */
    suspend fun removeFavorite(itemId: String) = db.transaction {
        if (gives(SyncMatrix.KIND_FAVORITES)) userData.recordDeletion(SyncMatrix.KIND_FAVORITES, itemId)
        favoriteDao.remove(itemId)
    }

    /**
     * "Remove from history" for one item. No marker by default: history rows merge per-row on
     * `watchedAt` and its tombstone kind does not travel, so unless the user turns the category's
     * "give" on the deletion stays local.
     */
    suspend fun removeHistory(itemId: String) = db.transaction {
        if (gives(SyncMatrix.KIND_HISTORY)) userData.recordDeletion(SyncMatrix.KIND_HISTORY, itemId)
        historyDao.remove(itemId)
    }

    /** Take one item out of one custom category. Custom categories are not a sync category — no marker. */
    suspend fun removeCustomCategoryMember(contextKey: String, itemId: String) = db.transaction {
        customCategoryDao.deleteItem(contextKey, itemId)
    }

    /**
     * "Clear watch history".
     *
     * Every row is described before it goes, which is thousands of small reads on a large history.
     * That is the price of the deletion actually sticking on the user's other device, and it is a
     * once-in-a-while action taken from a settings screen, not something on a hot path. Only paid
     * when the category's "give" is on — see the class doc.
     */
    suspend fun clearHistory() = db.transaction {
        if (gives(SyncMatrix.KIND_HISTORY)) {
            historyDao.getAllOnce().forEach { userData.recordDeletion(SyncMatrix.KIND_HISTORY, it.itemId) }
            userData.pruneTombstones()
        }
        historyDao.clear()
    }

    /** The "give" side of a category, as the matrix has it right now. */
    private suspend fun gives(kind: String): Boolean = settings.syncMatrix.first().gives(kind)
}

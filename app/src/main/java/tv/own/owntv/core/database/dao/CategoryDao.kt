package tv.own.owntv.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import tv.own.owntv.core.database.entity.CategoryEntity
import tv.own.owntv.core.model.MediaType

@Dao
interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(categories: List<CategoryEntity>): List<Long>

    /** Returns rowids (−1 for IGNOREd conflicts) so the sync can learn new category ids without a re-query. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<CategoryEntity>): List<Long>

    @Update
    suspend fun updateAll(categories: List<CategoryEntity>)

    /** One built-in source, so the rail follows the provider's category order directly. */
    @Query(
        "SELECT categories.* FROM categories WHERE categories.mediaType = :type " +
            "ORDER BY categories.sortOrder ASC, categories.name ASC",
    )
    fun observe(type: MediaType): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): CategoryEntity?

    @Query("SELECT COUNT(*) FROM categories WHERE mediaType = :type")
    fun count(type: MediaType): Flow<Int>

    @Query("DELETE FROM categories WHERE mediaType = :type")
    suspend fun clear(type: MediaType)

    @Query("SELECT * FROM categories WHERE mediaType = :type AND remoteId IN (:remoteIds)")
    suspend fun findByRemoteIds(type: MediaType, remoteIds: List<String>): List<CategoryEntity>

    @Query("SELECT remoteId FROM categories WHERE mediaType = :type AND remoteId IS NOT NULL")
    suspend fun remoteIds(type: MediaType): List<String>

    @Query("DELETE FROM categories WHERE mediaType = :type AND remoteId IN (:remoteIds)")
    suspend fun deleteByRemoteIds(type: MediaType, remoteIds: List<String>)
}

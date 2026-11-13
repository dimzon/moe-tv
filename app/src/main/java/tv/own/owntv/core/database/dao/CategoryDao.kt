package tv.own.owntv.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import tv.own.owntv.core.database.entity.CategoryEntity

@Dao
interface CategoryDao {
    /** Returns the provider ids (−1 for IGNOREd conflicts) so the sync can map rows without a re-query. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<CategoryEntity>): List<Long>

    @Update
    suspend fun updateAll(categories: List<CategoryEntity>)

    /** One built-in source, so the rail follows the provider's category order directly. */
    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, name ASC")
    fun observe(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): CategoryEntity?

    @Query("SELECT COUNT(*) FROM categories")
    fun count(): Flow<Int>

    @Query("DELETE FROM categories")
    suspend fun clear()

    @Query("SELECT * FROM categories WHERE id IN (:ids)")
    suspend fun findByIds(ids: List<Long>): List<CategoryEntity>

    @Query("SELECT id FROM categories")
    suspend fun ids(): List<Long>

    @Query("DELETE FROM categories WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}

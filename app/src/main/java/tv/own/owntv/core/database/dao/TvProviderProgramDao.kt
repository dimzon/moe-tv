package tv.own.owntv.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import tv.own.owntv.core.database.entity.TvProviderProgramEntity
import tv.own.owntv.core.tv.TvProviderSurface

@Dao
interface TvProviderProgramDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: TvProviderProgramEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<TvProviderProgramEntity>)

    @Query("SELECT * FROM tv_provider_programs WHERE surface = :surface AND groupId = :groupId LIMIT 1")
    suspend fun find(surface: TvProviderSurface, groupId: Long): TvProviderProgramEntity?

    @Query("SELECT * FROM tv_provider_programs ORDER BY surface ASC, lastPublishedAt DESC")
    suspend fun all(): List<TvProviderProgramEntity>

    @Query("SELECT * FROM tv_provider_programs WHERE surface = :surface")
    suspend fun getForSurface(surface: TvProviderSurface): List<TvProviderProgramEntity>

    @Query("DELETE FROM tv_provider_programs WHERE surface = :surface AND groupId = :groupId")
    suspend fun delete(surface: TvProviderSurface, groupId: Long)

    @Query("DELETE FROM tv_provider_programs")
    suspend fun clearAll()
}

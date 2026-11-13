package tv.own.owntv.core.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import tv.own.owntv.core.database.SingleSource
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.MediaType

/**
 * The set of sources the Browse screens should show right now: the one built-in RuTube source.
 * There is no `sources` table any more (schema v5) and no "active playlist" filter — this is the
 * single-element view the grids/guide read from, kept so their call sites stay unchanged.
 */
data class ActiveProfileSources(val sources: List<SourceEntity>) {
    val sourceIds: List<Long> get() = sources.map { it.id }
    val liveSourceIds: List<Long> get() = sources.filter { it.syncLive }.map { it.id }

    fun sourceIdsFor(type: MediaType): List<Long> = liveSourceIds
}

/** Always the one source; a Flow for the collect sites that expect one. */
fun activeProfileSources(): Flow<ActiveProfileSources> =
    flowOf(ActiveProfileSources(listOf(BuiltInSource.current)))

/** One-shot version of [activeProfileSources] for imperative code paths. */
@Suppress("UNUSED_PARAMETER")
suspend fun activeSourceIds(section: MediaType? = null): List<Long> = listOf(SingleSource.id)

/** The source list, for screens that list what exists. */
fun allSources(): Flow<List<SourceEntity>> = flowOf(listOf(BuiltInSource.current))

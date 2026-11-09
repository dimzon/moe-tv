package tv.own.myowntv.features.settings
import tv.own.owntv.*
import tv.own.owntv.core.*
import tv.own.owntv.core.backup.*
import tv.own.owntv.core.brand.*
import tv.own.owntv.core.catalog.*
import tv.own.owntv.core.categorylock.*
import tv.own.owntv.core.companion.*
import tv.own.owntv.core.content.*
import tv.own.owntv.core.customize.*
import tv.own.owntv.core.database.*
import tv.own.owntv.core.database.dao.*
import tv.own.owntv.core.database.entity.*
import tv.own.owntv.core.di.*
import tv.own.owntv.core.download.*
import tv.own.owntv.core.drm.*
import tv.own.owntv.core.epg.*
import tv.own.owntv.core.home.*
import tv.own.owntv.core.i18n.*
import tv.own.owntv.core.launcher.*
import tv.own.owntv.core.live.*
import tv.own.owntv.core.menu.*
import tv.own.owntv.core.metadata.*
import tv.own.owntv.core.model.*
import tv.own.owntv.core.nav.*
import tv.own.owntv.core.network.*
import tv.own.owntv.core.parser.*
import tv.own.owntv.core.player.*
import tv.own.owntv.core.profile.*
import tv.own.owntv.core.recording.*
import tv.own.owntv.core.reminder.*
import tv.own.owntv.core.repository.*
import tv.own.owntv.core.settings.*
import tv.own.owntv.core.setup.*
import tv.own.owntv.core.sports.*
import tv.own.owntv.core.sports.model.*
import tv.own.owntv.core.stalker.*
import tv.own.owntv.core.storage.*
import tv.own.owntv.core.subtitles.*
import tv.own.owntv.core.sync.*
import tv.own.owntv.core.sync.local.*
import tv.own.owntv.core.sync.work.*
import tv.own.owntv.core.theme.*
import tv.own.owntv.core.timeshift.*
import tv.own.owntv.core.trending.*
import tv.own.owntv.core.tv.*
import tv.own.owntv.core.ui.*
import tv.own.owntv.core.ui.stage.*
import tv.own.owntv.core.update.*
import tv.own.owntv.core.util.*
import tv.own.owntv.core.weather.*
import tv.own.owntv.player.*
import tv.own.owntv.player.ffmpeg.*

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.own.owntv.core.database.dao.LinkedSubtitle
import tv.own.owntv.core.subtitles.SubtitleController

/** Backs the "Delete subtitles" screen: downloaded subs per Movies/Series, with delete. */
class DeleteSubtitlesViewModel(
    private val controller: SubtitleController,
) : ViewModel() {

    enum class Section(val mediaType: String) {
        MOVIES("MOVIE"), SERIES("SERIES")
    }

    private val _section = MutableStateFlow(Section.MOVIES)
    val section: StateFlow<Section> = _section.asStateFlow()

    private val _items = MutableStateFlow<List<LinkedSubtitle>>(emptyList())
    val items: StateFlow<List<LinkedSubtitle>> = _items.asStateFlow()

    /** Counts per section for the toggle labels + empty states. */
    private val _movieCount = MutableStateFlow(0)
    val movieCount: StateFlow<Int> = _movieCount.asStateFlow()
    private val _seriesCount = MutableStateFlow(0)
    val seriesCount: StateFlow<Int> = _seriesCount.asStateFlow()

    init { refresh() }

    fun selectSection(section: Section) {
        _section.value = section
        reloadItems()
    }

    fun deleteOne(item: LinkedSubtitle) {
        viewModelScope.launch {
            controller.deleteCached(item.cacheId)
            refresh()
        }
    }

    fun deleteAll() {
        viewModelScope.launch { controller.deleteAllDownloads(); refresh() }
    }

    fun deleteAllMovies() {
        viewModelScope.launch { controller.deleteAllForType(Section.MOVIES.mediaType); refresh() }
    }

    fun deleteAllSeries() {
        viewModelScope.launch { controller.deleteAllForType(Section.SERIES.mediaType); refresh() }
    }

    private fun refresh() {
        viewModelScope.launch {
            _movieCount.value = controller.downloadsForType(Section.MOVIES.mediaType).size
            _seriesCount.value = controller.downloadsForType(Section.SERIES.mediaType).size
            reloadItems()
        }
    }

    private fun reloadItems() {
        viewModelScope.launch { _items.value = controller.downloadsForType(_section.value.mediaType) }
    }
}

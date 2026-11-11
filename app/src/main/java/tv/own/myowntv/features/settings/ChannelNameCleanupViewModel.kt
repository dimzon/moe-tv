package tv.own.myowntv.features.settings
import tv.own.owntv.*
import tv.own.owntv.core.*
import tv.own.owntv.core.backup.*
import tv.own.owntv.core.brand.*
import tv.own.owntv.core.categorylock.*
import tv.own.owntv.core.companion.*
import tv.own.owntv.core.content.*
import tv.own.owntv.core.customize.*
import tv.own.owntv.core.database.*
import tv.own.owntv.core.database.dao.*
import tv.own.owntv.core.database.entity.*
import tv.own.owntv.core.di.*
import tv.own.owntv.core.drm.*
import tv.own.owntv.core.epg.*
import tv.own.owntv.core.home.*
import tv.own.owntv.core.i18n.*
import tv.own.owntv.core.launcher.*
import tv.own.owntv.core.live.*
import tv.own.owntv.core.menu.*
import tv.own.owntv.core.model.*
import tv.own.owntv.core.nav.*
import tv.own.owntv.core.network.*
import tv.own.owntv.core.parser.*
import tv.own.owntv.core.player.*
import tv.own.owntv.core.reminder.*
import tv.own.owntv.core.repository.*
import tv.own.owntv.core.settings.*
import tv.own.owntv.core.setup.*
import tv.own.owntv.core.stalker.*
import tv.own.owntv.core.storage.*
import tv.own.owntv.core.sync.*
import tv.own.owntv.core.sync.local.*
import tv.own.owntv.core.sync.work.*
import tv.own.owntv.core.theme.*
import tv.own.owntv.core.tv.*
import tv.own.owntv.core.ui.*
import tv.own.owntv.core.ui.stage.*
import tv.own.owntv.core.update.*
import tv.own.owntv.core.util.*
import tv.own.owntv.core.weather.*
import tv.own.owntv.player.*

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.SourceEntity

/** Saves a playlist's "Clean channel names" option ([SourceEntity.cleanChannelNames]). */
class ChannelNameCleanupViewModel(
    private val sourceDao: SourceDao,
) : ViewModel() {

    /**
     * Stores the option and the user's own words ([customText], `;`-separated; blank = none). Names
     * are cleaned at sync time, so [onNeedsResync] gets the updated playlist whenever the shown names
     * would change: the option was switched, or its words changed while it is on.
     */
    fun save(source: SourceEntity, enabled: Boolean, customText: String, onNeedsResync: (SourceEntity) -> Unit) {
        val strip = customText.trim().ifEmpty { null }
        if (enabled == source.cleanChannelNames && strip == source.channelNameStrip) return
        val namesChange = enabled || source.cleanChannelNames
        viewModelScope.launch {
            sourceDao.updateChannelNameCleanup(source.id, enabled, strip)
            if (namesChange) onNeedsResync(source.copy(cleanChannelNames = enabled, channelNameStrip = strip))
        }
    }
}

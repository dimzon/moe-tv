package tv.own.myowntv.features.epg
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
import tv.own.owntv.core.profile.*
import tv.own.owntv.core.reminder.*
import tv.own.owntv.core.repository.*
import tv.own.owntv.core.settings.*
import tv.own.owntv.core.setup.*
import tv.own.owntv.core.sports.*
import tv.own.owntv.core.sports.model.*
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

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.parser.EpgDetails

class ProgrammeDayPageTest {

    private fun prog(start: Long, stop: Long, id: Long = 0) =
        EpgProgrammeEntity(id = id, sourceId = 1, epgChannelId = "c", startMs = start, stopMs = stop, title = "P$start")

    private val day = listOf(prog(0, 100), prog(100, 200), prog(200, 300), prog(300, 400))

    @Test
    fun `facts in order, categories are tags not facts`() {
        val d = EpgDetails(categories = "Drama", year = 2023, rating = "FSK 12", lengthMin = 95, episode = "S2 E5")
        assertEquals(listOf("S2 E5", "2023", "FSK 12", "95 min"), programmeFacts(d, "95 min"))
        assertEquals(emptyList<String>(), programmeFacts(EpgDetails.NONE, null))
    }

    @Test
    fun `past, on now and later against the clock`() {
        assertEquals(ProgrammeTime.PAST, programmeTime(prog(0, 100), now = 100))
        assertEquals(ProgrammeTime.NOW, programmeTime(prog(100, 200), now = 100))
        assertEquals(ProgrammeTime.NOW, programmeTime(prog(100, 200), now = 199))
        assertEquals(ProgrammeTime.LATER, programmeTime(prog(200, 300), now = 199))
    }

    @Test
    fun `opens on the programme asked for, by its start`() {
        assertEquals(3, openingIndex(day, wantedStartMs = 300, now = 150))
    }

    @Test
    fun `opens on the one on now when nothing is asked, or the asked one is gone`() {
        assertEquals(1, openingIndex(day, wantedStartMs = null, now = 150))
        assertEquals(1, openingIndex(day, wantedStartMs = 999, now = 150))
    }

    @Test
    fun `another day opens on the next to start, else the first`() {
        val gap = listOf(prog(0, 100), prog(300, 400))
        assertEquals(1, openingIndex(gap, wantedStartMs = null, now = 150))
        assertEquals(0, openingIndex(day, wantedStartMs = null, now = 10_000))
        assertEquals(0, openingIndex(emptyList(), wantedStartMs = null, now = 0))
    }

    @Test
    fun `buttons by where the programme sits`() {
        // Replay and recording are gone: a finished programme offers nothing, NOW only watches,
        // LATER only reminds.
        assertEquals(emptyList<PageAction>(), pageActions(ProgrammeTime.PAST))
        assertEquals(listOf(PageAction.WATCH), pageActions(ProgrammeTime.NOW))
        assertEquals(listOf(PageAction.REMIND), pageActions(ProgrammeTime.LATER))
    }
}

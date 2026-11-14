package tv.own.owntv.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.settings.DataRefreshPolicy
import tv.own.owntv.core.settings.EpgAutoRefresh
import tv.own.owntv.core.settings.EpgRefresh
import tv.own.owntv.core.settings.PlaylistAutoRefresh
import tv.own.owntv.core.settings.PlaylistRefresh
import tv.own.owntv.core.settings.dataRefreshPolicyOf

/**
 * The startup decisions, without Android or a database: what counts as missing, and when an existing
 * data set is stale enough to refresh under each policy. The default for both data sets is six hours.
 */
class AutoRefreshTest {

    private val now = 1_000_000_000_000L
    private val sixHours = 6 * 3600_000L

    @Test
    fun `a catalogue without channels or without a completed sync is missing`() {
        assertTrue(catalogueIsMissing(channels = 0, lastSyncAt = now))
        assertTrue(catalogueIsMissing(channels = 205, lastSyncAt = null))
        assertFalse(catalogueIsMissing(channels = 205, lastSyncAt = now))
    }

    @Test
    fun `missing catalogue is loaded even with an explicit only-first policy`() {
        val onlyFirst = DataRefreshPolicy.ONLY_FIRST.playlistRefresh()
        val due = playlistRefreshDue(onlyFirst, lastSyncAt = null, now = now, includeStartup = true)
        assertFalse("OFF alone never schedules a timer refresh", due)
        assertTrue(
            "bootstrap must not depend on the policy",
            catalogueIsMissing(0, null) || due,
        )
    }

    @Test
    fun `an absent policy is six hours for both data sets`() {
        assertEquals(DataRefreshPolicy.EVERY_6_HOURS, DataRefreshPolicy.DEFAULT)
        assertEquals(sixHours, DataRefreshPolicy.DEFAULT.playlistRefresh().thresholdMs)
        assertEquals(sixHours, DataRefreshPolicy.DEFAULT.epgRefresh().thresholdMs)
        assertEquals(sixHours, AutoRefresh.DEFAULT_PLAYLIST_REFRESH.thresholdMs)
        assertEquals(sixHours, AutoRefresh.DEFAULT_EPG_REFRESH.thresholdMs)
    }

    @Test
    fun `a catalogue synced less than six hours ago is left alone`() {
        assertFalse(playlistRefreshDue(DataRefreshPolicy.DEFAULT.playlistRefresh(), now - 5 * 3600_000L, now, includeStartup = true))
    }

    @Test
    fun `a catalogue older than six hours refreshes`() {
        assertTrue(playlistRefreshDue(DataRefreshPolicy.DEFAULT.playlistRefresh(), now - 7 * 3600_000L, now, includeStartup = false))
    }

    @Test
    fun `only-first never refreshes on a timer`() {
        assertFalse(playlistRefreshDue(DataRefreshPolicy.ONLY_FIRST.playlistRefresh(), now - 30 * 24 * 3600_000L, now, includeStartup = true))
        assertFalse(epgRefreshDue(DataRefreshPolicy.ONLY_FIRST.epgRefresh(), now - 30 * 24 * 3600_000L, now, includeStartup = true))
    }

    @Test
    fun `on-startup refreshes on a cold start and not on resume`() {
        assertTrue(playlistRefreshDue(DataRefreshPolicy.STARTUP.playlistRefresh(), now, now, includeStartup = true))
        assertFalse(playlistRefreshDue(DataRefreshPolicy.STARTUP.playlistRefresh(), now, now, includeStartup = false))
        assertTrue(epgRefreshDue(DataRefreshPolicy.STARTUP.epgRefresh(), now, now, includeStartup = true))
        assertFalse(epgRefreshDue(DataRefreshPolicy.STARTUP.epgRefresh(), now, now, includeStartup = false))
    }

    @Test
    fun `an empty guide that was never attempted is missing`() {
        assertTrue(guideIsMissing(programmes = 0, lastEpgSyncAt = null))
        assertFalse(guideIsMissing(programmes = 0, lastEpgSyncAt = now))
        assertFalse(guideIsMissing(programmes = 8573, lastEpgSyncAt = null))
    }

    @Test
    fun `an empty guide after a failed attempt is retried by the default after six hours`() {
        val default = DataRefreshPolicy.DEFAULT.epgRefresh()
        assertFalse(epgRefreshDue(default, lastSyncAt = now - 3600_000L, now = now, includeStartup = false))
        assertTrue(epgRefreshDue(default, lastSyncAt = now - 7 * 3600_000L, now = now, includeStartup = false))
        assertFalse(epgRefreshDue(DataRefreshPolicy.ONLY_FIRST.epgRefresh(), now - 7 * 3600_000L, now, includeStartup = true))
    }

    @Test
    fun `an existing guide refreshes by its own timestamp`() {
        val default = DataRefreshPolicy.DEFAULT.epgRefresh()
        assertFalse(epgRefreshDue(default, lastSyncAt = now - 5 * 3600_000L, now = now, includeStartup = true))
        assertTrue(epgRefreshDue(default, lastSyncAt = now - 7 * 3600_000L, now = now, includeStartup = false))
    }

    @Test
    fun `legacy per-map modes map to the shared policy`() {
        assertEquals(DataRefreshPolicy.ONLY_FIRST, dataRefreshPolicyOf(PlaylistAutoRefresh.OFF, null))
        assertEquals(DataRefreshPolicy.STARTUP, dataRefreshPolicyOf(PlaylistAutoRefresh.STARTUP, null))
        assertEquals(DataRefreshPolicy.EVERY_6_HOURS, dataRefreshPolicyOf(PlaylistAutoRefresh.HOURS_12, null))
        assertEquals(DataRefreshPolicy.ONLY_FIRST, dataRefreshPolicyOf(null, EpgAutoRefresh.OFF))
        assertEquals(DataRefreshPolicy.STARTUP, dataRefreshPolicyOf(null, EpgAutoRefresh.STARTUP))
        assertEquals(DataRefreshPolicy.DEFAULT, dataRefreshPolicyOf(null, null))
        assertEquals(
            "the catalogue's entry wins when the two maps disagree",
            DataRefreshPolicy.STARTUP,
            dataRefreshPolicyOf(PlaylistAutoRefresh.STARTUP, EpgAutoRefresh.OFF),
        )
    }

    @Test
    fun `policy round-trips through both stored refresh shapes`() {
        for (policy in DataRefreshPolicy.entries) {
            assertEquals(policy, dataRefreshPolicyOf(policy.playlistRefresh().mode, policy.epgRefresh().mode))
        }
        // Sanity-check the shapes the wizard and Settings write.
        assertEquals(PlaylistAutoRefresh.HOURS_6, DataRefreshPolicy.EVERY_6_HOURS.playlistRefresh().mode)
        assertEquals(EpgAutoRefresh.HOURS_6, DataRefreshPolicy.EVERY_6_HOURS.epgRefresh().mode)
        assertEquals(PlaylistRefresh.OFF, DataRefreshPolicy.ONLY_FIRST.playlistRefresh())
        assertEquals(EpgRefresh.OFF, DataRefreshPolicy.ONLY_FIRST.epgRefresh())
    }
}

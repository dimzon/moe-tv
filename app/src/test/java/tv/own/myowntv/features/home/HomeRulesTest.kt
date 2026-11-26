package tv.own.myowntv.features.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeRulesTest {

    // --- Keep watching stills: with the VOD rails gone, the card shows the channel logo or nothing ---

    @Test
    fun channelShowsItsLogo() = assertEquals(HomeStill.Logo("logo"), homeStill("logo"))

    @Test
    fun noLogoIsNoStill() = assertEquals(HomeStill.None, homeStill(null))

    @Test
    fun blankLogoIsNoStill() = assertEquals(HomeStill.None, homeStill(" "))
}

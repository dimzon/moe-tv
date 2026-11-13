package tv.own.owntv.core.customize

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.own.owntv.core.database.entity.ChannelEntity

/** A name given in Customize is the one shown; without one, the provider's. */
class DisplayNameTest {

    private val channel = ChannelEntity( name = "SKY MOVIES F1 FHD", remoteId = "101")

    @Test
    fun `a renamed item shows its new name`() {
        val cust = SectionCustomizations(
            itemNames = mapOf(CustomizeKeys.channel(channel) to "Sky Movies F1"),
        )
        assertEquals("Sky Movies F1", cust.displayName(channel))
    }

    @Test
    fun `an item never renamed keeps the provider's name`() {
        val cust = SectionCustomizations()
        assertEquals("SKY MOVIES F1 FHD", cust.displayName(channel))
    }
}

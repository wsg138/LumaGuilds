package net.lumalyte.lg.domain.values

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FlagTest {

    @Test
    fun `claim flag surface remains explicit`() {
        assertEquals(
            setOf(
                "FIRE",
                "MOB",
                "EXPLOSION",
                "PISTON",
                "FLUID",
                "TREE",
                "SCULK",
                "DISPENSER",
                "SPONGE",
                "LIGHTNING",
                "FALLING_BLOCK",
                "PASSIVE_ENTITY_VEHICLE"
            ),
            Flag.entries.map { it.name }.toSet()
        )
    }

    @Test
    fun `each flag exposes unique matching localization keys`() {
        val nameKeys = Flag.entries.map { it.nameKey }
        val loreKeys = Flag.entries.map { it.loreKey }

        assertEquals(nameKeys.size, nameKeys.toSet().size)
        assertEquals(loreKeys.size, loreKeys.toSet().size)

        Flag.entries.forEach { flag ->
            val id = flag.name.lowercase()
            assertEquals("flag.$id.name", flag.nameKey)
            assertEquals("flag.$id.lore", flag.loreKey)
            assertTrue(flag.nameKey.isNotBlank())
            assertTrue(flag.loreKey.isNotBlank())
        }
    }
}

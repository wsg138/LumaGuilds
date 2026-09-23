package net.lumalyte.lg.domain.values

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ClaimPermissionTest {

    @Test
    fun `claim permission surface remains explicit`() {
        assertEquals(
            setOf(
                "BUILD",
                "HARVEST",
                "CONTAINER",
                "DISPLAY",
                "VEHICLE",
                "SIGN",
                "REDSTONE",
                "DOOR",
                "TRADE",
                "HUSBANDRY",
                "DETONATE",
                "EVENT",
                "SLEEP",
                "VIEW"
            ),
            ClaimPermission.entries.map { it.name }.toSet()
        )
    }

    @Test
    fun `each permission exposes unique matching localization keys`() {
        val nameKeys = ClaimPermission.entries.map { it.nameKey }
        val loreKeys = ClaimPermission.entries.map { it.loreKey }

        assertEquals(nameKeys.size, nameKeys.toSet().size)
        assertEquals(loreKeys.size, loreKeys.toSet().size)

        ClaimPermission.entries.forEach { permission ->
            val id = permission.name.lowercase()
            assertEquals("permission.$id.name", permission.nameKey)
            assertEquals("permission.$id.lore", permission.loreKey)
            assertTrue(permission.nameKey.isNotBlank())
            assertTrue(permission.loreKey.isNotBlank())
        }
    }
}

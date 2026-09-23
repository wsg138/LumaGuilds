package net.lumalyte.lg.interaction.menus.guild

import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier
import java.nio.file.Path
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Contracts for the focused Paper 26.2 Guild Home preview.
 *
 * The entire home-page presentation is one Nexo glyph texture. There are intentionally no
 * category item models or filler/hitbox items: Bukkit click mapping makes empty slots interactive.
 */
internal class GuildDashboardFillerItemTest {

    @Test
    fun `GuildDashboard does not have a fillBackground method`() {
        val methods = GuildDashboard::class.java.declaredMethods.map { it.name }
        assertFalse(methods.contains("fillBackground"), "GuildDashboard must not add filler panes: $methods")
    }

    @Test
    fun `GuildDashboard does not have a FILLER_NEXO_ID constant`() {
        val fields = GuildDashboard::class.java.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && Modifier.isPublic(it.modifiers) }
            .map { it.name }
        val companionFields = GuildDashboard::class.java.declaredClasses
            .filter { it.simpleName == "Companion" }
            .flatMap { it.declaredFields.map { field -> field.name } }
        val allStatics = fields + companionFields

        assertTrue(allStatics.none { it.uppercase().contains("FILLER") }, "Unexpected filler field: $allStatics")
    }

    @Test
    fun `GuildDashboard is one integrated native home skin`() {
        val source = dashboardSource()

        assertTrue(source.contains("Bukkit.createInventory(newHolder, 54, redesignTitle())"))
        assertTrue(source.contains("Card.entries"))
        assertTrue(source.contains("AdventureUtils.NEXO_SERIALIZER"))
        assertTrue(source.contains("<glyph:guild_redesign_bg_home_6_row>"))
        assertTrue(source.contains("mapCard("))
        assertFalse(source.contains("ChestGui("), "Paper 26.2 home preview must not depend on InventoryFramework")
        assertFalse(source.contains("NexoItemProvider"), "Icons are baked into the background skin")
        assertFalse(source.contains("inventory.setItem("), "Top GUI slots must remain visually empty")
        assertFalse(source.contains("lg_redesign_hitbox"), "Empty Bukkit slots are the hitboxes")
    }

    @Test
    fun `GuildDashboard preview clicks stay on the native home screen`() {
        val source = dashboardSource()

        assertTrue(source.contains("cardBySlot[rawSlot]"))
        assertTrue(source.contains("player.sendActionBar"))
        assertFalse(source.contains("createGuildBankMenu(menuNavigator, player, guild)"))
        assertFalse(source.contains("GuildHomeSectionMenu.Section.MEMBERS"))
    }

    private fun dashboardSource(): String = Path.of(System.getProperty("user.dir"))
        .resolve("src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildDashboard.kt")
        .toFile()
        .readText()
}

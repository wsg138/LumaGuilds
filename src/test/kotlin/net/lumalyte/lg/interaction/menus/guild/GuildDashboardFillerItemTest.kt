package net.lumalyte.lg.interaction.menus.guild

import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier
import java.nio.file.Path
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Contracts for the focused Guild Home preview.
 *
 * The redesign intentionally uses a Nexo background glyph plus eight oversized category symbols.
 * Inventory filler panes are still forbidden: the background owns the visual structure and only
 * meaningful controls/hitboxes should occupy slots.
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
    fun `GuildDashboard is the six row eight card Guild Home`() {
        val source = dashboardSource()

        assertTrue(source.contains("ChestGui(6, redesignTitle(\"Guild Home\"))"))
        assertTrue(source.contains("Card.entries"))
        assertTrue(source.contains("lg_redesign_hitbox"))

        listOf(
            "lg_redesign_members",
            "lg_redesign_money",
            "lg_redesign_level",
            "lg_redesign_homes",
            "lg_redesign_allies",
            "lg_redesign_parties",
            "lg_redesign_customize",
            "lg_redesign_settings",
        ).forEach { nexoId ->
            assertTrue(source.contains(nexoId), "Missing Guild Home card icon $nexoId")
        }
    }

    @Test
    fun `GuildDashboard routes cards to the intended live destinations`() {
        val source = dashboardSource()

        assertTrue(source.contains("createGuildBankMenu(menuNavigator, player, guild)"))
        assertTrue(source.contains("createGuildHomeMenu(menuNavigator, player, guild)"))
        assertTrue(source.contains("createGuildPartyManagementMenu(menuNavigator, player, guild)"))
        assertTrue(source.contains("createGuildSettingsMenu(menuNavigator, player, guild)"))
        assertTrue(source.contains("GuildHomeSectionMenu.Section.MEMBERS"))
        assertTrue(source.contains("GuildHomeSectionMenu.Section.LEVEL"))
        assertTrue(source.contains("GuildHomeSectionMenu.Section.ALLIES"))
        assertTrue(source.contains("GuildHomeSectionMenu.Section.CUSTOMIZE"))
    }

    private fun dashboardSource(): String = Path.of(System.getProperty("user.dir"))
        .resolve("src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildDashboard.kt")
        .toFile()
        .readText()
}

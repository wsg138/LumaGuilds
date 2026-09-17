package net.lumalyte.lg.interaction.menus.guild

import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier
import java.nio.file.Path
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Structural contract for the redesigned GuildDashboard.
 *
 * The dashboard intentionally uses the normal inventory background rather than filling empty slots
 * with decorative panes. Its top-level navigation is the eight player-intent sections defined by
 * GuildRedesignSectionMenu.Section.
 */
internal class GuildDashboardFillerItemTest {

    private fun dashboardSource(): String = Path.of(System.getProperty("user.dir"))
        .resolve("src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildDashboard.kt")
        .toFile()
        .readText()

    @Test
    fun `GuildDashboard does not have a fillBackground method`() {
        val methods = GuildDashboard::class.java.declaredMethods.map { it.name }
        assertFalse(
            methods.contains("fillBackground"),
            "GuildDashboard must not have a fillBackground method. Found: $methods",
        )
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

        assertTrue(
            allStatics.none { it.uppercase().contains("FILLER") },
            "GuildDashboard must not have a FILLER constant. Found: $allStatics",
        )
    }

    @Test
    fun `GuildDashboard declares all eight player intent sections`() {
        val source = dashboardSource()
        val expectedSections = listOf(
            "MEMBERS",
            "MONEY",
            "LEVEL",
            "HOMES",
            "ALLIES",
            "PARTIES",
            "CUSTOMIZE",
            "SETTINGS",
        )

        expectedSections.forEach { section ->
            assertTrue(
                source.contains("GuildRedesignSectionMenu.Section.$section"),
                "Dashboard is missing redesigned section $section",
            )
        }
        assertTrue(source.contains("ChestGui(6"))
        assertTrue(source.contains("addCard(pane"))
    }

    @Test
    fun `GuildDashboard exposes the feature index and live member management`() {
        val source = dashboardSource()

        assertTrue(source.contains("GuildRedesignFeatureIndexMenu(menuNavigator, player, guild)"))
        assertTrue(source.contains("menuFactory.createGuildMemberManagementMenu(menuNavigator, player, guild)"))
        assertFalse(source.contains("addNavButton("))
        assertFalse(source.contains("GuildRedesignMemberDirectoryMenu"))
    }
}

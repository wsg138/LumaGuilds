package net.lumalyte.lg.utils

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer

/**
 * Shared visual surfaces for the LumaGuilds redesign.
 *
 * Every redesigned screen belongs to one of these families instead of inventing its own layout.
 */
enum class MenuSurface(val glyphKey: String) {
    HOME("guild_redesign_bg_home_6_row"),
    GRID("guild_redesign_bg_grid_6_row"),
    LIST("guild_redesign_bg_list_6_row"),
    DETAIL("guild_redesign_bg_detail_6_row"),
    DANGER("guild_redesign_bg_danger_6_row"),
}

/** Builds ChestGui titles backed by Nexo font-glyph overlays. */
object MenuTitleBuilder {
    private const val HORIZONTAL_OFFSET = "<shift:-9>"
    private const val REWIND_TO_TITLE = "<shift:-161>"

    /**
     * Compatibility path for screens still being structurally migrated.
     *
     * Six-row guild inventories inherit the shared redesign frame immediately so navigating from a
     * redesigned hub never drops the player back into an unrelated legacy visual theme. Smaller
     * inventories retain their exact legacy-sized glyph until their layout is converted to one of
     * the fixed six-row redesign surfaces.
     */
    fun build(theme: GuiTheme = GuiTheme.NEUTRAL, rows: Int, title: String = ""): String {
        if (rows == 6) return compose(MenuSurface.GRID.glyphKey, title)
        val themeKey = theme.name.lowercase()
        val glyphName = "guild_bg_${themeKey}_${rows}_row"
        return compose(glyphName, title)
    }

    /**
     * Use this for every redesigned player-facing guild inventory.
     * All redesign surfaces are six rows so navigation stays in a fixed location.
     */
    fun redesign(surface: MenuSurface, title: String = ""): String = compose(surface.glyphKey, title)

    /** Localized GUI labels are Components; flatten only the title text before composing glyph markup. */
    fun redesign(surface: MenuSurface, title: Component): String =
        compose(surface.glyphKey, PlainTextComponentSerializer.plainText().serialize(title))

    private fun compose(glyphName: String, title: String): String {
        val prefix = "${HORIZONTAL_OFFSET}<glyph:${glyphName}>"
        return if (title.isNotEmpty()) "$prefix$REWIND_TO_TITLE$title" else prefix
    }
}

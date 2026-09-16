package net.lumalyte.lg.interaction.menus.guild

import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class GuildRedesignAssetContractTest {

    private val root = File(System.getProperty("user.dir"))
        .resolve("menu-redesign/nexo/pack/assets/lumaguilds/textures/gui")

    private val iconNames = listOf(
        "lg_redesign_members.png",
        "lg_redesign_money.png",
        "lg_redesign_level.png",
        "lg_redesign_homes.png",
        "lg_redesign_allies.png",
        "lg_redesign_parties.png",
        "lg_redesign_customize.png",
        "lg_redesign_settings.png",
    )

    private val surfaceNames = listOf(
        "guild_redesign_bg_home_6_row.png",
        "guild_redesign_bg_grid_6_row.png",
        "guild_redesign_bg_list_6_row.png",
        "guild_redesign_bg_detail_6_row.png",
        "guild_redesign_bg_danger_6_row.png",
    )

    @Test
    fun `all redesign surfaces have the expected source size`() {
        surfaceNames.forEach { name ->
            val image = read(root.resolve(name))
            assertEquals(256, image.width, "$name must remain 256px wide")
            assertEquals(256, image.height, "$name must remain 256px high")
            assertTrue(image.colorModel.hasAlpha(), "$name must remain an RGBA PNG")
        }
    }

    @Test
    fun `all category symbols are detailed transparent 64px resource pack icons`() {
        val iconDir = root.resolve("icons")

        iconNames.forEach { name ->
            val image = read(iconDir.resolve(name))
            assertEquals(64, image.width, "$name must remain 64px wide")
            assertEquals(64, image.height, "$name must remain 64px high")
            assertTrue(image.colorModel.hasAlpha(), "$name must retain transparency")

            var transparentPixels = 0
            var visiblePixels = 0
            val colors = linkedSetOf<Int>()
            for (y in 0 until image.height) {
                for (x in 0 until image.width) {
                    val argb = image.getRGB(x, y)
                    val alpha = argb ushr 24 and 0xFF
                    if (alpha == 0) {
                        transparentPixels++
                    } else {
                        visiblePixels++
                        colors += argb and 0xFFFFFF
                    }
                }
            }

            val total = image.width * image.height
            val coverage = visiblePixels.toDouble() / total
            assertTrue(visiblePixels > 0, "$name must contain a visible Minecraft-style symbol")
            assertTrue(transparentPixels > total / 4, "$name must leave substantial transparency; panels belong to the background")
            assertTrue(coverage in 0.08..0.72, "$name visible coverage $coverage is outside the icon-system range")
            assertTrue(colors.size >= 4, "$name is too flat; V3 icons require material shading/detail")
            assertTrue(colors.size <= 96, "$name has too many colors for coherent pixel-art treatment (${colors.size})")

            listOf(0 to 0, 63 to 0, 0 to 63, 63 to 63).forEach { (x, y) ->
                val alpha = image.getRGB(x, y) ushr 24 and 0xFF
                assertEquals(0, alpha, "$name must keep transparent corners instead of baking in a card")
            }
        }
    }

    @Test
    fun `hitbox remains one fully transparent pixel`() {
        val image = read(root.resolve("icons/lg_redesign_hitbox.png"))
        assertEquals(1, image.width)
        assertEquals(1, image.height)
        assertEquals(0, image.getRGB(0, 0) ushr 24 and 0xFF)
    }

    private fun read(file: File): BufferedImage {
        assertTrue(file.isFile, "Missing redesign asset: ${file.relativeTo(File(System.getProperty("user.dir")))}")
        return assertNotNull(ImageIO.read(file), "Could not decode redesign asset: $file")
    }
}

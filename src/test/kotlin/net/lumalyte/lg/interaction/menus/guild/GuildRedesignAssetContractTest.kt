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

    private val approvedIconRgb = setOf(
        0x080E14,
        0x0D181F,
        0x1F3440,
        0x2C8B8E,
        0x3D5362,
        0x49B5B1,
        0x526C7C,
        0x899AA3,
        0xAA4E4F,
        0xDCE7EA,
        0xE6B549,
        0xF4F8F9,
        0xF8CC66,
    )

    @Test
    fun `dashboard background has the expected source size`() {
        val image = read(root.resolve("guild_redesign_bg_6_row.png"))
        assertEquals(256, image.width)
        assertEquals(256, image.height)
    }

    @Test
    fun `all category symbols are 64px transparent PNGs using the shared palette`() {
        val iconDir = root.resolve("icons")

        iconNames.forEach { name ->
            val image = read(iconDir.resolve(name))
            assertEquals(64, image.width, "$name must remain 64px wide")
            assertEquals(64, image.height, "$name must remain 64px high")
            assertTrue(image.colorModel.hasAlpha(), "$name must retain transparency")

            var transparentPixels = 0
            var visiblePixels = 0
            val unexpected = linkedSetOf<String>()

            for (y in 0 until image.height) {
                for (x in 0 until image.width) {
                    val argb = image.getRGB(x, y)
                    val alpha = argb ushr 24 and 0xFF
                    if (alpha == 0) {
                        transparentPixels++
                        continue
                    }

                    visiblePixels++
                    val rgb = argb and 0xFFFFFF
                    if (rgb !in approvedIconRgb) unexpected += "#%06X".format(rgb)
                }
            }

            assertTrue(visiblePixels > 0, "$name must contain a visible symbol")
            assertTrue(transparentPixels > 0, "$name must not contain a baked full-card background")
            assertTrue(unexpected.isEmpty(), "$name contains colors outside the shared UI palette: $unexpected")
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

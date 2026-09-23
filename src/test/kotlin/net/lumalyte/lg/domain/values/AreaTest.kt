package net.lumalyte.lg.domain.values

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AreaTest {

    @Test
    fun `constructor normalizes reversed corners on both axes`() {
        val area = Area(Position2D(9, 12), Position2D(3, 4))

        assertEquals(Position2D(3, 4), area.lowerPosition2D)
        assertEquals(Position2D(9, 12), area.upperPosition2D)
    }

    @Test
    fun `containment is inclusive on every edge and excludes outside positions`() {
        val area = Area(Position2D(2, 5), Position2D(4, 7))

        assertTrue(area.isPositionInArea(Position2D(2, 5)))
        assertTrue(area.isPositionInArea(Position2D(4, 7)))
        assertTrue(area.isPositionInArea(Position2D(3, 6)))
        assertFalse(area.isPositionInArea(Position2D(1, 6)))
        assertFalse(area.isPositionInArea(Position2D(5, 6)))
        assertFalse(area.isPositionInArea(Position2D(3, 4)))
        assertFalse(area.isPositionInArea(Position2D(3, 8)))
    }

    @Test
    fun `corner detection accepts only the four normalized corners`() {
        val area = Area(Position2D(2, 5), Position2D(4, 7))

        assertEquals(
            setOf(Position2D(2, 5), Position2D(2, 7), Position2D(4, 5), Position2D(4, 7)),
            area.getCornerBlockPositions().toSet()
        )
        area.getCornerBlockPositions().forEach { assertTrue(area.isPositionInCorner(it)) }
        assertFalse(area.isPositionInCorner(Position2D(3, 5)))
        assertFalse(area.isPositionInCorner(Position2D(3, 6)))
    }

    @Test
    fun `block count and axis lengths include both boundary blocks`() {
        val area = Area(Position2D(-2, 10), Position2D(2, 12))

        assertEquals(5, area.getXLength())
        assertEquals(3, area.getZLength())
        assertEquals(15, area.getBlockCount())
    }

    @Test
    fun `overlap is inclusive for touching boundary blocks but rejects separated areas`() {
        val area = Area(Position2D(0, 0), Position2D(4, 4))

        assertTrue(area.isAreaOverlap(Area(Position2D(4, 2), Position2D(8, 3))))
        assertTrue(area.isAreaOverlap(Area(Position2D(1, 1), Position2D(2, 2))))
        assertFalse(area.isAreaOverlap(Area(Position2D(5, 0), Position2D(8, 4))))
        assertFalse(area.isAreaOverlap(Area(Position2D(-4, 5), Position2D(4, 8))))
    }

    @Test
    fun `direct adjacency works on all four sides and not across a gap`() {
        val area = Area(Position2D(0, 0), Position2D(2, 2))

        assertTrue(area.isAreaAdjacent(Area(Position2D(0, -3), Position2D(2, -1))))
        assertTrue(area.isAreaAdjacent(Area(Position2D(0, 3), Position2D(2, 5))))
        assertTrue(area.isAreaAdjacent(Area(Position2D(-3, 0), Position2D(-1, 2))))
        assertTrue(area.isAreaAdjacent(Area(Position2D(3, 0), Position2D(5, 2))))

        assertFalse(area.isAreaAdjacent(Area(Position2D(0, -4), Position2D(2, -2))))
        assertFalse(area.isAreaAdjacent(Area(Position2D(4, 0), Position2D(6, 2))))
    }

    @Test
    fun `diagonal contact is not direct adjacency`() {
        val area = Area(Position2D(0, 0), Position2D(2, 2))

        assertFalse(area.isAreaAdjacent(Area(Position2D(3, 3), Position2D(5, 5))))
        assertFalse(area.isAreaAdjacent(Area(Position2D(-3, -3), Position2D(-1, -1))))
    }

    @Test
    fun `chunk enumeration covers every occupied chunk including negative coordinates`() {
        val area = Area(Position2D(-17, -1), Position2D(16, 16))

        assertEquals(
            setOf(
                Position2D(-2, -1), Position2D(-2, 0), Position2D(-2, 1),
                Position2D(-1, -1), Position2D(-1, 0), Position2D(-1, 1),
                Position2D(0, -1), Position2D(0, 0), Position2D(0, 1),
                Position2D(1, -1), Position2D(1, 0), Position2D(1, 1)
            ),
            area.getChunks().toSet()
        )
    }
}

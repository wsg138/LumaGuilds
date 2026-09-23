package net.lumalyte.lg.domain.values

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PositionTest {

    @Test
    fun `chunk conversion uses sixteen block boundaries`() {
        assertEquals(Position2D(0, 0), Position2D(0, 0).getChunk())
        assertEquals(Position2D(0, 0), Position2D(15, 15).getChunk())
        assertEquals(Position2D(1, 1), Position2D(16, 16).getChunk())
        assertEquals(Position2D(1, 1), Position2D(31, 31).getChunk())
        assertEquals(Position2D(2, 2), Position2D(32, 32).getChunk())
    }

    @Test
    fun `negative coordinates use floor style chunk boundaries`() {
        assertEquals(Position2D(-1, -1), Position2D(-1, -1).getChunk())
        assertEquals(Position2D(-1, -1), Position2D(-16, -16).getChunk())
        assertEquals(Position2D(-2, -2), Position2D(-17, -17).getChunk())
        assertEquals(Position2D(-2, -2), Position2D(-32, -32).getChunk())
        assertEquals(Position2D(-3, -3), Position2D(-33, -33).getChunk())
    }

    @Test
    fun `axes convert independently across zero`() {
        assertEquals(Position2D(-2, 2), Position2D(-17, 47).getChunk())
        assertEquals(Position2D(2, -2), Position2D(47, -17).getChunk())
        assertEquals(Position2D(-1, 0), Position2D(-1, 15).getChunk())
        assertEquals(Position2D(0, -1), Position2D(15, -1).getChunk())
    }

    @Test
    fun `three dimensional positions ignore y for chunk conversion`() {
        assertEquals(Position2D(1, -2), Position3D(31, 255, -17).getChunk())
        assertEquals(Position2D(1, -2), Position3D(31, -64, -17).getChunk())
    }
}

package net.lumalyte.lg.domain.values

import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChatChannelContractTest {

    @Test
    fun `chat channel surface remains explicit`() {
        assertEquals(
            listOf(ChatChannel.GUILD, ChatChannel.ALLY, ChatChannel.PARTY, ChatChannel.PUBLIC),
            ChatChannel.entries
        )
    }

    @Test
    fun `rosechat channel ids stay bound to the configured contract`() {
        assertEquals("guild", ChatChannelIds.GUILD)
        assertEquals("guild-ally", ChatChannelIds.ALLY)
        assertEquals("guild-modchat", ChatChannelIds.MODCHAT)
        assertEquals(3, setOf(ChatChannelIds.GUILD, ChatChannelIds.ALLY, ChatChannelIds.MODCHAT).size)
    }

    @Test
    fun `visibility defaults allow every private guild channel`() {
        val playerId = UUID.randomUUID()
        val settings = ChatVisibilitySettings(playerId)

        assertEquals(playerId, settings.playerId)
        assertTrue(settings.guildChatVisible)
        assertTrue(settings.allyChatVisible)
        assertTrue(settings.partyChatVisible)
    }

    @Test
    fun `visibility preserves explicit disabled state`() {
        val playerId = UUID.randomUUID()
        val settings = ChatVisibilitySettings(
            playerId = playerId,
            guildChatVisible = false,
            allyChatVisible = false,
            partyChatVisible = false
        )

        assertEquals(playerId, settings.playerId)
        assertEquals(false, settings.guildChatVisible)
        assertEquals(false, settings.allyChatVisible)
        assertEquals(false, settings.partyChatVisible)
    }

    @Test
    fun `rate limit defaults are a clean unused window`() {
        val playerId = UUID.randomUUID()
        val rateLimit = ChatRateLimit(playerId)

        assertEquals(playerId, rateLimit.playerId)
        assertEquals(0L, rateLimit.lastAnnounceTime)
        assertEquals(0L, rateLimit.lastPingTime)
        assertEquals(0, rateLimit.announceCount)
        assertEquals(0, rateLimit.pingCount)
    }

    @Test
    fun `rate limit preserves explicit timestamps and counters`() {
        val playerId = UUID.randomUUID()
        val rateLimit = ChatRateLimit(
            playerId = playerId,
            lastAnnounceTime = 1234L,
            lastPingTime = 5678L,
            announceCount = 2,
            pingCount = 3
        )

        assertEquals(1234L, rateLimit.lastAnnounceTime)
        assertEquals(5678L, rateLimit.lastPingTime)
        assertEquals(2, rateLimit.announceCount)
        assertEquals(3, rateLimit.pingCount)
    }
}

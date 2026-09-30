package net.lumalyte.lg.di

import net.lumalyte.lg.infrastructure.litebans.LiteBansStrikeListener
import net.lumalyte.lg.infrastructure.litebans.StrikeBackfillService
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.koin.core.KoinApplication

class OptionalLiteBansModuleTest {
    @Test
    fun `absent LiteBans does not register integration definitions`() {
        val application = KoinApplication.init().modules(guildsModule(false))
        try {
            assertNull(application.koin.getOrNull<LiteBansStrikeListener>())
            assertNull(application.koin.getOrNull<StrikeBackfillService>())
        } finally {
            application.close()
        }
    }
}

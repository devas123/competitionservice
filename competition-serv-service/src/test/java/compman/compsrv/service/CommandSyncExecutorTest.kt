package compman.compsrv.service

import arrow.fx.IODispatchers.CommonPool
import compman.compsrv.model.events.EventDTO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CommandSyncExecutorTest {
    companion object {
        private val log: Logger = LoggerFactory.getLogger("testlog")
    }


    @Test
    fun testCommandCache() {
        val key = "test"
        val commandCache = CommandSyncExecutor()
        val mono = commandCache.executeCommand(key) {
            log.info("Execute command")
        }
          CoroutineScope(CommonPool).launch {
            log.info("coroutine started. delay")
            delay(2000)
            commandCache.commandCallback(key, arrayOf(EventDTO().setId("TEST")))
            log.info("completing promise.done")
        }
        val kk = commandCache.waitForResult(key, java.time.Duration.ofMillis(3000))
        assertNotNull(kk)
        assertEquals(1, kk.size)
        assertNotNull(kk[0].id)
        assertEquals("TEST", kk[0].id)
        val tt = mono.block(java.time.Duration.ofMillis(3000))
        assertNotNull(tt)
        assertEquals(1, tt.size)
        assertNotNull(tt[0].id)
        assertEquals("TEST", tt[0].id)
    }
}

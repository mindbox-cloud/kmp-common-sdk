package cloud.mindbox.mobile_sdk.utils

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.*
import kotlin.time.Duration

class MindboxUtilsTest {

    @Test
    fun `stopwatch start stop in 1 seconds`() = runBlocking {
        MindboxUtils.Stopwatch.start("test")
        delay(1000)
        val duration: Duration? = MindboxUtils.Stopwatch.stop("test")
        assertEquals(duration?.inWholeSeconds, 1)
    }

    @Test
    fun `stopwatch stop not started tag`() {
        MindboxUtils.Stopwatch.start("test2")
        val duration: Duration? = MindboxUtils.Stopwatch.stop("test")
        assertNull(duration)
    }

    @Test
    fun `stopwatch track 1 seconds twice`() = runBlocking {
        MindboxUtils.Stopwatch.start("test3")
        delay(1000)
        val duration1: Duration? = MindboxUtils.Stopwatch.track("test3")
        assertEquals(1, duration1?.inWholeSeconds)
        delay(1000)
        val duration2: Duration? = MindboxUtils.Stopwatch.track("test3")
        assertEquals(2, duration2?.inWholeSeconds)
    }

    @Test
    fun `stopwatch stop twice`() {
        MindboxUtils.Stopwatch.start("test4")
        val duration1: Duration? = MindboxUtils.Stopwatch.stop("test4")
        assertNotNull(duration1)

        val duration2: Duration? = MindboxUtils.Stopwatch.stop("test4")
        assertNull(duration2)
    }
}

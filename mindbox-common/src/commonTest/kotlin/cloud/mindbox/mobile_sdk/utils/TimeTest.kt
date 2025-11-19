package cloud.mindbox.mobile_sdk.utils

import kotlin.test.*

class TimeTest {

    @Test
    fun `get system nano time returns a positive value`() {
        val nanoTime = getSystemNanoTime()
        assertTrue(nanoTime > 0)
    }

}

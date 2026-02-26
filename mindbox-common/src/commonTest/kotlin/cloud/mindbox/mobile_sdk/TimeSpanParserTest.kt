package cloud.mindbox.mobile_sdk

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TimeSpanParserTest {
    @Test
    fun testParseToMillis_basic() {
        assertEquals(129842000, TimeSpanParser.parseToMillis("1.12:04:02"))
        assertEquals(1800000, TimeSpanParser.parseToMillis("00:30:00"))
        assertEquals(321930500, TimeSpanParser.parseToMillis("3.17:25:30.5000000"))
        assertEquals(0, TimeSpanParser.parseToMillis("00:00:00"))
        assertEquals(123, TimeSpanParser.parseToMillis("00:00:00.123"))
    }

    @Test
    fun testParseToMillis_negative() {
        assertEquals(-1800123, TimeSpanParser.parseToMillis("-00:30:00.123"))
        assertEquals(-321930500, TimeSpanParser.parseToMillis("-3.17:25:30.5000000"))
    }

    @Test
    fun testParseToMillis_noDays() {
        assertEquals(3723000, TimeSpanParser.parseToMillis("01:02:03"))
    }

    @Test
    fun testParseToMillis_fractionalPadding() {
        assertEquals(120, TimeSpanParser.parseToMillis("00:00:00.12"))
        assertEquals(0, TimeSpanParser.parseToMillis("00:00:00.0000001"))
    }

    @Test
    fun testParseToMillis_invalidFormat() {
        val cases = listOf(
            "6" to null,
            "6:12" to null,
            "1.6:12" to null,
            "1.6:12.1" to null,
            "6.24:14:45" to null,
            "6.99:14:45" to null,
            "6.00:24:99" to null,
            "6.00:99:45" to null,
            "6.00:60:45" to null,
            "6.00:44:60" to null,
            "6.00:44:60" to null,
            "6.99:99:99" to null,
            "1:1:1:1:1" to null,
            "qwe" to null,
            "" to null,
            "999999999:0:0" to null,
            "0:0:0.12345678" to null,
            ".0:0:0.1234567" to null,
            "0:0:0." to null,
            "0:000:0" to null,
            "00:000:00" to null,
            "000:00:00" to null,
            "00:00:000" to null,
            "+0:0:0" to null,
            "12345678901234567890.00:00:00.00" to null
        )
        for ((input, _) in cases) {
            assertFailsWith<IllegalArgumentException> {
                TimeSpanParser.parseToMillis(input)
            }
        }
    }

    @Test
    fun testParseToMillis_validCases() {
        val cases = listOf(
            "0:0:0.1234567" to 123L,
            "0:0:0.1" to 100L,
            "0:0:0.01" to 10L,
            "0:0:0.001" to 1L,
            "0:0:0.0001" to 0L,
            "01.01:01:01.10" to 90061100L,
            "1.1:1:1.1" to 90061100L,
            "1.1:1:1" to 90061000L,
            "99.23:59:59" to 8639999000L,
            "999.23:59:59" to 86399999000L,
            "6:12:14" to 22334000L,
            "6.12:14:45" to 562485000L,
            "1.00:00:00" to 86400000L,
            "0.00:00:00.0" to 0L,
            "00:00:00" to 0L,
            "-00:00:00" to 0L,
            "0:0:0" to 0L,
            "-0:0:0" to 0L,
            "-0:0:0.001" to -1L,
            "-1.0:0:0" to -86400000L,
            "10675199.02:48:05.4775807" to 922337203685477L,
            "-10675199.02:48:05.4775808" to -922337203685477L
        )
        for ((input, expected) in cases) {
            assertEquals(expected, TimeSpanParser.parseToMillis(input), "Failed for '$input'")
        }
    }

    @Test
    fun testParseTimeSpanToMillisExtension() {
        assertEquals(129842000, "1.12:04:02".parseTimeSpanToMillis())
        assertEquals(1800000, "00:30:00".parseTimeSpanToMillis())
        assertEquals(321930500, "3.17:25:30.5000000".parseTimeSpanToMillis())
        assertEquals(0, "00:00:00".parseTimeSpanToMillis())
        assertEquals(-123, "-00:00:00.123".parseTimeSpanToMillis())
    }

    @Test
    fun `parseMillisToTimeSpan zero`() {
        assertEquals("0:00:00:00.0000000", 0L.millisToTimeSpan())
    }

    @Test
    fun `parseMillisToTimeSpan adr example 225ms`() {
        assertEquals("0:00:00:00.2250000", 225L.millisToTimeSpan())
    }

    @Test
    fun `parseMillisToTimeSpan milliseconds fraction`() {
        assertEquals("0:00:00:00.0010000", 1L.millisToTimeSpan())
        assertEquals("0:00:00:00.1000000", 100L.millisToTimeSpan())
        assertEquals("0:00:00:00.9990000", 999L.millisToTimeSpan())
    }

    @Test
    fun `parseMillisToTimeSpan whole seconds`() {
        assertEquals("0:00:00:01.0000000", 1_000L.millisToTimeSpan())
        assertEquals("0:00:00:59.0000000", 59_000L.millisToTimeSpan())
    }

    @Test
    fun `parseMillisToTimeSpan minutes`() {
        assertEquals("0:00:01:00.0000000", 60_000L.millisToTimeSpan())
        assertEquals("0:00:59:59.0000000", 3_599_000L.millisToTimeSpan())
    }

    @Test
    fun `parseMillisToTimeSpan hours`() {
        assertEquals("0:01:00:00.0000000", 3_600_000L.millisToTimeSpan())
        assertEquals("0:23:59:59.0000000", 86_399_000L.millisToTimeSpan())
    }

    @Test
    fun `parseMillisToTimeSpan days`() {
        assertEquals("1:00:00:00.0000000", 86_400_000L.millisToTimeSpan())
        assertEquals("3:17:25:30.5000000", 321_930_500L.millisToTimeSpan())
        assertEquals("1:01:01:01.1000000", 90_061_100L.millisToTimeSpan())
    }

    @Test
    fun `parseMillisToTimeSpan negative values coerced to zero`() {
        assertEquals("0:00:00:00.0000000", (-1L).millisToTimeSpan())
        assertEquals("0:00:00:00.0000000", (-999L).millisToTimeSpan())
        assertEquals("0:00:00:00.0000000", Long.MIN_VALUE.millisToTimeSpan())
    }

    @Test
    fun `parseMillisToTimeSpan output matches expected structure`() {
        val structureRegex = Regex("""\d+:\d{2}:\d{2}:\d{2}\.\d{7}""")
        val inputCases = listOf(
            0L,
            225L,
            1_000L,
            3_600_000L,
            86_400_000L,
            321_930_500L,
            90_061_100L,
        )
        for (inputMs in inputCases) {
            val result = inputMs.millisToTimeSpan()
            assertTrue(structureRegex.matches(result), "Bad format for ${inputMs}ms: '$result'")
        }
    }
}

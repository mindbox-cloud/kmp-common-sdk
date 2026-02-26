package cloud.mindbox.mobile_sdk

import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Parses a string in .NET TimeSpan format (e.g. "1.12:24:02", "00:30:00", "3.17:25:30.5000000")
 * and returns the duration in milliseconds.
 *
 * Supported format: [-][\d.]hh:mm:ss[.fffffff]
 *
 * @param timeSpanString the string to parse
 * @return duration in milliseconds
 * @throws IllegalArgumentException if the string is not a valid TimeSpan
 *
 * @see <a href="https://learn.microsoft.com/en-us/dotnet/standard/base-types/standard-timespan-format-strings">.NET TimeSpan format</a>
 */
internal object TimeSpanParser {
    private const val MILLIS_PER_SECOND = 1000L
    private const val SECONDS_PER_MINUTE = 60
    private const val SECONDS_PER_HOUR = 3600
    private const val SECONDS_PER_DAY = 86400
    private const val FRACTION_SCALE = 10_000L
    private const val FRACTION_DIGITS = 7

    internal fun parseToMillis(timeSpanString: String): Long {
        val regex = """(-)?(\d+\.)?([01]?\d|2[0-3]):([0-5]?\d):([0-5]?\d)(\.\d{1,7})?""".toRegex()
        val matchResult = regex.matchEntire(timeSpanString)
            ?: throw IllegalArgumentException("Invalid timeSpan format")
        val (sign, days, hours, minutes, seconds, fraction) = matchResult.destructured
        val daysCorrected = if (days.isBlank()) "0" else days.dropLast(1)

        val duration = try {
            daysCorrected.toLong().days +
                hours.toLong().hours +
                minutes.toLong().minutes +
                (seconds + fraction).toDouble().seconds
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException("Invalid timeSpan format", e)
        }

        return if (sign == "-") duration.inWholeMilliseconds * -1 else duration.inWholeMilliseconds
    }

    /**
     * Formats a duration in milliseconds as a .NET TimeSpan string.
     *
     * Output format: [-][\d.]hh:mm:ss.fffffff
     *  - days part ([\d.]) is included only when days > 0, separated from hours by a dot
     *  - hours, minutes, seconds are zero-padded to 2 digits
     *  - fractional seconds are always 7 digits (100-nanosecond ticks)
     *  - negative durations are prefixed with '-' and formatted by absolute value
     *
     * Examples:
     *  - 0 ms          → "00:00:00.0000000"
     *  - 225 ms        → "00:00:00.2250000"
     *  - 86_400_000 ms → "1.00:00:00.0000000"
     *  - -1_800_000 ms → "-00:30:00.0000000"
     *
     * The output is compatible with [parseToMillis]: parsing the result returns the original value.
     *
     * @param timeInMillis duration in milliseconds; negative values are formatted with a leading '-'
     * @return string in .NET TimeSpan format
     */
    internal fun formatMillisAsTimeSpan(timeInMillis: Long): String {
        val isNegative = timeInMillis < 0
        val absMillis = when {
            !isNegative -> timeInMillis
            timeInMillis == Long.MIN_VALUE -> Long.MAX_VALUE
            else -> -timeInMillis
        }
        val totalSeconds = absMillis / MILLIS_PER_SECOND
        val remainderMillis = (absMillis % MILLIS_PER_SECOND) * FRACTION_SCALE
        val fractionStr = remainderMillis.toString().padStart(FRACTION_DIGITS, '0')
        val days = totalSeconds / SECONDS_PER_DAY
        val hours = (totalSeconds % SECONDS_PER_DAY) / SECONDS_PER_HOUR
        val minutes = (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
        val seconds = totalSeconds % SECONDS_PER_MINUTE
        return buildString {
            if (isNegative) append('-')
            if (days > 0) {
                append(days)
                append('.')
            }
            append(hours.toString().padStart(2, '0'))
            append(':')
            append(minutes.toString().padStart(2, '0'))
            append(':')
            append(seconds.toString().padStart(2, '0'))
            append('.')
            append(fractionStr)
        }
    }
}

@Throws(IllegalArgumentException::class)
public fun String.parseTimeSpanToMillis(): Long = TimeSpanParser.parseToMillis(this)

public fun Long.millisToTimeSpan(): String = TimeSpanParser.formatMillisAsTimeSpan(this)

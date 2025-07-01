package cloud.mindbox.common

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
object TimeSpanParser {
    fun parseToMillis(timeSpanString: String): Long {
        val regex = """(-)?(\d+\.)?([01]?\d|2[0-3]):([0-5]?\d):([0-5]?\d)(\.\d{1,7})?""".toRegex()
        val matchResult = regex.matchEntire(timeSpanString)
            ?: throw IllegalArgumentException("Invalid timeSpan format")
        val (sign, days, hours, minutes, seconds, fraction) = matchResult.destructured
        val daysCorrected = if (days.isBlank()) "0" else days.dropLast(1)

        val duration = daysCorrected.toLong().days +
                hours.toLong().hours +
                minutes.toLong().minutes +
                (seconds + fraction).toDouble().seconds

        return if (sign == "-") duration.inWholeMilliseconds * -1 else duration.inWholeMilliseconds
    }
}

public fun String.parseTimeSpanToMillis(): Long = TimeSpanParser.parseToMillis(this)
package cloud.mindbox.mobile_sdk.utils

import platform.posix.CLOCK_MONOTONIC
import platform.posix.clock_gettime_nsec_np

internal actual fun getSystemNanoTime(): Long {
    return clock_gettime_nsec_np(CLOCK_MONOTONIC.toUInt()).toLong()
}

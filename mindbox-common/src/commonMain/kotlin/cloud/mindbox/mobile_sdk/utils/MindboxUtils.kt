package cloud.mindbox.mobile_sdk.utils

import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

public object MindboxUtils {
    public object Stopwatch {

        public const val INIT_SDK: String = "INIT_SDK"
        public const val GET_PUSH_TOKENS: String = "GET_PUSH_TOKENS"
        public const val INIT_PUSH_SERVICES: String = "INIT_PUSH_SERVICES"

        private val entries: MutableMap<String, Long> by lazy { mutableMapOf() }

        /***
         * Start tracking duration with tag
         */
        public fun start(tag: String) {
            entries[tag] = getSystemNanoTime()
        }

        /***
         * Stop tracking duration from call of [start] with the same tag
         *
         * @return Duration in nanoseconds or null if tag not found
         */
        public fun stop(tag: String): Duration? =
            track(tag)?.also {
                entries.remove(tag)
            }

        /***
         * Track duration from call of [start] with the same tag
         *
         * @return Duration in nanoseconds or null if tag not found
         */
        public fun track(tag: String): Duration? =
            entries[tag]?.let { start ->
                val now = getSystemNanoTime()
                (now - start).nanoseconds
            }
    }
}

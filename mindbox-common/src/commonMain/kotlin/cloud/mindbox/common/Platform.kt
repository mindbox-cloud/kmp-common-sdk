package cloud.mindbox.common

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
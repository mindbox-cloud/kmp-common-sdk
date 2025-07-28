package cloud.mindbox.common

import cloud.mindbox.mobile_sdk.abtests.CustomerAbMixer

public object MindboxCommon {

    public const val VERSION: Long = 1
    public const val VERSION_NAME: String = BuildConfig.VERSION_NAME

    @Suppress("unused")
    private fun check() {
        CustomerAbMixer.impl()
    }
}

internal enum class MindboxCommonPlatform {
    ANDROID,
    IOS,
    UNKNOWN
}

internal expect fun getPlatform(): MindboxCommonPlatform

package cloud.mindbox.common

import cloud.mindbox.mobile_sdk.abmixer.CustomerAbMixer

public object MindboxCommon {

    const val VERSION: Long = 1

    @Suppress("unused")
    private fun check() {
        CustomerAbMixer.impl()
    }

}

internal enum class MindboxCommonPlatform {
    ANDROID,
    IOS,
    UNKNOWN;
}

internal expect fun getPlatform(): MindboxCommonPlatform

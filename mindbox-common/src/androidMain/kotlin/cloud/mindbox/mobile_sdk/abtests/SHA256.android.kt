package cloud.mindbox.mobile_sdk.abtests

import java.security.MessageDigest

@OptIn(ExperimentalUnsignedTypes::class)
internal actual fun ByteArray.sha256(): UByteArray {
    val sha256 = MessageDigest.getInstance("SHA-256").apply { reset() }
    return sha256.digest(this).toUByteArray()
}
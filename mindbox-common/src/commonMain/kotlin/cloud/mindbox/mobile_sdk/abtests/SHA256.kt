package cloud.mindbox.mobile_sdk.abtests

@OptIn(ExperimentalUnsignedTypes::class)
internal expect fun ByteArray.sha256(): UByteArray

package cloud.mindbox.mobile_sdk.abtests

public interface CustomerAbMixer {

    public companion object {
        @Suppress("UNUSED")
        public fun impl(): CustomerAbMixer = CustomerAbMixerImpl()
    }

    public fun stringModulusHash(identifier: String, salt: String): Int
}

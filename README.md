# Mindbox Common KMP library for Mindbox SDK

[![Maven Central](https://img.shields.io/maven-central/v/cloud.mindbox/mindbox-common)](https://central.sonatype.com/artifact/cloud.mindbox/mindbox-common)

This repository contains the source code for the common Kotlin Multiplatform (KMP) library used by the [Mindbox Android SDK](https://github.com/mindbox-cloud/android-sdk) and [Mindbox iOS SDK](https://github.com/mindbox-cloud/ios-sdk).

## About this library

This library is intended for internal use by the Mindbox mobile SDKs. It contains shared logic for features.

**As a user of the Mindbox platform, you most likely do not need to use this library directly.** Instead, please refer to the documentation for the respective platform-specific SDKs:

*   [Android SDK Documentation](https://developers.mindbox.ru/docs/android-sdk-initialization)
*   [iOS SDK Documentation](https://developers.mindbox.ru/docs/ios-sdk-initialization)

## Building the project

If you wish to contribute to the development of this library, you can build it using the following instructions.

### Android

The Android artifact is a standard `.aar` library. You can build it using Gradle:

```bash
./gradlew :mindbox-common:assembleRelease
```

The output will be located in `mindbox-common/build/outputs/aar/`.

### iOS

The iOS artifact is an `XCFramework`. To build it, run the following script from the root of the repository:

```bash
./make-ios-framework.sh
```

The output will be located in `mindbox-common/build/XCFrameworks/release/`.

## License

The library is available as open source under the terms of the [Mindbox License](https://github.com/mindbox-cloud/kmp-common-sdk/blob/master/LICENSE.md). 
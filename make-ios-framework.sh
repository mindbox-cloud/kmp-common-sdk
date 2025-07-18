./gradlew assembleMindboxCommonReleaseXCFramework

cp -R mindbox-common/build/XCFrameworks/release/MindboxCommon.xcframework frameworks/

zip -r frameworks/MindboxCommon.xcframework.zip frameworks/MindboxCommon.xcframework/

swift package compute-checksum frameworks/MindboxCommon.xcframework.zip
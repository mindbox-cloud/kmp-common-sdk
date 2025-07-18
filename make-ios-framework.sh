rm -rf mindbox-common/build/XCFrameworks/
./gradlew assembleMindboxCommonReleaseXCFramework

cd mindbox-common/build/XCFrameworks/release/

zip -r MindboxCommon.xcframework.zip MindboxCommon.xcframework/

swift package compute-checksum MindboxCommon.xcframework.zip
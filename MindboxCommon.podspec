Pod::Spec.new do |s|
    s.name         = 'MindboxCommon'
    s.version      = '2.14.4'
    s.summary      = 'Mindbox KMP Common framework (Local)'
    s.homepage     = 'https://github.com/mindbox-cloud/kmp-common-sdk'
    s.license      = { :type => "CC BY-NC-ND 4.0", :text => "See LICENSE.md in the repository: https://github.com/mindbox-cloud/kmp-common-sdk" }
    s.author       = { "Mindbox" => "ios-sdk@mindbox.ru" }
    
    s.source       = { :git => 'https://github.com/mindbox-cloud/kmp-common-sdk.git', :tag => s.version.to_s }
    
    s.prepare_command = <<-CMD
        chmod +x gradlew
        ./gradlew assembleMindboxCommonReleaseXCFramework
    CMD
    
    s.platform     = :ios, '12.0'
    s.vendored_frameworks = 'mindbox-common/build/XCFrameworks/release/MindboxCommon.xcframework'
  end
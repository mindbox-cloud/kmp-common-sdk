# Keep the WebView JS bridge methods so R8 doesn't strip them from release builds.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# WebView looks the methods up reflectively via Method.isAnnotationPresent(JavascriptInterface),
# so the annotation itself must survive too, not just the method.
-keepattributes RuntimeVisibleAnnotations

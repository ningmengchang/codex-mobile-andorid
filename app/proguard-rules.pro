# Keep JavaScript bridge entry points and the SSH implementation in release builds.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.jcraft.jsch.** { *; }

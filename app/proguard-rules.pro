# The legacy projection host loads SDK and car parcelables by class name.
-keep class com.google.android.apps.auto.sdk.** { *; }
-keep class com.google.android.gms.car.** { *; }
# Android Auto constructs this non-manifest CarActivity through reflection.
-keep class com.sya.tube.video.car.TubeProjectionActivity { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**
# NewPipe's Android rules: optional JVM integration is unused by its interpreted JS path.
-dontwarn org.mozilla.javascript.JavaToJSONConverters
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { <fields>; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

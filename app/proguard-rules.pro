-keep class gozarcore.** { *; }
-keep class go.** { *; }
-keepclassmembers class gozarcore.** { *; }
-dontwarn gozarcore.**
-dontwarn go.**

-keep class org.strongswan.android.** { *; }
-keep interface org.strongswan.android.** { *; }
-keepclassmembers class org.strongswan.android.** { *; }
-dontwarn org.strongswan.android.**

-keep class net.gozar.app.GozarApplication { *; }
-keep class net.gozar.app.GozarVpnService { *; }
-keep class net.gozar.app.QsTileService { *; }

-keepclasseswithmembernames class * {
    native <methods>;
}

# Profile/backup serialization is explicit JSONObject mapping, not field reflection.
# JNI keeps above remain scoped to their actual bridge packages.

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, AnnotationDefault

-keepclassmembers class * extends android.app.Application {
    <init>();
}

-keepclassmembers class * extends android.app.Service {
    <init>();
}

-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**
-dontwarn org.slf4j.**
-dontwarn org.bouncycastle.**
-dontwarn org.apache.**
# zeptun's JNI_OnLoad looks up dev/zeptun/Zeptun by name and registers all
# four natives with RegisterNatives. R8 dropped the ones Kotlin never calls,
# the registration failed, and the release build reported zeptun (and so
# sing-box, which rides on it) as missing while debug builds worked.
-keep class dev.zeptun.Zeptun { *; }

# Debug chatter must not reach Logcat in release; error/warning diagnostics remain.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

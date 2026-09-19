# ---------------------------------------------------------------------------
# Plugin system rules. The .cs3 dex resolves host classes BY NAME at runtime,
# so nothing in the contract may be renamed, merged or shrunk. Turning
# isMinifyEnabled on without these rules breaks every extension in a way that
# looks like "the extension is broken".
# ---------------------------------------------------------------------------
-keep class com.lagradost.cloudstream3.** { *; }
-keep class com.lagradost.nicehttp.** { *; }
-keep class com.lagradost.api.** { *; }
-keep class com.fmhub.plugin.api.** { *; }

# Entry points a plugin dex references
-keepclassmembers class * extends com.lagradost.cloudstream3.MainAPI {
    public <init>();
    public <init>(...);
}
-keepclassmembers class * extends com.lagradost.cloudstream3.plugins.BasePlugin {
    public <init>();
}
-keep class * extends com.lagradost.cloudstream3.plugins.BasePlugin { *; }
-keep class * extends com.lagradost.cloudstream3.utils.ExtractorApi { *; }

# The manifest inside a .cs3 is parsed with Gson into this type
-keep class com.fmhub24.app.plugins.PluginManifest { *; }
-keepclassmembers class com.fmhub24.app.plugins.PluginManifest { *; }

# Jackson + kotlinx serialize plugin DTOs that live only inside the dex
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,RuntimeVisibleParameters
-dontwarn com.fasterxml.jackson.**
-dontwarn org.jetbrains.kotlinx.serialization.**
-dontwarn org.mozilla.javascript.**
-dontwarn org.conscrypt.**
-dontwarn okhttp3.**
-dontwarn okio.**

# Media3 / Room / Hilt
-keep class androidx.media3.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

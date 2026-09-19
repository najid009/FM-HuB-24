# Keep the whole contract: plugin dex resolves these classes by their exact name, and
# extensions override their members. Renaming/shrinking them breaks every .cs3 file.
-keep class com.lagradost.cloudstream3.** { *; }
-keep class com.lagradost.api.** { *; }
-keep class com.fmhub.plugin.api.** { *; }

-keep class * extends com.lagradost.cloudstream3.MainAPI { *; }
-keep class * extends com.lagradost.cloudstream3.plugins.BasePlugin { *; }
-keep class * extends com.lagradost.cloudstream3.utils.ExtractorApi { *; }

# CloudStream's library serialises DTOs through Jackson + kotlinx.serialization.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations
-dontwarn com.lagradost.cloudstream3.**
-dontwarn com.lagradost.nicehttp.**
-dontwarn org.mozilla.javascript.**
-dontwarn org.conscrypt.**

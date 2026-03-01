# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Preserve line numbers for meaningful stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Gson ---
# Keep Gson's own internals
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }

# Keep all data classes serialized/deserialized with Gson.
# R8 can strip or rename fields that Gson accesses via reflection.
-keep class com.infinicada.focuspocus.Blocker { *; }
-keep class com.infinicada.focuspocus.BlockerMode { *; }
-keep class com.infinicada.focuspocus.BlockEvent { *; }
-keep class com.infinicada.focuspocus.FocusSession { *; }
-keep class com.infinicada.focuspocus.NamedTag { *; }
-keep class com.infinicada.focuspocus.Schedule { *; }
-keep class com.infinicada.focuspocus.FocusPreset { *; }
-keep class com.infinicada.focuspocus.DayOfWeek { *; }
-keep class com.infinicada.focuspocus.PresetAction { *; }

# --- ZXing (QR code library) ---
-keep class com.google.zxing.** { *; }
-keep class com.journeyapps.** { *; }

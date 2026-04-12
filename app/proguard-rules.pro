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

# Retain generic signatures of TypeToken and its subclasses so Gson can
# resolve parameterized types (e.g. List<Blocker>) at runtime via reflection.
# Without this, R8 strips the Signature attribute causing an
# IllegalStateException: "TypeToken must be created with a type argument".
# Do NOT use allowshrinking here — R8 full mode will strip generic
# signatures from anonymous TypeToken subclasses even with keepattributes.
-keep,allowobfuscation class com.google.gson.reflect.TypeToken { *; }
-keep,allowobfuscation class * extends com.google.gson.reflect.TypeToken { *; }

# Keep all data classes serialized/deserialized with Gson.
# R8 can strip or rename fields that Gson accesses via reflection.
-keep class com.infinicada.focuspocus.Blocker { *; }
-keep class com.infinicada.focuspocus.BlockerMode { *; }
-keep class com.infinicada.focuspocus.BlockEvent { *; }
-keep class com.infinicada.focuspocus.FocusSession { *; }
-keep class com.infinicada.focuspocus.NamedTag { *; }
# Model classes live in the .model sub-package — the old rules below targeted the wrong package
# and had no effect, leaving fields vulnerable to R8 obfuscation.
-keep class com.infinicada.focuspocus.model.Schedule { *; }
-keep class com.infinicada.focuspocus.model.FocusPreset { *; }
-keep class com.infinicada.focuspocus.model.DayOfWeek { *; }
-keep class com.infinicada.focuspocus.model.PresetAction { *; }
-keep class com.infinicada.focuspocus.model.ConditionalUnlock { *; }
-keep class com.infinicada.focuspocus.model.AppTimeLimit { *; }
-keep class com.infinicada.focuspocus.model.AppInfo { *; }

# --- ZXing (QR code library) ---
-keep class com.google.zxing.** { *; }
-keep class com.journeyapps.** { *; }

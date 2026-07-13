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
# R8 can strip or rename fields that Gson accesses via reflection, and R8 full
# mode strips the generic Signature attribute from non-kept classes — Gson then
# fills Map/List fields with LinkedTreeMap instead of the declared type, which
# crashes with a ClassCastException far from the parse site (e.g. the 1.4
# LinkedTreeMap-cannot-be-cast-to-AppOpenStats crashes on the Insights and
# Spellbook screens). A hand-maintained per-class list already rotted twice, so
# keep the whole model package plus every serialized class outside it.
-keep class com.infinicada.focuspocus.model.** { *; }
-keep class com.infinicada.focuspocus.Blocker { *; }
-keep class com.infinicada.focuspocus.BlockerMode { *; }
-keep class com.infinicada.focuspocus.BlockEvent { *; }
-keep class com.infinicada.focuspocus.FocusSession { *; }
-keep class com.infinicada.focuspocus.NamedTag { *; }
-keep class com.infinicada.focuspocus.limit.CooldownState { *; }
-keep class com.infinicada.focuspocus.limit.AppOpenStats { *; }
-keep class com.infinicada.focuspocus.limit.OpenReflexTracker$Store { *; }

# --- ZXing (QR code library) ---
-keep class com.google.zxing.** { *; }
-keep class com.journeyapps.** { *; }

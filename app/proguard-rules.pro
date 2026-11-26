# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Preserve line numbers for meaningful stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# All persisted models use compile-time kotlinx.serialization serializers
# (zero reflection), so no hand-maintained Gson keep rules are needed.
# kotlinx-serialization-json ships its own consumer ProGuard rules.

-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-dontpreverify
-verbose

# Maximum obfuscation — rename everything to meaningless chars
-repackageclasses ''
-allowaccessmodification
-overloadaggressively

# Remove all logging in release
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# Remove debug info — competitors see nothing
-renamesourcefileattribute X
-keepattributes SourceFile,LineNumberTable

# Strip everything possible
-dontnote **
-dontwarn **

# Encrypt/adapt all strings
-adaptclassstrings
-adaptresourcefilenames
-adaptresourcefilecontents

# ==========================================
# OBFUSCATION DICTIONARY
# All classes/methods become random 1-2 char names
# Decompiler output: a.b.c.d() — meaningless
# ==========================================
-obfuscationdictionary proguard-dictionary.txt
-classobfuscationdictionary proguard-dictionary.txt
-packageobfuscationdictionary proguard-dictionary.txt

# ==========================================
# KEEP ONLY ESSENTIALS (everything else gets obfuscated)
# ==========================================

# Room DB entities (reflection-based)
-keep class com.paisabrain.app.db.** { *; }
-keep class * extends androidx.room.RoomDatabase

# TensorFlow Lite
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**

# ML Kit
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# SQLCipher
-keep class net.sqlcipher.** { *; }
-dontwarn net.sqlcipher.**

# Compose runtime
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# WorkManager workers
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker

# Widget
-keep class * extends android.appwidget.AppWidgetProvider

# BroadcastReceiver (SMS)
-keep class * extends android.content.BroadcastReceiver

# Biometric
-keep class androidx.biometric.** { *; }

# Security guard itself (needs reflection for integrity check)
-keep class com.paisabrain.app.security.AppSecurityGuard { *; }

# ==========================================
# OPTIMIZATION — restructure code logic
# Makes decompiled code even harder to follow
# ==========================================
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*,code/removal/simple,code/removal/advanced

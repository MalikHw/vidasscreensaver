-keep class androidx.media3.** { *; }

# Preserve source file and line numbers for deobfuscated stack traces in Google Play Console
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

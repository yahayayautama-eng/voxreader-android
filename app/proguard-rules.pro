# VoxLeaf release ProGuard/R8 rules.
# Hilt and Room ship their own consumer rules — not duplicated here.

-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# JNI — kotlinNativeEngine's external functions are called by their mangled
# Java_com_example_tts_KokoroNativeEngine_* symbol names from kokoro_bridge.cpp.
-keep class com.example.tts.KokoroNativeEngine {
    native <methods>;
}

# kotlinx.serialization — Navigation Compose type-safe routes (Screen sealed
# interface) are (de)serialized by class name; VoxLeafApp.kt also matches
# routes against Screen::class.qualifiedName.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class com.example.core.navigation.**$$serializer { *; }
-keepclassmembers class com.example.core.navigation.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.core.navigation.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# pdfbox-android + fontbox: reflection-heavy, bundles its own resources.
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-dontwarn com.tom_roush.**
-dontwarn org.bouncycastle.**
-dontwarn javax.**

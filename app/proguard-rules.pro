# VoxLeaf release ProGuard/R8 rules.
# Hilt and Room ship their own consumer rules — not duplicated here.

-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# sherpa-onnx binds its JNI methods by class/method name reflectively; R8 renaming
# breaks the native binding at runtime with no compile-time signal.
-keep class com.k2fsa.sherpa.onnx.** { *; }

# kotlinx.serialization — Navigation Compose type-safe routes (Screen sealed
# interface) are (de)serialized by class name; VoxLeafApp.kt also matches
# routes against Screen::class.qualifiedName.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class com.voxleaf.reader.core.navigation.**$$serializer { *; }
-keepclassmembers class com.voxleaf.reader.core.navigation.** {
    *** Companion;
}
-keepclasseswithmembers class com.voxleaf.reader.core.navigation.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# pdfbox-android + fontbox: reflection-heavy, bundles its own resources.
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-dontwarn com.tom_roush.**
-dontwarn org.bouncycastle.**
-dontwarn javax.**

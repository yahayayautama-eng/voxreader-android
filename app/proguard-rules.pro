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
# Routes carry the compile-time class names, and VoxLeafApp compares them with
# Screen::class.qualifiedName at runtime. Renamed Screen classes hid the bottom bar in release.
-keepnames class com.voxleaf.reader.core.navigation.Screen
-keepnames class com.voxleaf.reader.core.navigation.Screen$*

# pdfbox-android + fontbox: reflection-heavy, bundles its own resources.
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-dontwarn com.tom_roush.**
-dontwarn org.bouncycastle.**
-dontwarn javax.**

# ML Kit creates its component registrars by class name (manifest meta-data) through the no-arg
# constructor. R8 kept the names but removed the constructors, so opening Import crashed in release.
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }

# HMS In-App Purchases (huawei flavor): Huawei's documented rules. HMS Core talks to the SDK
# over AIDL and instantiates its classes by name, so renamed classes fail only at purchase time.
-keepattributes Exceptions
-keep class com.huawei.hms.** { *; }
-keep class com.huawei.hmf.** { *; }
-keep class com.huawei.agconnect.** { *; }
-keep class com.huawei.updatesdk.** { *; }
-keep class com.hianalytics.android.** { *; }
-dontwarn com.huawei.**

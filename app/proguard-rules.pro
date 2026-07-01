# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep MediaPipe classes
-keep class com.google.mediapipe.tasks.genai.llminference.** { *; }

# Keep Room entities
-keep class com.obrynex.studyguard.data.** { *; }
-keep class com.obrynex.studyguard.learningmaterials.** { *; }

# Keep ViewModel constructors
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# Keep Compose-related classes
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Kotlin serialization
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions

# DataStore
-keepclassmembers class androidx.datastore.preferences.** { *; }

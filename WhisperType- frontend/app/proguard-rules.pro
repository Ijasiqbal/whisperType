# WhisperType ProGuard Rules for Maximum Size Reduction
# These rules ensure the app works correctly with R8 optimization enabled

# ============================================================
# Android Framework & Services
# ============================================================

# Keep accessibility service (required for Android system)
-keep class com.whispertype.app.service.WhisperTypeAccessibilityService { *; }

# Keep overlay service (Android service)
-keep class com.whispertype.app.service.OverlayService { *; }

# Keep all service classes
-keep public class * extends android.app.Service

# Keep accessibility service methods
-keepclassmembers class * extends android.accessibilityservice.AccessibilityService {
    public <methods>;
}

# ============================================================
# Kotlin & Coroutines
# ============================================================

# Keep Kotlin metadata for reflection
-keep class kotlin.Metadata { *; }

# Keep coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Keep coroutines for Firebase
-keepclassmembers class kotlinx.coroutines.** {
    *;
}

# ============================================================
# Firebase
# ============================================================

# Keep Firebase Auth
-keep class com.google.firebase.auth.** { *; }
-keep class com.google.android.gms.auth.** { *; }

# Keep Firebase internal classes
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# Keep credential manager
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }

# Firebase specific
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# ============================================================
# OkHttp & Networking
# ============================================================

# OkHttp platform used for SSL/TLS
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# OkHttp classes
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**

# Okio (used by OkHttp)
-keep class okio.** { *; }
-dontwarn okio.**

# ============================================================
# Gson (JSON parsing)
# ============================================================

# Keep Gson annotations
-keepattributes *Annotation*

# Keep generic signature for Gson reflection
-keepattributes Signature

# Keep data classes for Gson serialization
-keep class com.whispertype.app.api.** { *; }

# Gson specific classes
-keep class com.google.gson.** { *; }
-keep class sun.misc.Unsafe { *; }

# Keep all model classes for JSON serialization
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Prevent obfuscation of generic types
-keepattributes Signature

# ============================================================
# Jetpack Compose
# ============================================================

# Keep Compose runtime
-keep class androidx.compose.runtime.** { *; }

# Keep Composable functions
-keep @androidx.compose.runtime.Composable class * { *; }

# Keep remember functions
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}

# ============================================================
# Data Classes & Models
# ============================================================

# Keep all data classes (used for API responses)
-keep class com.whispertype.app.**.data.** { *; }
-keep class com.whispertype.app.**.model.** { *; }

# Keep enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ============================================================
# View Binding
# ============================================================

# Keep view binding classes
-keep class com.whispertype.app.databinding.** { *; }

# ============================================================
# Optimizations
# ============================================================

# Enable aggressive optimizations
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification
-dontpreverify

# Remove logging in release builds (saves space)
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# ============================================================
# Warnings to Suppress
# ============================================================

# Suppress warnings for missing classes that aren't used
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.*
-dontwarn javax.lang.model.element.Modifier

# Suppress warnings for reflection in libraries
-dontwarn java.lang.invoke.StringConcatFactory

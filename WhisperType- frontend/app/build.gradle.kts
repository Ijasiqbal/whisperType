plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.whispertype.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.whispertype.app"
        // Minimum SDK 24 (Android 7.0) chosen for:
        // - Stable runtime permissions model
        // - Good device coverage (~95% of active devices)
        // - TYPE_PHONE window type fallback for API 24-25
        minSdk = 24
        targetSdk = 35
        versionCode = 8
        versionName = "1.0.8"

        vectorDrawables {
            useSupportLibrary = true
        }
        
        // Size optimization: Only include English resources
        // Remove other locales to reduce APK size by ~20-30%
        resourceConfigurations += listOf("en")
        
        // Size optimization: Native library filtering
        // Only include necessary ABIs, exclude unused architectures
        ndk {
            // Include only common architectures (covers 99%+ of devices)
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }

    buildTypes {
        release {
            // Size optimization: Enable R8 code shrinking, optimization, and obfuscation
            // Reduces APK size by 40-60% by removing unused code
            isMinifyEnabled = true
            
            // Size optimization: Remove unused resources (layouts, drawables, strings, etc.)
            // Additional 10-20% reduction by removing unreferenced resources
            isShrinkResources = true
            
            // Use optimized ProGuard rules for maximum size reduction
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            
            // Size optimization: Disable debug info in release builds
            isDebuggable = false
            
            // Performance optimization: Enable code optimization
            isMinifyEnabled = true
        }
        
        debug {
            // Keep debug builds fast - no optimization
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }
    
    // Size optimization: APK splits are NOT needed for AAB (Android App Bundle)
    // When uploading AAB to Play Store, Google automatically generates optimized APKs
    // per device architecture. Only enable this if building APKs directly.
    splits {
        abi {
            isEnable = false  // Disabled for AAB - Play Store handles this
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = false
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }
    
    buildFeatures {
        compose = true
        viewBinding = true
        buildConfig = true
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.4"
    }
    
    packaging {
        resources {
            // Size optimization: Exclude unnecessary metadata files
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE",
                "/META-INF/LICENSE.txt",
                "/META-INF/license.txt",
                "/META-INF/NOTICE",
                "/META-INF/NOTICE.txt",
                "/META-INF/notice.txt",
                "/META-INF/*.kotlin_module",
                "/META-INF/gradle/incremental.annotation.processors"
            )
        }
        
        // Size optimization: Use only one version of native libraries
        jniLibs {
            useLegacyPackaging = false
        }
    }
    
    // Size optimization: Enable Android App Bundle format
    // This replaces APK and allows Google Play to generate optimized APKs
    // Can reduce download size by 30-50%
    bundle {
        language {
            // Only include English strings in the base module
            enableSplit = false  // Keep all resources in base for simplicity
        }
        density {
            enableSplit = true  // Generate density-specific splits
        }
        abi {
            enableSplit = true  // Generate ABI-specific splits
        }
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.1")
    
    // Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    
    // For overlay view binding
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    
    // Networking for WhisperType API
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    
    // Firebase (using 32.7.0 for Kotlin 1.9 compatibility - BOM 34.x requires Kotlin 2.0+)
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    // Size optimization: Analytics removed - not used in app (saves ~1-2 MB)
    // implementation("com.google.firebase:firebase-analytics-ktx")
    
    // Kotlin Coroutines for Firebase (provides .await() extension)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    
    // Google Sign-In with Credential Manager (modern API for Android 14+)
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    
    // Google Play Billing for Pro subscription (Iteration 3)
    val billing_version = "7.1.1"
    implementation("com.android.billingclient:billing-ktx:$billing_version")
    
    // Firebase Remote Config for dynamic plan configuration
    implementation("com.google.firebase:firebase-config-ktx")
    
    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

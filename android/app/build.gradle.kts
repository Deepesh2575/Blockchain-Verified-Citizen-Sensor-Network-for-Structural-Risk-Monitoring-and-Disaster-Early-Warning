plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.sih26223.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.sih26223.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        
        // Exclude x86_64 native libraries (which trigger the 16KB warning)
        // Most physical devices run arm64-v8a anyway.
        ndk {
            abiFilters.add("arm64-v8a")
            abiFilters.add("armeabi-v7a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        jniLibs {
            // Setting this to true forces Android to extract the .so files at runtime.
            // This bypasses the strict 16KB page alignment check for the APK payload.
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(project(":sensing-core"))
    
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    
    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Location Services
    implementation("com.google.android.gms:play-services-location:21.3.0")
    
    // Wearable Communication
    implementation("com.google.android.gms:play-services-wearable:18.1.0")
    
    // BLE Mesh (Nearby Connections)
    implementation("com.google.android.gms:play-services-nearby:19.0.0")
    
    // AR Escape Route Pathfinder
    implementation("io.github.sceneview:arsceneview:2.2.1")
    
    // Federated Learning (WorkManager)
    implementation("androidx.work:work-runtime-ktx:2.9.0")
}

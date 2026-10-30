plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.xof.ft150ts"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.xof.ft150ts"
        minSdk = 26          // Android 8.0 o más nuevo
        targetSdk = 35
        versionCode = 52
        versionName = "3.11.0-beta.1"
    }

    buildTypes {
        release {
            // R8 quita código sin usar y optimiza: la app pesa menos y va más fluida
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Firmada con la llave de depuración para que la puedas instalar directo
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.01.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Dashcam (CameraX: solo video, sin vista previa)
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-video:1.4.1")

    // Mapa de OpenStreetMap (no necesita llave de Google)
    implementation("org.osmdroid:osmdroid-android:6.1.18")

    // Pruebas unitarias (solo para compilar/probar; no entran en la app)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")   // org.json real (el de Android en pruebas es de mentira)
}

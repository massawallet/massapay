plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-kapt")
    id("dagger.hilt.android.plugin")
}

import java.util.Properties
import java.io.FileInputStream

// Load keystore properties
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

// Load local properties for API keys
val localPropertiesFile = rootProject.file("local.properties")
val localProperties = Properties()
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

android {
    namespace = "com.massapay.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.massapay.android"
        minSdk = 26  // Android 8.0+ (cubre ~95% de dispositivos activos)
        targetSdk = 34  // Target 34 para cumplir requisitos de Google Play 2024
        versionCode = 7
        versionName = "1.4.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Soporte para todas las arquitecturas (dispositivos físicos y emuladores)
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file(keystoreProperties["storeFile"] as String? ?: "keystore/massapay-release-key.jks")
            storePassword = keystoreProperties["storePassword"] as String? ?: ""
            keyAlias = keystoreProperties["keyAlias"] as String? ?: "massapay"
            keyPassword = keystoreProperties["keyPassword"] as String? ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false  // Temporalmente deshabilitado por RAM
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "WALLETCONNECT_PROJECT_ID", "\"${localProperties.getProperty("WALLETCONNECT_PROJECT_ID", "")}\"")
        }
        debug {
            isMinifyEnabled = false
            // IMPORTANTE: Sin applicationIdSuffix para que debug y release usen el mismo ID
            // Esto permite actualizar de debug a release sin desinstalar
            signingConfig = signingConfigs.getByName("release")  // Misma firma que release
            buildConfigField("String", "WALLETCONNECT_PROJECT_ID", "\"${localProperties.getProperty("WALLETCONNECT_PROJECT_ID", "")}\"")
        }
    }

    // Paquete universal compatible con todos los dispositivos y emuladores
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
        }
        jniLibs {
            useLegacyPackaging = true  // Mejor compatibilidad con dispositivos antiguos
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.4.7"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Project modules
    implementation(project(":core"))
    // Note: :data and :domain modules are not present in this workspace. If you add them, re-enable the lines below.
    // implementation(project(":data"))
    // implementation(project(":domain"))
    implementation(project(":ui"))
    implementation(project(":security"))
    implementation(project(":network"))
    implementation(project(":price"))

    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.0")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2023.10.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    // Navigation for Compose (used in MainActivity)
    implementation("androidx.navigation:navigation-compose:2.7.4")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.47")
    kapt("com.google.dagger:hilt-android-compiler:2.47")
    implementation("androidx.hilt:hilt-navigation-compose:1.0.0")

    // Room
    implementation("androidx.room:room-runtime:2.6.0")
    implementation("androidx.room:room-ktx:2.6.0")
    kapt("androidx.room:room-compiler:2.6.0")

    // Retrofit & OkHttp
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")

    // Security
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.biometric:biometric:1.2.0-alpha05")

    // DataStore for preferences
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Material Components (styles referenced in Theme)
    implementation("com.google.android.material:material:1.9.0")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.8.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.5.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
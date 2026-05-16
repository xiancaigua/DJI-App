plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

import java.util.Properties

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use(::load)
    }
}

val djiSdkVersion = "5.17.0"
val djiWpmzVersion = "1.0.4.0"
val composeBomVersion = "2024.09.00"
val activityComposeVersion = "1.9.2"
val lifecycleVersion = "2.8.6"
val requestedAppApplicationId = providers.gradleProperty("APP_APPLICATION_ID")
    .orElse(localProperties.getProperty("APP_APPLICATION_ID") ?: "com.example.uavmobile")
    .get()
val appApplicationIdPattern = Regex("""^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$""")
val appApplicationId = requestedAppApplicationId.takeIf { appApplicationIdPattern.matches(it) }
    ?: "com.example.uavmobile"
val djiAppKey = providers.gradleProperty("AIRCRAFT_API_KEY")
    .orElse(localProperties.getProperty("AIRCRAFT_API_KEY") ?: "")
    .get()
val enableDjiRuntime = providers.gradleProperty("DJI_ENABLE_RUNTIME")
    .orElse(localProperties.getProperty("DJI_ENABLE_RUNTIME") ?: if (djiAppKey.isNotBlank()) "true" else "false")
    .get()
    .toBoolean()

android {
    namespace = "com.example.uavmobile"
    compileSdk = 34

    defaultConfig {
        applicationId = appApplicationId
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        multiDexEnabled = true
        manifestPlaceholders["API_KEY"] = djiAppKey
        buildConfigField("boolean", "DJI_ENABLE_RUNTIME", enableDjiRuntime.toString())
        buildConfigField("String", "DJI_SDK_VERSION", "\"$djiSdkVersion\"")
        buildConfigField("String", "DJI_WPMZ_VERSION", "\"$djiWpmzVersion\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
            pickFirsts += setOf(
                "lib/arm64-v8a/libc++_shared.so",
                "lib/armeabi-v7a/libc++_shared.so",
            )
            keepDebugSymbols += setOf(
                "*/*/libconstants.so",
                "*/*/libdji_innertools.so",
                "*/*/libdjibase.so",
                "*/*/libDJIRegister.so",
                "*/*/libdjisdk_jni.so",
                "*/*/libDJIUpgradeCore.so",
                "*/*/libDJIUpgradeJNI.so",
                "*/*/libFlightRecordEngine.so",
                "*/*/libvideo-framing.so",
                "*/*/libwaes.so",
                "*/*/libmrtc_28181.so",
                "*/*/libmrtc_agora.so",
                "*/*/libmrtc_core.so",
                "*/*/libmrtc_core_jni.so",
            )
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:$composeBomVersion")

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:$activityComposeVersion")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:$lifecycleVersion")
    implementation("androidx.multidex:multidex:2.0.1")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.google.android.material:material:1.12.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.dji:dji-sdk-v5-aircraft:$djiSdkVersion") {
        exclude(group = "com.dji", module = "wpmzsdk")
    }
    implementation("com.dji:wpmzsdk:$djiWpmzVersion")
    compileOnly("com.dji:dji-sdk-v5-aircraft-provided:$djiSdkVersion")
    runtimeOnly("com.dji:dji-sdk-v5-networkImp:$djiSdkVersion")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}

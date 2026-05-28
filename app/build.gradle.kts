plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

import org.gradle.api.GradleException
import java.util.Properties

val defaultAppApplicationId = "com.example.uavmobile"
val localPropertiesFile = rootProject.file("local.properties")
val localProperties = Properties().apply {
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use(::load)
    }
}

val djiSdkVersion = "5.17.0"
val djiWpmzVersion = "1.0.4.0"
val composeBomVersion = "2024.09.00"
val activityComposeVersion = "1.9.2"
val lifecycleVersion = "2.8.6"

fun localProperty(name: String): String? = localProperties.getProperty(name)?.trim()?.takeIf { it.isNotEmpty() }

val requireDjiRuntime = providers.gradleProperty("REQUIRE_DJI_RUNTIME")
    .orElse("false")
    .get()
    .toBoolean()
val requestedAppApplicationId = providers.gradleProperty("APP_APPLICATION_ID")
    .orElse(localProperty("APP_APPLICATION_ID") ?: defaultAppApplicationId)
    .get()
    .trim()
val appApplicationIdPattern = Regex("""^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$""")
val appApplicationId = requestedAppApplicationId.takeIf { appApplicationIdPattern.matches(it) }
    ?: defaultAppApplicationId
val appApplicationIdIsDefault = appApplicationId == defaultAppApplicationId
val djiAppKey = providers.gradleProperty("AIRCRAFT_API_KEY")
    .orElse(localProperty("AIRCRAFT_API_KEY") ?: "")
    .get()
    .trim()
val djiAppKeyEmpty = djiAppKey.isBlank()
val enableDjiRuntime = providers.gradleProperty("DJI_ENABLE_RUNTIME")
    .orElse(localProperty("DJI_ENABLE_RUNTIME") ?: if (djiAppKey.isNotBlank()) "true" else "false")
    .get()
    .trim()
    .toBoolean()

if (requireDjiRuntime) {
    val validationErrors = buildList {
        if (appApplicationIdIsDefault) {
            add("applicationId 回退到了 $defaultAppApplicationId")
        }
        if (djiAppKeyEmpty) {
            add("AIRCRAFT_API_KEY 为空")
        }
        if (!enableDjiRuntime) {
            add("DJI_ENABLE_RUNTIME=false")
        }
    }
    if (validationErrors.isNotEmpty()) {
        throw GradleException(
            """
            REQUIRE_DJI_RUNTIME=true，但当前 DJI 真机调试配置无效。
            请检查仓库根目录 local.properties：${localPropertiesFile.absolutePath}
            必须确认：
            - APP_APPLICATION_ID
            - AIRCRAFT_API_KEY
            - DJI_ENABLE_RUNTIME
            当前解析结果：
            - APPLICATION_ID=$appApplicationId
            - APP_APPLICATION_ID_IS_DEFAULT=$appApplicationIdIsDefault
            - DJI_APP_KEY_EMPTY=$djiAppKeyEmpty
            - DJI_ENABLE_RUNTIME=$enableDjiRuntime
            - DJI_SDK_VERSION=$djiSdkVersion
            - DJI_WPMZ_VERSION=$djiWpmzVersion
            失败原因：
            ${validationErrors.joinToString(separator = System.lineSeparator()) { "- $it" }}
            """.trimIndent(),
        )
    }
}

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
        buildConfigField("boolean", "DJI_APP_KEY_EMPTY", djiAppKeyEmpty.toString())
        buildConfigField("boolean", "APP_APPLICATION_ID_IS_DEFAULT", appApplicationIdIsDefault.toString())
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

    testOptions {
        unitTests.isReturnDefaultValues = true
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
    testCompileOnly("com.dji:dji-sdk-v5-aircraft-provided:$djiSdkVersion")
}

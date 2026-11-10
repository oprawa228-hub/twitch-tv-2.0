import java.util.Properties
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
val signing = Properties().apply { rootProject.file("signing.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) } }
val legacyTvClientId = providers.gradleProperty("legacyTvClientId").orElse("").get()
require(legacyTvClientId.isEmpty() || legacyTvClientId.matches(Regex("[a-z0-9]{30}")))
android {
    namespace = "com.oprawa.twitch20"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.oprawa.twitch20"
        minSdk = 23
        targetSdk = 36
        versionCode = providers.gradleProperty("releaseCode").orElse("1").get().toInt()
        versionName = providers.gradleProperty("releaseName").orElse("0.1.0-alpha.1").get()
        buildConfigField("String", "TWITCH_CLIENT_ID", "\"${providers.gradleProperty("twitchClientId").orElse("").get()}\"")
        buildConfigField("String", "LEGACY_TV_CLIENT_ID", "\"\"")
        manifestPlaceholders["appLabel"] = "Twitch 2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        if (signing.isNotEmpty()) create("release") {
            storeFile = file(signing.getProperty("storeFile"))
            storePassword = signing.getProperty("storePassword")
            keyAlias = signing.getProperty("keyAlias")
            keyPassword = signing.getProperty("keyPassword")
        }
    }
    buildTypes {
        debug {
            if (legacyTvClientId.isNotEmpty()) {
                applicationIdSuffix = ".legacytest"
                versionNameSuffix = "-legacy-tv"
                manifestPlaceholders["appLabel"] = "Twitch 2.0 — тест входа"
                buildConfigField("String", "LEGACY_TV_CLIENT_ID", "\"$legacyTvClientId\"")
            } else if (signing.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        release {
            buildConfigField("String", "LEGACY_TV_CLIENT_ID", "\"$legacyTvClientId\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (signing.isNotEmpty()) signingConfig = signingConfigs.getByName("release")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    testBuildType = "debug"
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.tv:tv-material:1.1.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.11.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.11.1")
    implementation("androidx.media3:media3-ui-compose:1.11.1")
    implementation("androidx.media3:media3-session:1.11.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.1")
    implementation("io.coil-kt.coil3:coil-compose:3.4.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.4.0")
    implementation("io.coil-kt.coil3:coil-svg:3.4.0")
    implementation("io.coil-kt.coil3:coil-gif:3.4.0")
    implementation("com.google.zxing:core:3.5.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
}

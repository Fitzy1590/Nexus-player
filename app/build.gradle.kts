import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Single source of truth for the version. versionCode is derived (X.Y.Z → X*10000 + Y*100 + Z) so every
// release automatically increases it; Android refuses updates whose versionCode isn't higher.
val appVersion = "0.2.6"
val appVersionCode = appVersion.split('.').map(String::toInt).let { (x, y, z) -> x * 10_000 + y * 100 + z }

// Release signing: keystore.properties (gitignored) or NEXUS_* env vars. Absent → unsigned release APK.
val signingProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun signingValue(key: String, env: String): String? = signingProps.getProperty(key) ?: System.getenv(env)

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

android {
    namespace = "com.nexus.player"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.fitzy.nexusplayer"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersion
    }
    signingConfigs {
        create("release") {
            signingValue("storeFile", "NEXUS_KEYSTORE")?.let { storeFile = rootProject.file(it) }
            storePassword = signingValue("storePassword", "NEXUS_KEYSTORE_PASSWORD")
            keyAlias = signingValue("keyAlias", "NEXUS_KEY_ALIAS")
            keyPassword = signingValue("keyPassword", "NEXUS_KEY_PASSWORD")
        }
    }
    buildTypes {
        release {
            if (signingValue("storeFile", "NEXUS_KEYSTORE") != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    testOptions { unitTests.isReturnDefaultValues = true; unitTests.isIncludeAndroidResources = true }
}

dependencies {
    val media3 = "1.11.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-exoplayer-dash:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.media3:media3-datasource-okhttp:$media3")

    implementation(platform("androidx.compose:compose-bom:2025.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.tv:tv-material:1.0.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")

    val room = "2.8.4"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.18.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

tasks.withType<Test> { testLogging { events("passed", "failed") } }

// Copies the debug APK to <root>/apk/ (gitignored) for sharing outside build/.
tasks.register<Copy>("exportApk") {
    dependsOn("assembleDebug")
    from(layout.buildDirectory.dir("outputs/apk/debug")) { include("*.apk") }
    into(rootProject.layout.projectDirectory.dir("apk"))
    rename { "nexus-player-debug.apk" }
}

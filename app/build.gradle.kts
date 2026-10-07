import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Local credentials are never committed. Environment variables support CI.
val releaseSettings = Properties().apply {
    rootProject.file("release.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun releaseSetting(key: String): String? =
    System.getenv("PAD_RELEASE_${key.replace(Regex("([a-z])([A-Z])"), "$1_$2").uppercase()}")
        ?: releaseSettings.getProperty(key)?.takeIf { it.isNotBlank() }
val releaseApplicationId = releaseSetting("applicationId")
val releaseStoreFile = releaseSetting("storeFile")
val releaseStorePassword = releaseSetting("storePassword")
val releaseKeyAlias = releaseSetting("keyAlias")
val releaseKeyPassword = releaseSetting("keyPassword")
val signingReady = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all { it != null }

android {
    namespace = "com.example.phoneapp"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.phoneapp"
        minSdk = 26
        targetSdk = 37
        versionCode = releaseSetting("versionCode")?.toInt() ?: 1
        versionName = releaseSetting("versionName") ?: "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (signingReady) create("upload") {
            storeFile = rootProject.file(releaseStoreFile!!)
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }
    buildTypes {
        getByName("release") {
            isDebuggable = false
            // Keep release behavior identical while the first closed test is validated.
            isMinifyEnabled = false
            if (signingReady) signingConfig = signingConfigs.getByName("upload")
        }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        releaseApplicationId?.let { variant.applicationId.set(it) }
    }
}

val validatePlayRelease = tasks.register("validatePlayRelease") {
    doLast {
        check(releaseApplicationId != null && releaseApplicationId != "com.example.phoneapp" &&
            releaseApplicationId.matches(Regex("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+"))) {
            "Set a permanent applicationId in release.properties before creating a Play bundle."
        }
        check((releaseSetting("versionCode")?.toIntOrNull() ?: 1) > 0) { "versionCode must be positive." }
        check(signingReady) { "Configure upload signing in release.properties or PAD_RELEASE_* environment variables." }
        check(rootProject.file(releaseStoreFile!!).isFile) { "Upload keystore file does not exist." }
    }
}
// APK assembly remains available for unsigned local validation; Play bundles fail closed.
tasks.matching { it.name == "bundleRelease" }.configureEach { dependsOn(validatePlayRelease) }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.activity:activity-compose:1.12.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.4.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

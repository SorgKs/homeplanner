plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

import com.android.build.gradle.internal.api.ApkVariantOutputImpl

import java.util.Properties

// Read version from pyproject (single source of truth for MAJOR/MINOR)
val pyprojectFile = listOf(
    "pyproject.toml",
    "../pyproject.toml",
    "../../pyproject.toml"
).map { project.rootProject.file(it) }
    .firstOrNull { it.exists() }
    ?: project.rootProject.file("pyproject.toml")
val androidVersionFile = project.rootProject.file("version.json")

fun loadProjectVersionFromPyproject(file: File, fallback: Map<String, Int>): Map<String, Int> {
    if (!file.exists()) return fallback
    return try {
        val content = file.readText()
        val majorMatch = Regex("project_major\\s*=\\s*\"?(\\d+)\"?").find(content)
        val minorMatch = Regex("project_minor\\s*=\\s*\"?(\\d+)\"?").find(content)

        val result = mutableMapOf<String, Int>()
        if (majorMatch != null) result["major"] = majorMatch.groupValues[1].toInt()
        if (minorMatch != null) result["minor"] = minorMatch.groupValues[1].toInt()

        fallback.forEach { (key, value) ->
            if (!result.containsKey(key)) result[key] = value
        }
        result
    } catch (e: Exception) {
        fallback
    }
}

fun loadJsonFile(file: File, fallback: Map<String, Int>): Map<String, Int> {
    if (!file.exists()) return fallback
    return try {
        val content = file.readText()
        // Simple JSON parsing for version files (format: {"major": 0, "minor": 2})
        val majorMatch = Regex("\"major\"\\s*:\\s*(\\d+)").find(content)
        val minorMatch = Regex("\"minor\"\\s*:\\s*(\\d+)").find(content)
        val patchMatch = Regex("\"patch\"\\s*:\\s*(\\d+)").find(content)
        
        val result = mutableMapOf<String, Int>()
        if (majorMatch != null) result["major"] = majorMatch.groupValues[1].toInt()
        if (minorMatch != null) result["minor"] = minorMatch.groupValues[1].toInt()
        if (patchMatch != null) result["patch"] = patchMatch.groupValues[1].toInt()
        
        // Merge with fallback for missing values
        fallback.forEach { (key, value) ->
            if (!result.containsKey(key)) result[key] = value
        }
        result
    } catch (e: Exception) {
        fallback
    }
}

val projectVersion = loadProjectVersionFromPyproject(pyprojectFile, mapOf("major" to 0, "minor" to 0))
val androidVersion = loadJsonFile(androidVersionFile, mapOf("patch" to 0))

val major = projectVersion["major"] ?: 0
val minor = projectVersion["minor"] ?: 0
val patch = androidVersion["patch"] ?: 0

// Read and increment build number
val buildNumberFile = file("build_number.txt")
var buildNumber = 1
if (buildNumberFile.exists()) {
    buildNumber = buildNumberFile.readText().trim().toIntOrNull() ?: 1
} else {
    buildNumberFile.writeText("1")
}
// Increment and save for next build
buildNumber++
buildNumberFile.writeText(buildNumber.toString())

// Public version string must remain MAJOR.MINOR.PATCH
val versionNameStr = "$major.$minor.$patch"

android {
    namespace = "com.homeplanner"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.homeplanner"
        minSdk = 24
        targetSdk = 34
        versionCode = buildNumber
        versionName = versionNameStr

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Resolve API base URL from local.properties or environment to keep Android and Web on the same backend
        val localProps = project.rootProject.file("local.properties").takeIf { it.exists() }?.reader()?.use {
            Properties().apply { load(it) }
        }
        val apiBaseUrlFromLocal = localProps?.getProperty("apiBaseUrl")
        val apiBaseUrlFromEnv = System.getenv("HP_API_BASE_URL")
        val resolvedApiBaseUrl = (apiBaseUrlFromLocal ?: apiBaseUrlFromEnv) ?: "http://192.168.1.2:8000/api/v1"
        buildConfigField("String", "API_BASE_URL", "\"$resolvedApiBaseUrl\"")
        buildConfigField("String", "VERSION_NAME", "\"$versionNameStr\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // Use same resolved API base URL in debug
            val localProps = project.rootProject.file("local.properties").takeIf { it.exists() }?.reader()?.use {
                Properties().apply { load(it) }
            }
            val apiBaseUrlFromLocal = localProps?.getProperty("apiBaseUrl")
            val apiBaseUrlFromEnv = System.getenv("HP_API_BASE_URL")
            val resolvedApiBaseUrl = (apiBaseUrlFromLocal ?: apiBaseUrlFromEnv) ?: "http://192.168.1.2:8000/api/v1"
            buildConfigField("String", "API_BASE_URL", "\"$resolvedApiBaseUrl\"")
            buildConfigField("String", "VERSION_NAME", "\"$versionNameStr\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    
    // Configure Java toolchain to automatically download JDK 17 if not found
    // This allows the project to work on different machines without manual JDK setup
    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material")
    // Icons for Material Design (managed by Compose BOM)
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.google.android.material:material:1.12.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    
    // DataStore for network settings
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    
    // ML Kit for QR code scanning
    implementation("com.google.mlkit:barcode-scanning:17.2.0")
    
    // CameraX for camera access
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}

@Suppress("UnstableApiUsage")
android.applicationVariants.all {
    val versionLabel = (versionName ?: "0.0.0").replace('.', '_')
    outputs
        .mapNotNull { it as? ApkVariantOutputImpl }
        .forEach { output ->
            output.outputFileName = "homeplanner_v$versionLabel.apk"
        }
}


plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
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
// Try to find version.json in android/ directory (root of android project)
// project.rootProject.dir is android/ directory
val androidVersionFile = project.rootProject.file("version.json")

// Load shared backend/frontend configuration from common/config/settings.toml
// to keep Android app settings (API version, default backend host/port) in sync.
val settingsTomlFileCandidates = listOf(
    // Typical monorepo layout: this android/ directory is sibling to common/
    project.rootProject.file("../common/config/settings.toml"),
    project.rootProject.file("common/config/settings.toml")
)
val settingsTomlFile = settingsTomlFileCandidates.firstOrNull { it.exists() }

data class ApiAndNetworkConfig(
    val apiVersion: String,
    val backendHost: String,
    val backendPort: Int
)

fun loadApiAndNetworkConfigFromSettingsToml(file: File?): ApiAndNetworkConfig {
    if (file == null || !file.exists()) {
        println("WARNING: common/config/settings.toml not found, using hardcoded defaults for API config")
        // Fallback matches template defaults in common/config/settings.toml.template
        return ApiAndNetworkConfig(
            apiVersion = "0.2",
            backendHost = "localhost",
            backendPort = 8000
        )
    }

    return try {
        val content = file.readText()

        // [api].version = "0.2"
        val apiVersionRegex = Regex("""\[api\][^\[]*?version\s*=\s*"([^"]+)"""", RegexOption.DOT_MATCHES_ALL)
        val apiMatch = apiVersionRegex.find(content)
        val apiVersion = apiMatch?.groupValues?.getOrNull(1) ?: "0.2"

        // [network].host = "...."
        val hostRegex = Regex("""\[network\][^\[]*?host\s*=\s*"([^"]+)"""", RegexOption.DOT_MATCHES_ALL)
        val hostMatch = hostRegex.find(content)
        val backendHost = hostMatch?.groupValues?.getOrNull(1) ?: "localhost"

        // [network].port = 8000
        val portRegex = Regex("""\[network\][^\[]*?port\s*=\s*(\d+)""", RegexOption.DOT_MATCHES_ALL)
        val portMatch = portRegex.find(content)
        val backendPort = portMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 8000

        println("Loaded API/network config from settings.toml: apiVersion=$apiVersion, host=$backendHost, port=$backendPort")
        ApiAndNetworkConfig(
            apiVersion = apiVersion,
            backendHost = backendHost,
            backendPort = backendPort
        )
    } catch (e: Exception) {
        println("WARNING: Failed to parse settings.toml for API config: ${e.message}. Falling back to defaults.")
        ApiAndNetworkConfig(
            apiVersion = "0.2",
            backendHost = "localhost",
            backendPort = 8000
        )
    }
}

val apiAndNetworkConfig = loadApiAndNetworkConfigFromSettingsToml(settingsTomlFile)

println("Looking for version.json at: ${androidVersionFile.absolutePath}")
println("File exists: ${androidVersionFile.exists()}")
if (androidVersionFile.exists()) {
    println("File content preview: ${androidVersionFile.readText().take(100)}")
}

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
    if (!file.exists()) {
        println("WARNING: version.json not found at ${file.absolutePath}, using fallback: $fallback")
        return fallback
    }
    return try {
        val content = file.readText()
        println("Reading version.json from ${file.absolutePath}")
        println("Content: $content")
        
        // Simple JSON parsing for version files (format: {"major": 0, "minor": 2, "patch": 77})
        // Use more flexible regex that handles whitespace and formatting
        val majorMatch = Regex("\"major\"\\s*:\\s*(\\d+)").find(content)
        val minorMatch = Regex("\"minor\"\\s*:\\s*(\\d+)").find(content)
        val patchMatch = Regex("\"patch\"\\s*:\\s*(\\d+)").find(content)
        
        val result = mutableMapOf<String, Int>()
        if (majorMatch != null) {
            result["major"] = majorMatch.groupValues[1].toInt()
            println("Found major: ${result["major"]}")
        }
        if (minorMatch != null) {
            result["minor"] = minorMatch.groupValues[1].toInt()
            println("Found minor: ${result["minor"]}")
        }
        if (patchMatch != null) {
            result["patch"] = patchMatch.groupValues[1].toInt()
            println("Found patch: ${result["patch"]}")
        } else {
            println("WARNING: patch not found in version.json")
        }
        
        // Merge with fallback for missing values
        fallback.forEach { (key, value) ->
            if (!result.containsKey(key)) result[key] = value
        }
        
        println("Final version map: $result")
        result
    } catch (e: Exception) {
        println("ERROR: Failed to parse version.json: ${e.message}")
        e.printStackTrace()
        fallback
    }
}

val projectVersion = loadProjectVersionFromPyproject(pyprojectFile, mapOf("major" to 0, "minor" to 0))
var androidVersion = loadJsonFile(androidVersionFile, mapOf("patch" to 0))

val major = projectVersion["major"] ?: 0
val minor = projectVersion["minor"] ?: 0
val patch = androidVersion["patch"] ?: 0

println("========================================")
println("Version information:")
println("  Major: $major (from pyproject.toml)")
println("  Minor: $minor (from pyproject.toml)")
println("  Patch: $patch (from version.json)")
println("  Full version: $major.$minor.$patch")
println("========================================")

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
        // VERSION_NAME is shared for all build types
        buildConfigField("String", "VERSION_NAME", "\"$versionNameStr\"")
    }

    signingConfigs {
        // Use debug signing for release to allow installation on devices
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // Release build: network settings must be strictly empty.
            // API_BASE_URL is always an empty string; the app must be configured
            // explicitly at runtime via Settings/QR and must not rely on any
            // compile-time defaults or environment variables.
            buildConfigField("String", "API_BASE_URL", "\"\"")
        }
        debug {
            // Debug build: use shared settings.toml as the default network config,
            // so that developers can run the app without manual host/port setup.
            val localProps = project.rootProject.file("local.properties").takeIf { it.exists() }?.reader()?.use {
                Properties().apply { load(it) }
            }
            val apiBaseUrlFromLocal = localProps?.getProperty("apiBaseUrl")
            val apiBaseUrlFromEnv = System.getenv("HP_API_BASE_URL")
            val defaultApiBaseUrlFromSettings = "http://${apiAndNetworkConfig.backendHost}:${apiAndNetworkConfig.backendPort}/api/v${apiAndNetworkConfig.apiVersion}"
            val resolvedApiBaseUrl = (apiBaseUrlFromLocal ?: apiBaseUrlFromEnv) ?: defaultApiBaseUrlFromSettings
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

    lint {
        abortOnError = false
        disable.add("StateFlowValueCalledInComposition")
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.16"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/LICENSE*"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material")
    // Icons for Material Design (managed by Compose BOM)
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.google.android.material:material:1.12.0")

    // Navigation Compose
    implementation("androidx.navigation:navigation-compose:2.8.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    
    // DataStore for network settings
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    
    // Room / offline cache
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")
    
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
    // UiAutomator for handling system dialogs
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    // MockWebServer for instrumented tests (API/sync tests)
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    // MockK for mocking in tests
    androidTestImplementation("io.mockk:mockk-android:1.13.10")
    // InstantTaskExecutorRule for testing LiveData/ViewModels
    androidTestImplementation("androidx.arch.core:core-testing:2.2.0")

    // WorkManager for background sync
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Koin for dependency injection
    implementation("io.insert-koin:koin-android:3.5.0")
    implementation("io.insert-koin:koin-androidx-compose:3.5.0")
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


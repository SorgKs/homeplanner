plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

import com.android.build.gradle.internal.api.ApkVariantOutputImpl

import java.util.Properties

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

// Base version (major.minor.patch.build)
val baseVersion = "0.1.0"
val versionNameStr = "$baseVersion.$buildNumber"

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


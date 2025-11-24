import java.util.Properties

// Read JDK path from local.properties (machine-specific, not in git)
val localPropertiesFile = file("local.properties")
val localProperties = Properties()
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

// Set Java home from local.properties if specified
// Priority: 1) local.properties, 2) JAVA_HOME environment variable, 3) system default
val javaHomeFromLocal = localProperties.getProperty("java.home")
val javaHomeFromEnv = System.getenv("JAVA_HOME")

when {
    javaHomeFromLocal != null -> {
        System.setProperty("org.gradle.java.home", javaHomeFromLocal)
        println("Using JDK from local.properties: $javaHomeFromLocal")
    }
    javaHomeFromEnv != null -> {
        println("Using JDK from JAVA_HOME: $javaHomeFromEnv")
    }
    else -> {
        println("JDK not found in local.properties or JAVA_HOME. Gradle will try to use system default or download via Toolchain.")
    }
}

rootProject.name = "HomePlanner"
include(":app")

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}


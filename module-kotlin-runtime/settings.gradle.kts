pluginManagement {
    // Resolve the Kotlin JVM plugin implementation directly from Maven Central.
    // This avoids depending on the separate Gradle Plugin Portal marker artifact.
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "org.jetbrains.kotlin.jvm") {
                useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:${requested.version}")
            }
        }
    }
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
        // Official standalone R8 release repository documented by the R8 project.
        maven { url = uri("https://storage.googleapis.com/r8-releases/raw") }
    }
}

rootProject.name = "dsh-kotlin-runtime"

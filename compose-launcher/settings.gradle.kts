pluginManagement {
    // Kotlin 2.4 emits metadata 2.4.0; use an R8 release that can parse it.
    // AGP's bundled R8 can be older than the standalone D8 used by Legacy/Universal builds.
    buildscript {
        repositories {
            google()
            mavenCentral()
        }
        dependencies {
            classpath("com.android.tools:r8:9.1.29")
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
        maven("https://jitpack.io")
        maven("https://api.xposed.info/")
    }
}

rootProject.name = "deekseep-compose-launcher"
include(":app")

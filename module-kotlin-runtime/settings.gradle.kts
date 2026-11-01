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
        // Official standalone R8 release repository documented by the R8 project.
        maven { url = uri("https://storage.googleapis.com/r8-releases/raw") }
    }
}

rootProject.name = "dsh-kotlin-runtime"

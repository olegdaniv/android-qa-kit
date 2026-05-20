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
        maven { url = uri("https://jitpack.io") } // Chucker
    }
}

rootProject.name = "android-qa-kit"

include(":sample")
include(":qa-core")
include(":qa-ui-compose")
include(":qa-ui-view")
include(":qa-no-op")

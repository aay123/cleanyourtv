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
        // Kadb's Android artifact needs SPAKE2 from JitPack for wireless pairing.
        maven("https://jitpack.io") {
            content { includeGroup("com.github.Flyfish233") }
        }
    }
}

rootProject.name = "CleanYourTvMobile"
include(":app")

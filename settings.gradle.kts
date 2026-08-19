// SuperFlow - root project settings.
//
// The standard Android Gradle Plugin build is the only supported build path.
// All resource linking, R class generation, manifest merging, ${applicationId}
// replacement, dexing and signing are done by Gradle/AGP. The former
// Gradle-less pipeline (tools/build_apk.sh) was removed for this reason:
// it hand-rolled partial library R classes and a hand-assembled manifest,
// which is what caused the original launch crash.

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

rootProject.name = "SuperFlow"
include(":app")

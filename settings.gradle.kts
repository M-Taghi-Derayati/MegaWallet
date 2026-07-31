import java.net.URI

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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url= URI("https://maven.google.com/")
            isAllowInsecureProtocol=true}
        google()
        mavenCentral()
        maven { url= URI("https://jitpack.io")
            isAllowInsecureProtocol=true}

    }

}

rootProject.name = "MegaWallet"
include(":app")
include(":common_ui")
include(":data")
include(":domain")
include(":core")
include(":baselineprofile") // PERF-10

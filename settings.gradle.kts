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
        jcenter()
    }
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
        jcenter()
    }
}

rootProject.name = "MegaWallet"
include(":app")
include(":common_ui")
include(":data")
include(":domain")
include(":core")

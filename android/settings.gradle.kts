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

rootProject.name = "LocalRush"

include(":core:common")
include(":core:network")
include(":core:auth")
include(":core:designsystem")
include(":core:location")
include(":core:maps")
include(":core:push")
include(":core:database")
include(":feature:auth")
include(":app-customer")
include(":app-rider")
include(":app-partner")

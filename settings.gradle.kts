rootProject.name = "FoodRats"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

include(":androidApp")
include(":catalogApp")
include(":shared")

include(":core:domain")
include(":core:data")
include(":core:designsystem")
include(":core:presentation")
include(":core:i18n")

include(":feature:auth")
include(":feature:crew")
include(":feature:feed")
include(":feature:ingredient")
include(":feature:meal")
include(":feature:meal-ai")
include(":feature:notifications")
include(":feature:stats")

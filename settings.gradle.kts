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
    }
}

rootProject.name = "reviewflow"
include(":reviewflow-core", ":review-compose", ":sample-app")
project(":reviewflow-core").projectDir = file("review-core")

// Native SwiftUI sample; this framework adapter is never published.
include(":ios-demo-bridge")
project(":ios-demo-bridge").projectDir = file("samples/ios/bridge")

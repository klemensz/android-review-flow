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

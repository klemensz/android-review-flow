plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

group = providers.gradleProperty("GROUP").get()
version = providers.gradleProperty("VERSION_NAME").get()

subprojects {
    group = rootProject.group
    version = rootProject.version
}

tasks.register("releaseToMavenCentralPortal") {
    group = "publishing"
    description = "Publishes and releases review-core and review-compose via Maven Central Portal."
    dependsOn(
        ":review-core:releaseMavenCentralPortalPublication",
        ":review-compose:releaseMavenCentralPortalPublication",
    )
}

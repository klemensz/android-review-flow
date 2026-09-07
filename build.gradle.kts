import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.tasks.Delete

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

group = providers.gradleProperty("GROUP").get()
version = providers.gradleProperty("VERSION_NAME").get()

val releaseModules = setOf("reviewflow-core", "review-compose")
val verifyMavenCentralRelease = tasks.register("verifyMavenCentralRelease") {
    group = "verification"
    description = "Runs library unit tests and the sample-app integration smoke tests."
    dependsOn(
        ":reviewflow-core:allTests",
        ":reviewflow-core:checkLegacyAbi",
        ":review-compose:test",
        ":sample-app:test",
    )
}

val prepareMavenCentralRelease = tasks.register("prepareMavenCentralRelease") {
    group = "publishing"
    description = "Builds both Maven Central deployment bundles without uploading them."
    dependsOn(
        ":reviewflow-core:zipMavenCentralPortalPublication",
        ":review-compose:zipMavenCentralPortalPublication",
    )
}

val validateMavenCentralRelease = tasks.register("validateMavenCentralRelease") {
    group = "publishing"
    description = "Uploads and validates both Maven Central deployments without releasing them."
    dependsOn(
        prepareMavenCentralRelease,
        ":reviewflow-core:validateMavenCentralPortalPublication",
        ":review-compose:validateMavenCentralPortalPublication",
    )
}

subprojects {
    group = rootProject.group
    version = rootProject.version

    if (name in releaseModules) {
        pluginManager.withPlugin("org.danilopianini.publish-on-central") {
            val cleanMavenCentralPortalStaging = tasks.register<Delete>(
                "cleanMavenCentralPortalStaging",
            ) {
                group = "publishing"
                description = "Deletes local Maven Central staging outputs before rebuilding the deployment bundle."
                delete(
                    layout.buildDirectory.dir("project-local-repository"),
                    layout.buildDirectory.dir("maven-central-portal"),
                )
            }

            tasks.withType<PublishToMavenRepository>().configureEach {
                mustRunAfter(cleanMavenCentralPortalStaging)
            }

            tasks.named("zipMavenCentralPortalPublication") {
                dependsOn(
                    cleanMavenCentralPortalStaging,
                    "publishAllPublicationsToProjectLocalRepository",
                )
            }
            tasks.named("validateMavenCentralPortalPublication") {
                dependsOn(
                    "zipMavenCentralPortalPublication",
                    verifyMavenCentralRelease,
                )
                mustRunAfter(prepareMavenCentralRelease)
            }
            tasks.named("releaseMavenCentralPortalPublication") {
                dependsOn("validateMavenCentralPortalPublication")
                mustRunAfter(validateMavenCentralRelease)
            }
        }
    }
}

tasks.register("releaseToMavenCentralPortal") {
    group = "publishing"
    description = "Publishes and releases reviewflow-core and reviewflow-compose via Maven Central Portal."
    dependsOn(
        validateMavenCentralRelease,
        ":reviewflow-core:releaseMavenCentralPortalPublication",
        ":review-compose:releaseMavenCentralPortalPublication",
    )
}

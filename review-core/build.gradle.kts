import org.gradle.api.publish.maven.MavenPublication
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.publish.on.central)
    id("signing")
}

kotlin {
    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation {
        enabled.set(true)
    }

    android {
        namespace = "com.zleptnig.reviewflow.core"
        compileSdk = 36
        minSdk = 23

        withHostTestBuilder {}.configure {}

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }

        optimization {
            consumerKeepRules.apply {
                publish = true
                file("consumer-rules.pro")
            }
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
        iosX64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ReviewFlowCore"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.kotlinx.coroutines.play.services)
            implementation(libs.androidx.datastore.preferences)
            implementation(libs.google.play.review)
            implementation(libs.google.play.review.ktx)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.junit4)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.androidx.test.core)
            implementation(libs.robolectric)
        }
    }
}

publishOnCentral {
    repoOwner.set(providers.gradleProperty("POM_DEVELOPER_ID").get())
    projectDescription.set("Multiplatform review orchestration for Android and iOS.")
    licenseName.set(providers.gradleProperty("POM_LICENSE_NAME").get())
    licenseUrl.set(providers.gradleProperty("POM_LICENSE_URL").get())
}

afterEvaluate {
    publishing {
        publications.withType<MavenPublication>().configureEach {
            pom {
                name.set("${providers.gradleProperty("POM_NAME").get()} Core")
                description.set("Multiplatform review orchestration for Android and iOS.")
                url.set(providers.gradleProperty("POM_URL").get())

                scm {
                    url.set(providers.gradleProperty("POM_SCM_URL").get())
                    connection.set(providers.gradleProperty("POM_SCM_CONNECTION").get())
                    developerConnection.set(providers.gradleProperty("POM_SCM_DEV_CONNECTION").get())
                }

                developers {
                    developer {
                        id.set(providers.gradleProperty("POM_DEVELOPER_ID").get())
                        name.set(providers.gradleProperty("POM_DEVELOPER_NAME").get())
                        url.set(providers.gradleProperty("POM_DEVELOPER_URL").get())
                    }
                }
            }
        }
    }
}

signing {
    val signingKey = providers.gradleProperty("SIGNING_KEY")
        .orElse(providers.environmentVariable("SIGNING_KEY"))
        .orNull
    val signingKeyFile = providers.gradleProperty("SIGNING_KEY_FILE")
        .orElse(providers.environmentVariable("SIGNING_KEY_FILE"))
        .orNull
    val signingPassword = providers.gradleProperty("SIGNING_PASSWORD")
        .orElse(providers.environmentVariable("SIGNING_PASSWORD"))
        .orNull

    val resolvedKey = when {
        !signingKey.isNullOrBlank() -> signingKey
        !signingKeyFile.isNullOrBlank() -> file(signingKeyFile).readText(Charsets.UTF_8)
        else -> null
    }

    if (!resolvedKey.isNullOrBlank() && !signingPassword.isNullOrBlank()) {
        useInMemoryPgpKeys(resolvedKey, signingPassword)
        afterEvaluate {
            sign(publishing.publications)
        }
    }
}

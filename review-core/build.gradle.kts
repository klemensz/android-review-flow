plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("maven-publish")
    id("signing")
}

android {
    namespace = "com.zleptnig.reviewflow.core"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.google.play.review)
    implementation(libs.google.play.review.ktx)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])

                groupId = project.group.toString()
                artifactId = "reviewflow-core"
                version = project.version.toString()

                pom {
                    name.set("${providers.gradleProperty("POM_NAME").get()} Core")
                    description.set("Core orchestration module for ${providers.gradleProperty("POM_NAME").get()}.")
                    url.set(providers.gradleProperty("POM_URL").get())

                    licenses {
                        license {
                            name.set(providers.gradleProperty("POM_LICENSE_NAME").get())
                            url.set(providers.gradleProperty("POM_LICENSE_URL").get())
                            distribution.set(providers.gradleProperty("POM_LICENSE_DIST").get())
                        }
                    }

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

        repositories {
            mavenLocal()

            maven {
                name = "MavenCentral"
                val releaseUrl = providers.gradleProperty("MAVEN_CENTRAL_RELEASE_URL").get()
                val snapshotUrl = providers.gradleProperty("MAVEN_CENTRAL_SNAPSHOT_URL").get()
                url = uri(
                    if (version.toString().endsWith("SNAPSHOT")) snapshotUrl else releaseUrl,
                )

                credentials {
                    username = providers.gradleProperty("OSSRH_USERNAME")
                        .orElse(providers.environmentVariable("OSSRH_USERNAME"))
                        .orNull
                    password = providers.gradleProperty("OSSRH_PASSWORD")
                        .orElse(providers.environmentVariable("OSSRH_PASSWORD"))
                        .orNull
                }
            }
        }
    }
}

signing {
    val signingKeyId = providers.gradleProperty("SIGNING_KEY_ID")
        .orElse(providers.environmentVariable("SIGNING_KEY_ID"))
        .orNull
    val signingKey = providers.gradleProperty("SIGNING_KEY")
        .orElse(providers.environmentVariable("SIGNING_KEY"))
        .orNull
    val signingPassword = providers.gradleProperty("SIGNING_PASSWORD")
        .orElse(providers.environmentVariable("SIGNING_PASSWORD"))
        .orNull

    if (!signingKey.isNullOrBlank() && !signingPassword.isNullOrBlank()) {
        useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword)
        sign(publishing.publications)
    }
}

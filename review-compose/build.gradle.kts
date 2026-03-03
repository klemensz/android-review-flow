plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("maven-publish")
    id("signing")
}

android {
    namespace = "com.zleptnig.reviewflow.compose"
    compileSdk = 36

    defaultConfig { minSdk = 23 }

    buildFeatures {
        compose = true
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
    api(project(":review-core"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.activity.compose)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])

                groupId = project.group.toString()
                artifactId = "reviewflow-compose"
                version = project.version.toString()

                pom {
                    name.set("${providers.gradleProperty("POM_NAME").get()} Compose")
                    description.set("Compose integration module for ${providers.gradleProperty("POM_NAME").get()}.")
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
                    username = providers.gradleProperty("MAVEN_CENTRAL_USERNAME")
                        .orElse(providers.gradleProperty("OSSRH_USERNAME"))
                        .orElse(providers.environmentVariable("MAVEN_CENTRAL_USERNAME"))
                        .orElse(providers.environmentVariable("OSSRH_USERNAME"))
                        .orNull
                    password = providers.gradleProperty("MAVEN_CENTRAL_PASSWORD")
                        .orElse(providers.gradleProperty("OSSRH_PASSWORD"))
                        .orElse(providers.environmentVariable("MAVEN_CENTRAL_PASSWORD"))
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

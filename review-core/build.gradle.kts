plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.publish.on.central)
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

publishOnCentral {
    repoOwner.set(providers.gradleProperty("POM_DEVELOPER_ID").get())
    projectDescription.set("Core orchestration module for ${providers.gradleProperty("POM_NAME").get()}.")
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

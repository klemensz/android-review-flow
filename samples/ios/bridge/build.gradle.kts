plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    listOf(iosArm64(), iosSimulatorArm64(), iosX64()).forEach { target ->
        target.binaries.framework {
            baseName = "ReviewFlowDemoKit"
            isStatic = true
            export(project(":reviewflow-core"))
        }
    }
    sourceSets {
        iosMain.dependencies {
            api(project(":reviewflow-core"))
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}

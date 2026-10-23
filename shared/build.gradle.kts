import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.koinCompiler)
    alias(libs.plugins.aboutLibraries)
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    jvm()

    androidLibrary {
        namespace = "io.github.diasdominik.sft.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        androidResources {
            enable = true
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.android)
            implementation(libs.ktor.client.okhttp)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)

            implementation(libs.navigation3.ui)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.androidx.lifecycle.viewmodelCompose)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ksafe)
            implementation(libs.ktor.client.core)
            implementation(libs.kotlinx.io.core)
            implementation(libs.aboutlibraries.compose.m3)

            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.annotations)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.compose.uiTest)
        }
    }
}

aboutLibraries {
    collect {
        // Code and data reused from other projects that aren't Gradle dependencies.
        configPath = file("aboutlibraries")
    }
    export {
        excludeFields.addAll("funding")
    }
    library {
        // One entry per library instead of one per platform artifact (foo-android, foo-jvm, ...).
        mergePlatformArtifacts = true
    }
}

// The licenses screen reads files/aboutlibraries.json from the compose resources. It is generated on every build
// rather than committed, so it always matches the dependencies: the common resources are copied next to it and
// the copy is what compose packages.
val composeResourcesDir = layout.buildDirectory.dir("generated/composeResourcesWithLicenses")
val composeResourcesWithLicenses = tasks.register<Sync>("composeResourcesWithLicenses") {
    from("src/commonMain/composeResources")
    from(tasks.named("exportLibraryDefinitions")) { into("files") }
    into(composeResourcesDir)
}

compose.resources {
    publicResClass = true
    packageOfResClass = "io.github.diasdominik.sft.resources"
    customDirectory(
        sourceSetName = "commonMain",
        directoryProvider = composeResourcesWithLicenses.map { composeResourcesDir.get() },
    )
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}

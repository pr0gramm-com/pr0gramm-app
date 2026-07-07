import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    // AGP 9 tooling (ddmlib/repository) wants org.jetbrains:annotations 23.x, but Gradle's
    // embedded Kotlin strictly pins it to 13.0 on the buildscript classpath. Force the newer
    // one so the plugin classpath resolves.
    configurations.classpath {
        resolutionStrategy.force("org.jetbrains:annotations:23.0.0")
    }
}

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.google.ksp) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.sqldelight) apply false

    alias(libs.plugins.gradle.versions)
}

/*
tasks.named<DependencyUpdatesTask>("dependencyUpdates").configure {
    resolutionStrategy {
        componentSelection {
            all {
                val rejected = listOf("alpha", "beta", "rc", "cr", "-m").any {
                    candidate.version.lowercase().contains(it)
                }

                if (rejected) {
                    reject("Release candidate")
                }
            }
        }
    }
}
*/

allprojects {
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            optIn.addAll(
                "kotlin.ExperimentalStdlibApi",
                "kotlin.contracts.ExperimentalContracts",
                "kotlinx.coroutines.ExperimentalCoroutinesApi",
                "kotlinx.coroutines.FlowPreview"
            )

            if (name == "compileReleaseKotlin") {
                freeCompilerArgs.addAll(
                    listOf(
                        "-Xno-call-assertions",
                        "-Xno-param-assertions",
                        "-Xno-receiver-assertions"
                    )
                )
            }
        }
    }
}

import org.gradle.internal.os.OperatingSystem

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

apply(from = "version.gradle.kts")
val appVersion: Int by extra

android {
    namespace = "com.pr0gramm.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.pr0gramm.app"
        minSdk = 23
        targetSdk = 37
        versionCode = appVersion
        versionName = "1.${(appVersion / 10)}.${(appVersion % 10)}"

        androidResources.localeFilters += listOf("en", "de")

        vectorDrawables.useSupportLibrary = true

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("../release.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // jvmTarget defaults to android.compileOptions.targetCompatibility (17) with built-in Kotlin

    buildFeatures {
        viewBinding = true
        buildConfig = true
        compose = true
    }


    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
            isShrinkResources = false
            isCrunchPngs = false

            proguardFiles("proguard-rules-base.pro", "proguard-rules-debug.pro")

            versionNameSuffix = ".dev"
            applicationIdSuffix = ".dev"
        }

        getByName("release") {
            isMinifyEnabled = true
            // setting this to true prevents the in-app update dialog to work.
            isShrinkResources = false
            isCrunchPngs = false

            proguardFiles("proguard-rules-base.pro", "proguard-rules-release.pro")

            signingConfig = signingConfigs.getByName("release")
        }
    }

    packaging {
        resources.excludes += listOf(
            "META-INF/NOTICE",
            "META-INF/LICENSE",
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE.txt",
            "META-INF/NOTICE.txt",
            "META-INF/*.kotlin_module",
            "**/*.kotlin_builtins",
            "**/*.kotlin_metadata"
        )
    }


    lint {
        checkReleaseBuilds = false
    }
}

androidComponents {
    onVariants { variant ->
        val variantName = variant.name.replaceFirstChar { it.uppercase() }

        tasks.matching { it.name == "merge${variantName}JniLibFolders" }.configureEach {
            doLast {
                fileTree("build/") {
                    include("**/armeabi/libpl_droidsonroids_gif.so")
                    include("**/mips*/*.so")
                }.forEach { it.delete() }
            }
        }


        tasks.matching { it.name == "package${variantName}" }.configureEach {
            doLast {
                println("Checking for important files in the apk...")

                val buildDirFile = layout.buildDirectory.get().asFile
                val pathsToApk = listOf(
                    "$buildDirFile/outputs/apk/${variant.name}/app-${variant.name}.apk",
                    "$buildDirFile/intermediates/apk/${variant.name}/app-${variant.name}.apk"
                )

                if (pathsToApk.none { file(it).exists() }) {
                    throw RuntimeException("No .apk file found.")
                }

                pathsToApk.forEach { pathToApk ->
                    println(pathToApk)
                    if (file(pathToApk).exists()) {
                        val output = providers.exec {
                            if (OperatingSystem.current().isWindows) {
                                commandLine("tar", "-tf", pathToApk)
                            } else {
                                commandLine("unzip", "-v", pathToApk)
                            }
                        }

                        println(output.result.get())

                        val outputStr = output.standardOutput.asText.get()

                        if (!outputStr.contains("okhttp3/internal/publicsuffix/publicsuffixes.gz")) {
                            throw RuntimeException("publicsuffixes.gz not found in build")
                        }
                    }
                }
            }
        }
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))
    implementation(project(":model"))

    implementation(libs.androidx.core.ktx)

    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.view.material)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.drawerlayout)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.user.messaging.platform)

    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)

    implementation(libs.androidx.fragment.ktx)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.paging.compose)
    // Preview support
    implementation(libs.androidx.ui.tooling.preview)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.play.services.ads)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)

    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.moshi)

    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.dnsoverhttps)
    implementation(libs.okhttp.brotli)

    implementation(libs.picasso)

    // Coil 3 for Compose image loading (additive; Picasso stays for View code)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.gif)

    implementation(libs.moshi)

    implementation(libs.droidsonroids.gifdrawable)

    implementation(libs.namedregex)
    implementation(libs.materialishprogress)
    implementation(libs.proguard.annotations)
    implementation(libs.subsamplingimageview)
    implementation(libs.dogstadClient)
    implementation(variantOf(libs.shortcutbadger) { artifactType("aar") })
    // implementation("com.github.AlexKorRnd:ChipsLayoutManager:v0.3.8.4")
    // implementation("ir.mahdiparastesh:chipslayoutmanager:0.5.0@aar")
    implementation(libs.speeddial)

    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.appintro)

    implementation(libs.androidx.media3.exoplayer)
}

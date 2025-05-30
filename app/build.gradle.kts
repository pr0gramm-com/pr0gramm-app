import org.gradle.internal.os.OperatingSystem
import java.io.ByteArrayOutputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

apply(from = "version.gradle.kts")
val appVersion: Int by extra

android {
    namespace = "com.pr0gramm.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pr0gramm.app"
        minSdk = 23
        targetSdk = 33
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.13"
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

android.applicationVariants.configureEach {
    val variant = name

    tasks.named("merge${variant.capitalize()}JniLibFolders").configure {
        doLast {
            fileTree("build/") {
                include("**/armeabi/libpl_droidsonroids_gif.so")
                include("**/mips*/*.so")
            }.forEach { it.delete() }
        }
    }


    tasks.named("package${variant.capitalize()}").configure {
        doLast {
            println("Checking for important files in the apk...")

            val pathsToApk = listOf(
                "$buildDir/outputs/apk/$variant/app-$variant.apk",
                "$buildDir/intermediates/apk/$variant/app-$variant.apk"
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
    implementation(libs.androidx.compose.material)
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

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.ksp)
    alias(libs.plugins.sqldelight)
}

android {
    namespace = "com.pr0gramm.app.model"
    compileSdk = 35


    defaultConfig {
        minSdk = 23
        lint.targetSdk = 31

        testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"

        consumerProguardFiles("proguard-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
    }
}

sqldelight {
    databases {
        create("AppDB") {
            //package name used for the generated MyDatabase.kt
            packageName.set("com.pr0gramm.app.db")

            // The directory where to store '.db' schema files relative to the root of the project.
            // These files are used to verify that migrations yield a database with the latest schema.
            // Defaults to null so the verification tasks will not be created.
            schemaOutputDirectory.set(file("src/main/sqldelight/databases"))
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.moshi)
    implementation(libs.retrofit.core)

    api(libs.sqldelight.primitiveadapters)
    api(libs.sqldelight.androiddriver)
    api(libs.sqldelight.coroutinesextensions)

    ksp(libs.moshi.codegen)
}

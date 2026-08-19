// SuperFlow app module.
//
// Conventional AGP build: dependency resolution, resource linking, R class
// generation, manifest merging (including the androidx.startup and WorkManager
// components contributed by library AARs), Kotlin compilation, D8 dexing and
// debug signing are all performed by Gradle.

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.superflow"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.superflow"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "2.0.0"

        // Vector drawables are used everywhere; keep the support library
        // fallback for pre-21 vector features (minSdk is 26, this is a no-op
        // kept for clarity).
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isDebuggable = true
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        buildConfig = true
    }

    lint {
        // Lint must never block a build over style warnings; real issues are
        // reviewed via ./gradlew lintDebug.
        abortOnError = false
        checkReleaseBuilds = false
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }

    packaging {
        resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.sqlite)
    implementation(libs.androidx.sqlite.framework)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)

    // JVM unit tests
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Pure-JVM org.json so logic tests that build JSON run without the
    // Android framework (the app itself uses the framework's org.json).
    testImplementation(libs.org.json)

    // Instrumented tests
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.espresso.core)
}

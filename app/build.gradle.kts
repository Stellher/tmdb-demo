plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Demo only: intentionally embedded test credential.
val tmdbReadToken = "eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiI3YTZhNzMyNmQyY2U5MjkwOTQ3OTJjYjVhMjA3NGE5ZiIsIm5iZiI6MTc3ODc3NjU3NS43MzksInN1YiI6IjZhMDVmOWZmNWJiMDgwYWNhNWRiMTkwOSIsInNjb3BlcyI6WyJhcGlfcmVhZCJdLCJ2ZXJzaW9uIjoxfQ.qr2TASl1r8j_bpfVua4JX6f9ja9LKwLSjNTo9FWDKGI"

android {
    namespace = "com.stellher.tmdbdemo"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.stellher.tmdbdemo"
        minSdk = 26
        targetSdk = 35
        // CI builds have monotonically increasing codes for in-place upgrades.
        versionCode = 10000 + (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0)
        versionName = "1.1." + (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0)
        buildConfigField("String", "TMDB_READ_TOKEN", "\"" + tmdbReadToken + "\"")
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    signingConfigs {
        // Fixed keystore tracked for this disposable demo only.
        create("demoRelease") {
            storeFile = rootProject.file("app/signing/tmdb-demo-release.jks")
            storePassword = "tmdb-demo-2026"
            keyAlias = "tmdb-demo"
            keyPassword = "tmdb-demo-2026"
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("demoRelease")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}
kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}
dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(project(":feature:search"))
    implementation(project(":feature:detail"))
    implementation(platform("androidx.compose:compose-bom:2025.10.01"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.navigation:navigation-compose:2.9.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
}

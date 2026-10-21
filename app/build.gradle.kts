plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
val tmdbToken = providers.gradleProperty("tmdbToken")
    .orElse(providers.environmentVariable("TMDB_READ_TOKEN"))
    .orElse("")
val token = tmdbToken.get().replace("\\", "\\\\").replace("\"", "\\\"")
android {
    namespace = "com.stellher.tmdbdemo"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.stellher.tmdbdemo"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("String", "TMDB_READ_TOKEN", "\"" + token + "\"")
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    // The CI's temporary demo signing key is intentionally NOT suitable for real production releases.
    signingConfigs {
        create("demoRelease") {
            val path = System.getenv("DEMO_KEYSTORE_PATH")
            if (!path.isNullOrBlank()) {
                storeFile = file(path)
                storePassword = System.getenv("DEMO_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("DEMO_KEY_ALIAS")
                keyPassword = System.getenv("DEMO_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            if (!System.getenv("DEMO_KEYSTORE_PATH").isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("demoRelease")
            }
        }
    }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
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

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.stellher.tmdbdemo.core.ui"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    api(platform("androidx.compose:compose-bom:2025.10.01"))
    api("androidx.compose.material3:material3")
    api("androidx.compose.foundation:foundation")
    api("androidx.compose.ui:ui")
    api("androidx.compose.material:material-icons-extended")
    api("io.coil-kt.coil3:coil-compose:3.3.0")
    runtimeOnly("io.coil-kt.coil3:coil-network-okhttp:3.3.0")
    api("dev.chrisbanes.haze:haze:1.7.1")
}

plugins {
    id("com.android.application")
    kotlin("android")
    id("com.neusoft.oper-log") version "1.0.0"
}

android {
    namespace = "com.neusoft.sample.android11"
    compileSdk = 33

    defaultConfig {
        applicationId = "com.neusoft.sample.android11"
        minSdk = 30
        targetSdk = 30
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
    }
}

operLog {
    enabled = true
    enableInRelease = false
    includePackages = listOf("com.neusoft.sample")
    printArgs = true
    printThread = true
    printResult = true
    measureTime = true
}

dependencies {
    // Only oper-log-runtime needed; oper-log-annotation is transitively provided
    implementation("com.neusoft.operlog:oper-log-runtime:1.0.0")
}

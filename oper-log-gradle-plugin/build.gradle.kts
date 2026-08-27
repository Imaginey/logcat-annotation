plugins {
    `java-gradle-plugin`
    kotlin("jvm")
    `maven-publish`
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}

gradlePlugin {
    plugins {
        create("operLog") {
            id = "com.neusoft.oper-log"
            implementationClass = "com.neusoft.operlog.plugin.OperLogGradlePlugin"
            displayName = "OperLog Gradle Plugin"
            description = "Cross-platform Android method log annotation framework"
        }
    }
}

dependencies {
    compileOnly("com.android.tools.build:gradle:7.2.2")
    implementation(project(":oper-log-bytecode-core"))
    implementation(project(":oper-log-agp-modern"))
    implementation(project(":oper-log-agp-legacy"))
}

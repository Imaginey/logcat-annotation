plugins {
    kotlin("jvm")
    `maven-publish`
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

dependencies {
    implementation(project(":oper-log-annotation"))
    implementation("org.ow2.asm:asm:9.1")
    implementation("org.ow2.asm:asm-commons:9.1")
    implementation("org.ow2.asm:asm-util:9.1")

    testImplementation(project(":oper-log-runtime"))
}

val runBytecodeTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs the OperLog Bytecode Core ASM verification tests"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.neusoft.operlog.bytecode.BytecodeCoreTestKt")
}

tasks.named("test") {
    dependsOn(runBytecodeTest)
}

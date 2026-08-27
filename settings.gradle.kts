pluginManagement {
    repositories {
        mavenLocal()
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        mavenLocal()
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        google()
        mavenCentral()
    }
}

rootProject.name = "oper-log-framework"

include(":oper-log-annotation")
include(":oper-log-runtime")
include(":oper-log-bytecode-core")
include(":oper-log-agp-legacy")
include(":oper-log-agp-modern")
include(":oper-log-gradle-plugin")

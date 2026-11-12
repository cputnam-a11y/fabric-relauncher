pluginManagement {
    includeBuild("build-logic")
    repositories {
        maven {
            name = "Fabric"
            url = uri("https://maven.fabricmc.net/")
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("net.fabricmc.fabric-loom-repositories") version providers.gradleProperty("loom_version")
}

dependencyResolutionManagement {
        repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        maven {
            url = uri("https://mvn.devos.one/releases")
        }
        mavenCentral()
    }
}

rootProject.name = "fabricrelauncher"

include("task-model", "agent", "relauncher")
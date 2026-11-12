@file:Suppress("UnstableApiUsage")

plugins {
    `java-library`
    id("illuminate")
}

tasks.jar {
    manifest.attributes["Premain-Class"] = "io.github.cputnama11y.modrinthunsuprelaunch.Agent"
}

dependencies {
    // idea fix your broken ide
    // thanks actually, I didn't realize you already did :)
    /*compileOnlyApi*/(illuminate(project(":task-model"))!!)
}
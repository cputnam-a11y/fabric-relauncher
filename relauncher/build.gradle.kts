plugins {
    id("illuminate")
    id("net.fabricmc.fabric-loom")
}

loom.runConfigs.configureEach {
    generateRunConfig = true
}

loom.mods.register("fabric-relauncher") {
    this.sourceSet(sourceSets.main.get())
    dependency(dependencies.project(":agent", "shadow"))
}

var agent = configurations.register("agent") {
    isTransitive = false
}

dependencies {
    minecraft("com.mojang:minecraft:${providers.gradleProperty("minecraft_version").get()}")
    implementation("net.fabricmc:fabric-loader:${providers.gradleProperty("loader_version").get()}")
    agent(illuminate(project(":agent", "shadow"))!!)
}

tasks.processResources {
    val version = version
    inputs.property("version", version)
    inputs.files(agent)

    filesMatching("fabric.mod.json") {
        expand("version" to version)
    }

    from(agent) {
        rename { "agent.jar" }
        into("META-INF/agent")
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
}

java {
    withSourcesJar()

    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}
plugins {
    id("com.gradleup.shadow")
    `java-library`
}

// region illuminate
val illuminate = configurations.register("illuminate")

configurations.shadow {
    extendsFrom(configurations.implementation)
}

configurations.runtimeClasspath {
    extendsFrom(illuminate)
}

configurations.compileOnly {
    extendsFrom(illuminate)
}

tasks.shadowJar {
    configurations = illuminate.map(::listOf)
}
// endregion illuminate
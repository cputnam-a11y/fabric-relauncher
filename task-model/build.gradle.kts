@file:Suppress("UnstableApiUsage")

import org.gradle.kotlin.dsl.`java-library`
import org.gradle.kotlin.dsl.version

plugins {
    `java-library`
}

dependencies {
    api("fish.cichlidmc:tiny-codecs:6.1.0")
}
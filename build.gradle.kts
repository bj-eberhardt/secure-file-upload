plugins {
    kotlin("jvm") version "2.1.0" apply false
    kotlin("plugin.allopen") version "2.1.0" apply false
    id("io.micronaut.application") version "4.5.0" apply false
    id("com.github.node-gradle.node") version "7.1.0" apply false
}

tasks.register("buildAll") {
    dependsOn(":frontend:copyFrontendToBackend")
    dependsOn(":backend:build")
}

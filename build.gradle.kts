plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.allopen) apply false
    alias(libs.plugins.micronaut.application) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.node) apply false
    alias(libs.plugins.shadow) apply false
}

tasks.register("buildAll") {
    dependsOn(":frontend:copyFrontendToBackend", ":backend:build")
}

gradle.taskGraph.whenReady {
    val frontendTask = rootProject.tasks.findByPath(":frontend:copyFrontendToBackend")
    val backendTask = rootProject.tasks.findByPath(":backend:build")
    if (frontendTask != null && backendTask != null) {
        backendTask.mustRunAfter(frontendTask)
    }
}

plugins {
    id("com.github.node-gradle.node")
}

node {
    download.set(true)
    version.set("22.11.0")
    npmVersion.set("10.9.0")
}

tasks.register<com.github.gradle.node.npm.task.NpmTask>("npmBuild") {
    dependsOn("npmInstall")
    args.set(listOf("run", "build"))
}

tasks.register<Copy>("copyFrontendToBackend") {
    dependsOn("npmBuild")
    from(layout.projectDirectory.dir("dist"))
    into(rootProject.layout.projectDirectory.dir("backend/src/main/resources/public"))
}

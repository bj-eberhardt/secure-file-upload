plugins {
    alias(libs.plugins.node)
}

node {
    download.set(true)
    // Vite 8 requires Node.js 20.19+ or 22.12+
    version.set("22.12.0")
    // Use a stable, project-local directory to reduce Windows file lock issues during clean/build cycles.
    workDir.set(rootProject.layout.projectDirectory.dir(".gradle-user-home/nodejs").asFile)
    npmWorkDir.set(rootProject.layout.projectDirectory.dir(".gradle-user-home/npm").asFile)
}

// Prevent nodeSetup from running when the configured workDir already exists.
// This avoids deleting/recreating the folder if a developer has a persistent local Node install here.
// We use a tolerant reflection-based approach to support different plugin versions (File, Directory, Provider).
tasks.named("nodeSetup") {
    onlyIf {
        val nodeExt = project.extensions.findByName("node")
        val workDirFile = try {
            val w = nodeExt?.javaClass?.getMethod("getWorkDir")?.invoke(nodeExt)
            when (w) {
                is java.io.File -> w
                is org.gradle.api.file.Directory -> w.asFile
                is org.gradle.api.provider.Provider<*> -> {
                    val v = w.get()
                    when (v) {
                        is java.io.File -> v
                        is org.gradle.api.file.Directory -> v.asFile
                        else -> file("${'$'}buildDir/nodejs")
                    }
                }
                else -> file("${'$'}buildDir/nodejs")
            }
        } catch (_: Exception) {
            file("${'$'}buildDir/nodejs")
        }
        !workDirFile.exists()
    }
}

tasks.named<com.github.gradle.node.npm.task.NpmInstallTask>("npmInstall") {
    // Use a project-local cache/log dir so installs work in sandboxed environments.
    val cacheDir = layout.buildDirectory.dir("npm-cache").get().asFile.absolutePath
    args.set(listOf("install", "--cache", cacheDir, "--prefer-online", "--no-offline", "--no-audit", "--no-fund"))
}

tasks.register<com.github.gradle.node.npm.task.NpmTask>("npmBuild") {
    dependsOn("npmInstall")
    environment.put("NODE_OPTIONS", "--require=./scripts/node-preload.cjs")
    args.set(listOf("run", "build"))
}

tasks.register<com.github.gradle.node.npm.task.NpmTask>("npmPlaywrightInstall") {
    dependsOn("npmInstall")
    environment.put("PLAYWRIGHT_BROWSERS_PATH", "0")
    args.set(listOf("exec", "--", "playwright", "install", "chromium"))
}

tasks.register<com.github.gradle.node.npm.task.NpmTask>("npmE2e") {
    dependsOn("copyFrontendToBackend")
    environment.put("PLAYWRIGHT_BROWSERS_PATH", "0")
    // Force Playwright-managed Chromium for deterministic runs (ignore any globally set PW_CHANNEL).
    environment.put("PW_CHANNEL", "")
    args.set(listOf("run", "test:e2e"))
}

tasks.register<com.github.gradle.node.npm.task.NpmTask>("npmE2eUi") {
    dependsOn("copyFrontendToBackend")
    environment.put("PLAYWRIGHT_BROWSERS_PATH", "0")
    // Force Playwright-managed Chromium for deterministic runs (ignore any globally set PW_CHANNEL).
    environment.put("PW_CHANNEL", "")
    args.set(listOf("run", "test:e2e:ui"))
}

tasks.register<Copy>("copyFrontendToBackend") {
    dependsOn("npmBuild")
    from(layout.projectDirectory.dir("dist"))
    into(rootProject.layout.projectDirectory.dir("backend/src/main/resources/public"))
}

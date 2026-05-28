plugins {
    alias(libs.plugins.node)
}

node {
    val nodeVersion = "22.13.0"
    download.set(true)
    // Vite 8 requires Node.js 20.19+ or 22.12+
    // Keep in sync with toolchain requirements of devDependencies (e.g. ESLint stack).
    version.set(nodeVersion)
    // Use a stable, project-local directory to reduce Windows file lock issues during clean/build cycles.
    // Version-scoped dirs avoid Windows file-lock issues when switching Node versions.
    workDir.set(rootProject.layout.projectDirectory.dir(".gradle-user-home/nodejs/${'$'}nodeVersion").asFile)
    npmWorkDir.set(rootProject.layout.projectDirectory.dir(".gradle-user-home/npm/${'$'}nodeVersion").asFile)
}

tasks.named<com.github.gradle.node.npm.task.NpmInstallTask>("npmInstall") {
    // Use a project-local cache/log dir so installs work in sandboxed environments.
    val cacheDir = layout.buildDirectory.dir("npm-cache").get().asFile.absolutePath
    args.set(listOf("install", "--cache", cacheDir, "--prefer-online", "--no-offline", "--no-audit", "--no-fund"))
}

tasks.register<com.github.gradle.node.npm.task.NpmTask>("npmLint") {
    dependsOn("npmInstall")
    args.set(listOf("run", "lint"))
}

tasks.register<com.github.gradle.node.npm.task.NpmTask>("npmBuild") {
    dependsOn("npmLint")
    environment.put("NODE_OPTIONS", "--require=./scripts/node-preload.cjs")
    args.set(listOf("run", "build"))
}

tasks.register<com.github.gradle.node.npm.task.NpmTask>("npmPlaywrightInstall") {
    dependsOn("npmInstall")
    environment.put("PLAYWRIGHT_BROWSERS_PATH", "0")
    args.set(listOf("exec", "--", "playwright", "install", "chromium"))
}

tasks.register<com.github.gradle.node.npm.task.NpmTask>("npmPlaywrightInstallAll") {
    dependsOn("npmInstall")
    environment.put("PLAYWRIGHT_BROWSERS_PATH", "0")
    args.set(listOf("exec", "--", "playwright", "install", "chromium", "firefox"))
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

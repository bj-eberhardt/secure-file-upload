plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.allopen)
    alias(libs.plugins.ksp)
    alias(libs.plugins.micronaut.application)
    alias(libs.plugins.shadow)
    jacoco
}

version = "0.1.0"
group = "app"

java {
    sourceCompatibility = JavaVersion.toVersion("25")
}

val micronautVersion: String by project

micronaut {
    runtime("netty")
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("app.*")
    }
}

dependencies {
    implementation(platform("io.micronaut.platform:micronaut-platform:$micronautVersion"))
    testImplementation(platform("io.micronaut.platform:micronaut-platform:$micronautVersion"))

    ksp(platform("io.micronaut.platform:micronaut-platform:$micronautVersion"))
    ksp("io.micronaut:micronaut-inject-kotlin")
    ksp("io.micronaut.validation:micronaut-validation-processor")
    ksp("io.micronaut.serde:micronaut-serde-processor")
    // Micronaut OpenAPI KSP processor - generates OpenAPI under META-INF/swagger
    ksp(libs.micronautOpenapi)

    implementation("io.micronaut.kotlin:micronaut-kotlin-runtime")
    implementation("io.micronaut.serde:micronaut-serde-jackson")
    implementation("io.micronaut:micronaut-jackson-databind")
    implementation("io.micronaut.validation:micronaut-validation")
    implementation("io.micronaut:micronaut-http-server-netty")
    implementation("io.micronaut:micronaut-management")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    // Optional: swagger annotations if you want explicit swagger annotations
    implementation(libs.swaggerAnnotations)
    runtimeOnly("ch.qos.logback:logback-classic")
    runtimeOnly(libs.snakeyaml)
    runtimeOnly("tools.jackson.module:jackson-module-kotlin")
    testImplementation("io.micronaut.test:micronaut-test-junit5")
    testImplementation("io.micronaut:micronaut-http-client")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

application {
    mainClass.set("app.ApplicationKt")
}

fun forwardSystemProperties(vararg prefixes: String): Map<String, Any> {
    val props = System.getProperties()
    val out = linkedMapOf<String, Any>()
    for ((kAny, vAny) in props) {
        val k = kAny?.toString() ?: continue
        if (prefixes.any { k.startsWith(it) }) {
            out[k] = vAny
        }
    }
    return out
}

// Ensure `./gradlew :backend:run -D...` forwards selected system properties to the application JVM.
tasks.named<JavaExec>("run") {
    systemProperties(forwardSystemProperties("micronaut.", "secure-file-upload."))
}

tasks.jacocoTestReport {

    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.90".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.70".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

tasks.named("build") {
    dependsOn("shadowJar")
    dependsOn(":frontend:copyFrontendToBackend")
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(":frontend:copyFrontendToBackend")
}

// `:frontend:copyFrontendToBackend` writes into `backend/src/main/resources/public`.
// `inspectRuntimeClasspath` reads the backend resources; declare the dependency explicitly
// to satisfy Gradle task dependency validation (Gradle 9+).
tasks.named("inspectRuntimeClasspath") {
    dependsOn(":frontend:copyFrontendToBackend")
}

import java.net.URI

plugins {
    java
    alias(libs.plugins.license)
}

license {
    header = rootProject.file("HEADER")
    mapping(mapOf("java" to "SLASHSTAR_STYLE"))
    excludes(listOf("**/*.json"))
    strictCheck = true
}

// The environment variables ARTIFACTORY_USER and ARTIFACTORY_ACCESS_TOKEN are used on CI env
// On local box, please add artifactoryUrl, artifactoryUsername and artifactoryPassword to ~/.gradle/gradle.properties
val artifactoryUrl = System.getenv("ARTIFACTORY_URL")
    ?: (if (project.hasProperty("artifactoryUrl")) project.property("artifactoryUrl").toString() else "")
val artifactoryUsername = System.getenv("ARTIFACTORY_USER")
    ?: (if (project.hasProperty("artifactoryUsername")) project.property("artifactoryUsername").toString() else "")
val artifactoryPassword = System.getenv("ARTIFACTORY_ACCESS_TOKEN")
    ?: (if (project.hasProperty("artifactoryPassword")) project.property("artifactoryPassword").toString() else "")

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    if (artifactoryUrl.isNotEmpty() && artifactoryUsername.isNotEmpty() && artifactoryPassword.isNotEmpty()) {
        maven("$artifactoryUrl/sonarsource") {
            credentials {
                username = artifactoryUsername
                password = artifactoryPassword
            }
        }
    } else {
        mavenCentral()
    }
}

dependencies {
    testImplementation(project(":"))

    // Testcontainers
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.jupiter)

    // Test frameworks
    testImplementation(libs.assertj)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

val cagVersion = rootProject.property("sonarContextAugmentationVersion") as String

tasks.register("downloadCagBinary") {
    description = "Downloads the sonar-context-augmentation Alpine binary for integration tests"
    group = "verification"

    val outputFile = file("src/test/resources/binaries/sonar-context-augmentation")
    outputs.file(outputFile)

    onlyIf { !outputFile.exists() }

    doLast {
        val arch = "x64"
        val tarGz = file("${layout.buildDirectory.get()}/tmp/sonar-context-augmentation.tar.gz")
        tarGz.parentFile.mkdirs()

        val url = "https://binaries.sonarsource.com/Distribution/" +
            "sonar-context-augmentation-alpine-$arch/" +
            "sonar-context-augmentation-alpine-$arch-$cagVersion.tar.gz"
        println("Downloading CAG binary from: $url")

        URI(url).toURL().openStream().use { input ->
            tarGz.outputStream().use { output -> input.copyTo(output) }
        }

        outputFile.parentFile.mkdirs()
        exec {
            commandLine("tar", "-xzf", tarGz.absolutePath, "-C", outputFile.parentFile.absolutePath)
        }
        outputFile.setExecutable(true)
        tarGz.delete()
        println("CAG binary downloaded to: ${outputFile.absolutePath}")
    }
}

tasks.test {
    // Don't run ITs in regular test task
    enabled = false
}

// Custom task for integration tests
tasks.register<Test>("integrationTest") {
    description = "Runs integration tests for proxied MCP servers using Testcontainers"
    group = "verification"
    
    dependsOn("downloadCagBinary")

    // Check if we should use a downloaded JAR from environment variable
    val downloadedJarPath = System.getenv("DOWNLOADED_JAR_PATH")
    
    // Only build the JAR if we're not using a downloaded one
    if (downloadedJarPath.isNullOrEmpty()) {
        dependsOn(":jar")
    }
    
    useJUnitPlatform()
    
    // Pass the JAR path as a system property to the tests
    doFirst {
        val jarPath = if (!downloadedJarPath.isNullOrEmpty()) {
            println("Using downloaded JAR from: $downloadedJarPath")
            downloadedJarPath
        } else {
            val jarTask = project(":").tasks.named<Jar>("jar").get()
            val path = jarTask.archiveFile.get().asFile.absolutePath
            println("Using locally built JAR from: $path")
            path
        }
        systemProperty("sonarqube.mcp.jar.path", jarPath)
    }

    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}


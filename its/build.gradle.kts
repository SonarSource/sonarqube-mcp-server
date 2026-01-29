plugins {
    java
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

tasks.test {
    // Don't run ITs in regular test task
    enabled = false
}

// Custom task for integration tests
tasks.register<Test>("integrationTest") {
    description = "Runs integration tests for proxied MCP servers using Testcontainers"
    group = "verification"
    
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


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
    
    // Ensure main JAR is built before running integration tests
    dependsOn(":jar")
    
    useJUnitPlatform()
    
    // Pass the JAR path as a system property to the tests
    doFirst {
        val jarTask = project(":").tasks.named<Jar>("jar").get()
        systemProperty("sonarqube.mcp.jar.path", jarTask.archiveFile.get().asFile.absolutePath)
    }

    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}


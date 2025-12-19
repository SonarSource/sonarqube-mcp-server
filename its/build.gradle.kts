plugins {
    java
}

// The environment variables ARTIFACTORY_PRIVATE_USERNAME and ARTIFACTORY_PRIVATE_PASSWORD are used on CI env
// On local box, please add artifactoryUrl, artifactoryUsername and artifactoryPassword to ~/.gradle/gradle.properties
val artifactoryUrl = System.getenv("ARTIFACTORY_URL")
    ?: (if (project.hasProperty("artifactoryUrl")) project.property("artifactoryUrl").toString() else "")
val artifactoryUsername = System.getenv("ARTIFACTORY_PRIVATE_USERNAME")
    ?: (if (project.hasProperty("artifactoryUsername")) project.property("artifactoryUsername").toString() else "")
val artifactoryPassword = System.getenv("ARTIFACTORY_PRIVATE_PASSWORD")
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
    
    // MCP SDK - needed explicitly for test compilation
    testImplementation("io.modelcontextprotocol.sdk:mcp:0.16.0")
    
    // Testcontainers
    testImplementation("org.testcontainers:testcontainers:1.21.4")
    testImplementation("org.testcontainers:junit-jupiter:1.21.4")
    
    // Test frameworks
    testImplementation("org.assertj:assertj-core:3.24.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.slf4j:slf4j-api:2.0.9")
    testRuntimeOnly("ch.qos.logback:logback-classic:1.4.14")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    // Don't run ITs in regular test task
    enabled = false
}

// Custom task for integration tests
tasks.register<Test>("integrationTest") {
    description = "Runs integration tests for external tool providers using Testcontainers"
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


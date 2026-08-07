import java.net.URI

plugins {
    java
    alias(libs.plugins.license)
}

// This subproject contains only integration tests and should not contribute to the SBOM
tasks.named("cyclonedxDirectBom") { enabled = false }

license {
    header = rootProject.file("HEADER")
    mapping(mapOf("java" to "SLASHSTAR_STYLE"))
    excludes(listOf("**/*.json"))
    strictCheck = true
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

val artifactoryUrl = System.getenv("ARTIFACTORY_URL").orEmpty()
    .ifEmpty { project.findProperty("artifactoryUrl")?.toString().orEmpty() }
val artifactoryUsername = System.getenv("ARTIFACTORY_ACCESS_USERNAME").orEmpty()
    .ifEmpty { project.findProperty("artifactoryUsername")?.toString().orEmpty() }
val artifactoryPassword = System.getenv("ARTIFACTORY_ACCESS_TOKEN").orEmpty()
    .ifEmpty { project.findProperty("artifactoryPassword")?.toString().orEmpty() }

if (gradle.startParameter.isWriteDependencyLocks) {
    require(artifactoryUrl.isNotEmpty() && artifactoryUsername.isNotEmpty() && artifactoryPassword.isNotEmpty()) {
        "Dependency locks must be written using Repox (Artifactory) credentials to ensure consistent resolution.\n" +
            "Set artifactoryUrl, artifactoryUsername, and artifactoryPassword in ~/.gradle/gradle.properties or via environment variables."
    }
}

dependencyLocking {
    lockAllConfigurations()
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

configurations.all {
    resolutionStrategy.eachDependency {
        // Pulled in transitively by testcontainers:1.21.x
        if (requested.group == "org.apache.commons" && requested.name == "commons-compress") {
            useVersion("1.28.0")
            because("CVE-2024-25710 + CVE-2024-26308")
        }
        if (requested.group == "com.fasterxml.jackson.core" && requested.name != "jackson-annotations") {
            useVersion("2.21.1")
            because("GHSA-72hv-8253-57qq")
        }
        if (requested.group == "tools.jackson.core") {
            useVersion("3.1.2")
            because("CVE-2026-29062 + GHSA-72hv-8253-57qq")
        }
    }
}

val rootProjectTestOutput = project(":").sourceSets["test"].output

sourceSets {
    test {
        compileClasspath += rootProjectTestOutput
        runtimeClasspath += rootProjectTestOutput
    }
}

tasks.named<JavaCompile>("compileTestJava") {
    dependsOn(project(":").tasks.named("compileTestJava"))
}

dependencies {
    testImplementation(project(":"))
    testImplementation(libs.mcp.server)
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.jupiter)
    testImplementation(libs.assertj)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.awaitility)
    testImplementation(libs.commons.langs3)
    testRuntimeOnly(libs.junit.launcher)
}

val cagVersion = rootProject.property("sonarContextAugmentationVersion") as String
val cagArch = when (System.getProperty("os.arch")) {
    "aarch64", "arm64" -> "arm64"
    else -> "x64"
}

val downloadCagBinary = tasks.register("downloadCagBinary") {
    description = "Downloads the sonar-context-augmentation Alpine binary for integration tests"
    group = "verification"

    val outputDir = layout.buildDirectory.dir("cag-binary")
    outputs.dir(outputDir)
    inputs.property("version", cagVersion)
    inputs.property("arch", cagArch)

    doLast {
        val targetDir = outputDir.get().asFile.apply {
            deleteRecursively()
            mkdirs()
        }
        val tarGz = layout.buildDirectory.file("tmp/sonar-context-augmentation.tar.gz").get().asFile
        tarGz.parentFile.mkdirs()

        val url = "https://binaries.sonarsource.com/Distribution/" +
            "sonar-context-augmentation-linux-$cagArch/" +
            "sonar-context-augmentation-linux-$cagArch-$cagVersion.tar.gz"
        logger.lifecycle("Downloading CAG binary from: $url")

        URI(url).toURL().openStream().use { input ->
            tarGz.outputStream().use { output -> input.copyTo(output) }
        }

        val exitCode = ProcessBuilder("tar", "-xzf", tarGz.absolutePath, "-C", targetDir.absolutePath)
            .inheritIO()
            .start()
            .waitFor()
        check(exitCode == 0) { "tar extraction failed with exit code $exitCode" }
        tarGz.delete()

        File(targetDir, "sonar-context-augmentation").setExecutable(true)
        logger.lifecycle("CAG binary extracted to: ${targetDir.absolutePath}")
    }
}

val cagTestResources = layout.buildDirectory.dir("integration-test-resources")

val packageCagBinaryForIntegrationTest = tasks.register<Copy>("packageCagBinaryForIntegrationTest") {
    description = "Packages the CAG binary on the Docker integration test classpath"
    group = "verification"
    dependsOn(downloadCagBinary)
    from(layout.buildDirectory.dir("cag-binary")) {
        include("sonar-context-augmentation")
    }
    into(cagTestResources.map { it.dir("binaries") })
}

tasks.test {
    enabled = false
}

tasks.register<Test>("integrationTest") {
    description = "Runs Docker-based integration tests (Testcontainers)"
    group = "verification"

    dependsOn(packageCagBinaryForIntegrationTest)

    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath + files(cagTestResources)

    useJUnitPlatform {
        excludeTags("SonarCloud")
    }

    val downloadedJarPath = System.getenv("DOWNLOADED_JAR_PATH")
    if (downloadedJarPath.isNullOrEmpty()) {
        dependsOn(":jar")
    }

    doFirst {
        val jarPath = downloadedJarPath?.takeIf { it.isNotEmpty() } ?: run {
            project(":").tasks.named<Jar>("jar").get().archiveFile.get().asFile.absolutePath
        }
        logger.lifecycle("Using JAR: $jarPath")
        systemProperty("sonarqube.mcp.jar.path", jarPath)
    }

    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

tasks.register<Test>("sonarCloudIntegrationTest") {
    description = "Runs SonarQube Cloud staging integration tests (requires SONARCLOUD_IT_TOKEN and Maven)"
    group = "verification"

    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath

    useJUnitPlatform {
        includeTags("SonarCloud")
    }

    systemProperty("TELEMETRY_DISABLED", "true")
    systemProperty("mcp.client.timeout.seconds", "120")
    systemProperty("mcp.client.init.timeout.seconds", "120")

    doFirst {
        val token = System.getenv("SONARCLOUD_IT_TOKEN")
        if (token.isNullOrBlank()) {
            val skipWithoutToken = System.getenv("SONARCLOUD_IT_ON_MISSING_TOKEN") == "skip"
                || project.findProperty("sonarCloudIntegrationTest.skipWithoutToken") == "true"
            if (skipWithoutToken) {
                logger.warn(
                    "SONARCLOUD_IT_TOKEN not available (e.g. fork PR); skipping SonarQube Cloud staging integration tests"
                )
                throw org.gradle.api.tasks.StopExecutionException()
            }
            throw GradleException(
                "SONARCLOUD_IT_TOKEN must be set to run sonarCloudIntegrationTest against SonarQube Cloud staging " +
                    "(org: sonarlint-it). Export the token locally, then re-run this task."
            )
        }
    }

    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

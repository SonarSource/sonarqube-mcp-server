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

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencyLocking {
    lockAllConfigurations()
}

repositories {
    // CI sets ARTIFACTORY_*. For local builds, put artifactoryUrl / artifactoryUsername /
    // artifactoryPassword in ~/.gradle/gradle.properties; otherwise we fall back to Maven Central.
    val artifactoryUrl = System.getenv("ARTIFACTORY_URL").orEmpty()
        .ifEmpty { project.findProperty("artifactoryUrl")?.toString().orEmpty() }
    val artifactoryUsername = System.getenv("ARTIFACTORY_USER").orEmpty()
        .ifEmpty { project.findProperty("artifactoryUsername")?.toString().orEmpty() }
    val artifactoryPassword = System.getenv("ARTIFACTORY_ACCESS_TOKEN").orEmpty()
        .ifEmpty { project.findProperty("artifactoryPassword")?.toString().orEmpty() }

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
    }
}

dependencies {
    testImplementation(project(":"))
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.jupiter)
    testImplementation(libs.assertj)
    testImplementation(libs.junit.jupiter)
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
            "sonar-context-augmentation-alpine-$cagArch/" +
            "sonar-context-augmentation-alpine-$cagArch-$cagVersion.tar.gz"
        logger.lifecycle("Downloading CAG binary from: $url")

        URI(url).toURL().openStream().use { input ->
            tarGz.outputStream().use { output -> input.copyTo(output) }
        }

        exec { commandLine("tar", "-xzf", tarGz.absolutePath, "-C", targetDir.absolutePath) }
        tarGz.delete()

        File(targetDir, "sonar-context-augmentation").setExecutable(true)
        logger.lifecycle("CAG binary extracted to: ${targetDir.absolutePath}")
    }
}

tasks.named<ProcessResources>("processTestResources") {
    from(downloadCagBinary) {
        into("binaries")
    }
}

tasks.test {
    enabled = false
}

tasks.register<Test>("integrationTest") {
    description = "Runs integration tests for proxied MCP servers using Testcontainers"
    group = "verification"

    useJUnitPlatform()

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

pluginManagement {
    // This block is compiled earlier than the rest of the script, so keep it self-contained.
    val artifactoryUrl = System.getenv("ARTIFACTORY_URL") ?: providers.gradleProperty("artifactoryUrl").orNull
    val artifactoryUsername = System.getenv("ARTIFACTORY_ACCESS_USERNAME") ?: providers.gradleProperty("artifactoryUsername").orNull
    val artifactoryPassword = System.getenv("ARTIFACTORY_ACCESS_TOKEN") ?: providers.gradleProperty("artifactoryPassword").orNull
    repositories {
        maven {
            if (artifactoryUrl != null && artifactoryUsername != null && artifactoryPassword != null) {
                url = java.net.URI("$artifactoryUrl/plugins.gradle.org/")
                credentials { username = artifactoryUsername; password = artifactoryPassword }
            } else {
                url = java.net.URI("https://plugins.gradle.org/m2/")
            }
        }
    }
}

rootProject.name = "sonarqube-mcp-server"

include("its")

plugins {
    id("com.gradle.develocity") version "4.4.3"
    id("com.gradle.common-custom-user-data-gradle-plugin") version "2.6.0"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

val isCiServer = System.getenv("CI") != null

buildCache {
    local {
        isEnabled = !isCiServer
    }
    remote(develocity.buildCache) {
        isEnabled = true
        isPush = isCiServer
    }
}

develocity {
    server = "https://develocity.sonar.build"
    buildScan {
        publishing.onlyIf { isCiServer && it.isAuthenticated }
        capture {
            buildLogging.set(!startParameter.taskNames.contains("properties"))
        }
    }
}

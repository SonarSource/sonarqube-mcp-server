rootProject.name = "sonarqube-mcp-server"

include("its")

plugins {
    id("com.gradle.develocity") version "4.4.1"
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

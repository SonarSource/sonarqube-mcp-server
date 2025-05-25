rootProject.name = "sonar-mcp-server"

plugins {
    id("com.gradle.develocity") version "3.18.2"
    id("com.gradle.common-custom-user-data-gradle-plugin") version "2.2.1"
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
        publishing.onlyIf { false }
    }
}

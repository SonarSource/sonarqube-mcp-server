import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.util.zip.ZipFile
import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream

plugins {
	application
	jacoco
	signing
	alias(libs.plugins.sonarqube)
	alias(libs.plugins.license)
	alias(libs.plugins.artifactory)
	alias(libs.plugins.cyclonedx)
}

group = "org.sonarsource.sonarqube.mcp.server"

val omnisharpVersion: String by project

// The environment variables ARTIFACTORY_PRIVATE_USERNAME and ARTIFACTORY_PRIVATE_PASSWORD are used on CI env
// On local box, please add artifactoryUsername and artifactoryPassword to ~/.gradle/gradle.properties
val artifactoryUsername = System.getenv("ARTIFACTORY_PRIVATE_USERNAME")
	?: (if (project.hasProperty("artifactoryUsername")) project.property("artifactoryUsername").toString() else "")
val artifactoryPassword = System.getenv("ARTIFACTORY_PRIVATE_PASSWORD")
	?: (if (project.hasProperty("artifactoryPassword")) project.property("artifactoryPassword").toString() else "")

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	maven("https://repox.jfrog.io/repox/sonarsource") {
		if (artifactoryUsername.isNotEmpty() && artifactoryPassword.isNotEmpty()) {
			credentials {
				username = artifactoryUsername
				password = artifactoryPassword
			}
		}
	}
	mavenCentral {
		content {
			// avoid dependency confusion
			excludeGroupByRegex("com\\.sonarsource.*")
		}
	}
}

license {
	header = rootProject.file("HEADER")
	mapping(
		mapOf(
			"java" to "SLASHSTAR_STYLE",
			"kt" to "SLASHSTAR_STYLE",
			"svg" to "XML_STYLE",
			"form" to "XML_STYLE"
		)
	)
	excludes(
		listOf("**/*.jar", "**/*.png", "**/README", "**/logback.xml")
	)
	strictCheck = true
}

val mockitoAgent = configurations.create("mockitoAgent")

dependencies {
	implementation(libs.mcp.server)
	implementation(libs.sonarlint.java.client.utils)
	implementation(libs.sonarlint.rpc.java.client)
	implementation(libs.sonarlint.rpc.impl)
	implementation(libs.commons.langs3)
	implementation(libs.commons.text)
	implementation(libs.sslcontext.kickstart)
	runtimeOnly(libs.logback.classic)
	testImplementation(platform(libs.junit.bom))
	testImplementation(libs.junit.jupiter)
	testImplementation(libs.mockito.core)
	testImplementation(libs.assertj)
	testImplementation(libs.awaitility)
	testImplementation(libs.wiremock)
	testRuntimeOnly(libs.junit.launcher)
	mockitoAgent(libs.mockito.core) { isTransitive = false }
}

tasks {
	test {
		useJUnitPlatform()
		systemProperty("TELEMETRY_DISABLED", "true")
		systemProperty("sonarqube.mcp.server.version", project.version)
		doNotTrackState("Tests should always run")
		jvmArgs("-javaagent:${mockitoAgent.asPath}")
	}

	jar {
		manifest {
			attributes["Main-Class"] = "org.sonarsource.sonarqube.mcp.SonarQubeMcpServer"
			attributes["Implementation-Version"] = project.version
		}

		from({
			configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }
		}) {
			exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA",
				// module-info comes from sslcontext-kickstart and is looking for slf4j
				"META-INF/versions/**/module-info.class", "module-info.class")
		}

		duplicatesStrategy = DuplicatesStrategy.EXCLUDE
	}

	jacocoTestReport {
		reports {
			xml.required.set(true)
		}
	}

	register<Exec>("buildDocker") {
		val appVersion = project.version.toString()
		val appName = project.name
		group = "docker"
		description = "Builds the Docker image with the current project version"

		commandLine("docker", "build", "-t", "$appName:$appVersion", "--build-arg", "APP_VERSION=$appVersion", ".")
	}
}

application {
	mainClass = "org.sonarsource.sonarqube.mcp.SonarQubeMcpServer"
}

artifactory {
	clientConfig.info.buildName = "sonarqube-mcp-server"
	clientConfig.info.buildNumber = System.getenv("BUILD_ID")
	clientConfig.isIncludeEnvVars = true
	clientConfig.envVarsExcludePatterns = "*password*,*PASSWORD*,*secret*,*MAVEN_CMD_LINE_ARGS*,sun.java.command,*token*,*TOKEN*,*LOGIN*,*login*,*key*,*KEY*,*PASSPHRASE*,*signing*"
	clientConfig.info.addEnvironmentProperty(
		"ARTIFACTS_TO_DOWNLOAD",
		"org.sonarsource.sonarqube.mcp.server:sonarqube-mcp-server:jar,org.sonarsource.sonarqube.mcp.server:sonarqube-mcp-server:json:cyclonedx"
	)
	setContextUrl(System.getenv("ARTIFACTORY_URL"))
	publish {
		repository {
			setProperty("repoKey", System.getenv("ARTIFACTORY_DEPLOY_REPO"))
			setProperty("username", System.getenv("ARTIFACTORY_DEPLOY_USERNAME"))
			setProperty("password", System.getenv("ARTIFACTORY_DEPLOY_PASSWORD"))
		}
		defaults {
			setProperties(
				mapOf(
					"vcs.revision" to System.getenv("CIRRUS_CHANGE_IN_REPO"),
					"vcs.branch" to (System.getenv("CIRRUS_BASE_BRANCH")
						?: System.getenv("CIRRUS_BRANCH")),
					"build.name" to "sonarqube-mcp-server",
					"build.number" to System.getenv("BUILD_ID")
				)
			)
			setPublishPom(true)
			setPublishIvy(false)
		}
	}
}

sonar {
	properties {
		property("sonar.organization", "sonarsource")
		property("sonar.projectKey", "SonarSource_sonar-mcp-server")
		property("sonar.projectName", "SonarQube MCP Server")
		property("sonar.links.ci", "https://cirrus-ci.com/github/SonarSource/sonarqube-mcp-server")
		property("sonar.links.scm", "https://github.com/SonarSource/sonarqube-mcp-server")
		property("sonar.links.issue", "https://jira.sonarsource.com/browse/MCP")
		property("sonar.exclusions", "**/build/**/*")
	}
}

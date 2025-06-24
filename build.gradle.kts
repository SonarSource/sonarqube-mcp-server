import java.nio.file.Files
import java.nio.file.Paths
import java.util.zip.ZipFile
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream

plugins {
	application
	jacoco
	`maven-publish`
	signing
	alias(libs.plugins.sonarqube)
	alias(libs.plugins.license)
	alias(libs.plugins.artifactory)
	alias(libs.plugins.cyclonedx)
}

group = "org.sonarsource.sonarqube.mcp.server"

val pluginName = "sonarqube-mcp-server"
val mainClassName = "org.sonarsource.sonarqube.mcp.SonarQubeMcpServer"

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

configurations {
	val sqplugins = create("sqplugins") { isTransitive = false }
	create("sqplugins_deps") {
		extendsFrom(sqplugins)
		isTransitive = true
	}
}

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
	"sqplugins"(libs.bundles.sonar.analyzers)
	mockitoAgent(libs.mockito.core) { isTransitive = false }
}

tasks {
	test {
		useJUnitPlatform()
		systemProperty("TELEMETRY_DISABLED", "true")
		systemProperty("sonarqube.mcp.server.version", project.version)
		doNotTrackState("Tests should always run")
		maxHeapSize = "2g"
		jvmArgs("-javaagent:${mockitoAgent.asPath}", "-XX:MaxMetaspaceSize=512m")
		dependsOn("prepareTestPlugins")
	}

	jar {
		manifest {
			attributes["Main-Class"] = mainClassName
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

	register("prepareTestPlugins") {
		val destinationDir = file(layout.buildDirectory)
		description = "Prepare SonarQube test plugins"
		group = "build"
		
		// Incremental build support
		inputs.files(configurations["sqplugins"])
		outputs.dir("$destinationDir/$pluginName/plugins")

		doLast {
			copyTestPlugins(destinationDir, pluginName)
		}
	}
}

fun copyTestPlugins(destinationDir: File, pluginName: String) {
	copy {
		from(project.configurations["sqplugins"])
		into(file("$destinationDir/$pluginName/plugins"))
	}
}

application {
	mainClass = mainClassName
}

artifactory {
	clientConfig.info.buildName = "sonarqube-mcp-server"
	clientConfig.info.buildNumber = System.getenv("BUILD_NUMBER")
	clientConfig.isIncludeEnvVars = true
	clientConfig.envVarsExcludePatterns = "*password*,*PASSWORD*,*secret*,*MAVEN_CMD_LINE_ARGS*,sun.java.command,*token*,*TOKEN*,*LOGIN*,*login*,*key*,*KEY*,*PASSPHRASE*,*signing*"
	clientConfig.info.addEnvironmentProperty("PROJECT_VERSION", version.toString())
	clientConfig.info.addEnvironmentProperty("ARTIFACTS_TO_DOWNLOAD", "")
	setContextUrl(System.getenv("ARTIFACTORY_URL"))
	publish {
		repository {
			repoKey = System.getenv("ARTIFACTORY_DEPLOY_REPO")
			username = System.getenv("ARTIFACTORY_DEPLOY_USERNAME")
			password = System.getenv("ARTIFACTORY_DEPLOY_PASSWORD")
		}
		defaults {
			publications("mavenJava")
			setProperties(
				mapOf(
					"vcs.revision" to System.getenv("CIRRUS_CHANGE_IN_REPO"),
					"vcs.branch" to (System.getenv("CIRRUS_BASE_BRANCH")
						?: System.getenv("CIRRUS_BRANCH")),
					"build.name" to "sonarqube-mcp-server",
					"build.number" to System.getenv("BUILD_NUMBER")
				)
			)
			setPublishPom(true)
			setPublishIvy(false)
		}
	}
}

publishing {
	publications {
		create<MavenPublication>("mavenJava") {
			from(components["java"])
			pom {
				name.set("sonarqube-mcp-server")
				description.set(project.description)
				url.set("https://www.sonarqube.org/")
				organization {
					name.set("SonarSource")
					url.set("https://www.sonarqube.org/")
				}
				licenses {
					license {
						name.set("SSALv1")
						url.set("https://sonarsource.com/license/ssal/")
						distribution.set("repo")
					}
				}
				scm {
					url.set("https://github.com/SonarSource/sonarqube-mcp-server")
				}
				developers {
					developer {
						id.set("sonarsource-team")
						name.set("SonarSource Team")
					}
				}
			}
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

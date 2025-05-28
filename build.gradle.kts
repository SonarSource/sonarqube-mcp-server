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
	`maven-publish`
	signing
	alias(libs.plugins.sonarqube)
	alias(libs.plugins.license)
	alias(libs.plugins.artifactory)
	alias(libs.plugins.cyclonedx)
}

group = "org.sonarsource.sonarqube.mcp.server"

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

	register("preparePlugins") {
		val destinationDir = file(layout.buildDirectory)
		description = "Prepare SonarQube plugins"
		group = "build"

		doLast {
			val pluginName = "sonarqube-mcp-server"
			copyPlugins(destinationDir, pluginName)
			unzipEslintBridgeBundle(destinationDir, pluginName)
		}
	}
}

fun copyPlugins(destinationDir: File, pluginName: String) {
	copy {
		from(project.configurations["sqplugins"])
		into(file("$destinationDir/$pluginName/plugins"))
	}
}

fun unzipEslintBridgeBundle(destinationDir: File, pluginName: String) {
	val pluginsDir = File("$destinationDir/$pluginName/plugins")
	val jarPath = pluginsDir.listFiles()?.find {
		it.name.startsWith("sonar-javascript-plugin-") && it.name.endsWith(".jar")
	} ?: throw GradleException("sonar-javascript-plugin-* JAR not found in $destinationDir")

	val zipFile = ZipFile(jarPath)
	val entry = zipFile.entries().asSequence().find { it.name.matches(Regex("sonarjs-.*\\.tgz")) }
		?: throw GradleException("eslint bridge server bundle not found in JAR $jarPath")


	val outputFolderPath = Paths.get("$pluginsDir/eslint-bridge")
	val outputFilePath = outputFolderPath.resolve(entry.name)

	if (!Files.exists(outputFolderPath)) {
		Files.createDirectory(outputFolderPath)
	}

	zipFile.getInputStream(entry).use { input ->
		FileOutputStream(outputFilePath.toFile()).use { output ->
			input.copyTo(output)
		}
	}

	GzipCompressorInputStream(FileInputStream(outputFilePath.toFile())).use { gzipInput ->
		TarArchiveInputStream(gzipInput).use { tarInput ->
			var tarEntry: ArchiveEntry?
			while (tarInput.nextEntry.also { tarEntry = it } != null) {
				val outputFile = outputFolderPath.resolve(tarEntry!!.name).toFile()
				if (tarEntry!!.isDirectory) {
					outputFile.mkdirs()
				} else {
					outputFile.parentFile.mkdirs()
					FileOutputStream(outputFile).use { output ->
						tarInput.copyTo(output)
					}
				}
			}
		}
	}

	Files.delete(outputFilePath)
}

application {
	mainClass = "org.sonarsource.sonarqube.mcp.SonarQubeMcpServer"
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

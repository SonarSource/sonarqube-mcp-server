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

group = "org.sonarsource.sonar.mcp.server"

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
		listOf("**/*.jar", "**/*.png", "**/README")
	)
	strictCheck = true
}

configurations {
	val sqplugins = create("sqplugins") { isTransitive = false }
	create("sqplugins_deps") {
		extendsFrom(sqplugins)
		isTransitive = true
	}
	create("omnisharp")
}

dependencies {
	implementation(libs.mcp.server)
	implementation(libs.sonarlint.java.client.utils)
	implementation(libs.sonarlint.rpc.java.client)
	implementation(libs.sonarlint.rpc.impl)
	implementation(libs.commons.langs3)
	implementation(libs.commons.text)
	implementation(libs.sslcontext.kickstart)
	testImplementation(platform(libs.junit.bom))
	testImplementation(libs.junit.jupiter)
	testImplementation(libs.mockito.core)
	testImplementation(libs.assertj)
	testImplementation(libs.awaitility)
	testImplementation(libs.wiremock)
	testRuntimeOnly(libs.junit.launcher)
	"sqplugins"(libs.bundles.sonar.analyzers)
	if (artifactoryUsername.isNotEmpty() && artifactoryPassword.isNotEmpty()) {
		"sqplugins"(libs.sonar.cfamily)
		"sqplugins"(libs.sonar.dotnet.enterprise)
		"omnisharp"("org.sonarsource.sonarlint.omnisharp:omnisharp-roslyn:$omnisharpVersion:mono@zip")
		"omnisharp"("org.sonarsource.sonarlint.omnisharp:omnisharp-roslyn:$omnisharpVersion:net472@zip")
		"omnisharp"("org.sonarsource.sonarlint.omnisharp:omnisharp-roslyn:$omnisharpVersion:net6@zip")
	}
}

fun copyPlugins(destinationDir: File, pluginName: String) {
	copy {
		from(project.configurations["sqplugins"])
		into(file("$destinationDir/$pluginName/plugins"))
	}
}

fun renameCsharpPlugins(destinationDir: File, pluginName: String) {
	val pluginsDir = File("$destinationDir/$pluginName/plugins")
	pluginsDir.listFiles()?.forEach { file ->
		if (file.name.matches(Regex("sonar-csharp-enterprise-plugin-.*\\.jar"))) {
			file.renameTo(File(pluginsDir, "sonar-csharp-enterprise-plugin.jar"))
		} else if (file.name.matches(Regex("sonar-csharp-plugin-.*\\.jar"))) {
			file.renameTo(File(pluginsDir, "sonar-csharp-plugin.jar"))
		}
	}
}

fun copyOmnisharp(destinationDir: File, pluginName: String) {
	configurations["omnisharp"].resolvedConfiguration.resolvedArtifacts.forEach { artifact ->
		copy {
			from(zipTree(artifact.file))
			into(file("$destinationDir/$pluginName/omnisharp/${artifact.classifier}"))
		}
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

tasks {
	test {
		dependsOn("preparePlugins")
		useJUnitPlatform()
		systemProperty("TELEMETRY_DISABLED", "true")
		systemProperty("sonar.mcp.server.version", project.version)
		doNotTrackState("Tests should always run")
	}

	register("preparePlugins") {
		val destinationDir = file(layout.buildDirectory)
		description = "Prepare Sonar plugins"
		group = "build"

		doLast {
			val pluginName = "sonar-mcp-server"
			copyPlugins(destinationDir, pluginName)
			renameCsharpPlugins(destinationDir, pluginName)
			copyOmnisharp(destinationDir, pluginName)
			unzipEslintBridgeBundle(destinationDir, pluginName)
		}
	}

	register<Copy>("copyPluginResources") {
		dependsOn("preparePlugins")
		description = "Copy Sonar plugins"
		group = "build"

		val pluginName = "sonar-mcp-server"
		val fromDir = layout.buildDirectory.dir(pluginName)

		from(fromDir) {
			include("**/plugins/**", "**/omnisharp/**")
			eachFile {
				path = path.removePrefix("$pluginName/")
			}
		}

		into("$buildDir/generated-resources/plugins")
	}

	jar {
		manifest {
			attributes["Main-Class"] = "org.sonar.mcp.SonarMcpServer"
			attributes["Implementation-Version"] = project.version
		}

		from({
			configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }
		}) {
			exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
		}

		duplicatesStrategy = DuplicatesStrategy.EXCLUDE
	}

	jacocoTestReport {
		reports {
			xml.required.set(true)
		}
	}
}

application {
	mainClass = "org.sonar.mcp.SonarMcpServer"
}

artifactory {
	clientConfig.info.buildName = "sonar-mcp-server"
	clientConfig.info.buildNumber = System.getenv("BUILD_ID")
	clientConfig.isIncludeEnvVars = true
	clientConfig.envVarsExcludePatterns = "*password*,*PASSWORD*,*secret*,*MAVEN_CMD_LINE_ARGS*,sun.java.command,*token*,*TOKEN*,*LOGIN*,*login*,*key*,*KEY*,*PASSPHRASE*,*signing*"
	clientConfig.info.addEnvironmentProperty(
		"ARTIFACTS_TO_DOWNLOAD",
		"org.sonarsource.sonar.mcp.server:sonar-mcp-server:jar,org.sonarsource.sonar.mcp.server:sonar-mcp-server:json:cyclonedx"
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
					"build.name" to "sonar-mcp-server",
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
		property("sonar.projectName", "Sonar MCP Server")
		property("sonar.links.ci", "https://cirrus-ci.com/github/SonarSource/sonar-mcp-server")
		property("sonar.links.scm", "https://github.com/SonarSource/sonar-mcp-server")
		property("sonar.links.issue", "https://jira.sonarsource.com/browse/SLCORE")
		property("sonar.exclusions", "**/build/**/*")
	}
}

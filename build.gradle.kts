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

// The environment variables ARTIFACTORY_USER and ARTIFACTORY_ACCESS_TOKEN are used on CI env
// On local box, please add artifactoryUrl, artifactoryUsername and artifactoryPassword to ~/.gradle/gradle.properties
val artifactoryUrl = System.getenv("ARTIFACTORY_URL")
	?: (if (project.hasProperty("artifactoryUrl")) project.property("artifactoryUrl").toString() else "")
val artifactoryUsername = System.getenv("ARTIFACTORY_USER")
	?: (if (project.hasProperty("artifactoryUsername")) project.property("artifactoryUsername").toString() else "")
val artifactoryPassword = System.getenv("ARTIFACTORY_ACCESS_TOKEN")
	?: (if (project.hasProperty("artifactoryPassword")) project.property("artifactoryPassword").toString() else "")

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

// Locks resolved dependency versions to gradle.lockfile (required by S8569).
// To regenerate after adding or updating dependencies:
//   ./gradlew :dependencies --write-locks
//   ./gradlew :its:dependencies --write-locks
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
        listOf("**/*.jar", "**/*.png", "**/README", "**/logback.xml", "**/proxied-mcp-servers.json")
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
	all {
		resolutionStrategy.eachDependency {
			// Pulled in by xodus-entity-store:2.0.1
			if (requested.group == "org.jetbrains.kotlin" && requested.name in listOf("kotlin-stdlib", "kotlin-stdlib-common")) {
				useVersion("2.2.0")
				because("CVE-2020-29582")
			}
			// Pulled in transitively by mcp-json-jackson2 and sonarlint-rpc-impl
            if (requested.group == "com.fasterxml.jackson.core" && requested.name != "jackson-annotations") {
                useVersion("2.21.1")
				because("GHSA-72hv-8253-57qq")
			}
			// Pulled in by mcp-json-jackson3
			if (requested.group == "tools.jackson.core") {
				useVersion("3.1.2")
				because("CVE-2026-29062 + GHSA-72hv-8253-57qq")
			}
			// Pulled in transitively by sonarlint-core
			if (requested.group == "org.apache.commons" && requested.name == "commons-compress") {
				useVersion("1.28.0")
				because("CVE-2024-25710 + CVE-2024-26308")
			}
		}
	}
}

dependencies {
	implementation(libs.mcp.server)
	implementation(libs.sonarlint.java.client.utils)
	implementation(libs.sonarlint.rpc.java.client)
	implementation(libs.sonarlint.rpc.impl)
	implementation(libs.commons.langs3)
	implementation(libs.commons.text)
	implementation(libs.ayza)
	implementation(libs.jetty.server)
	implementation(libs.jetty.ee10.servlet)
	implementation(libs.jsonschema.generator)
	implementation(libs.jsonschema.module.jackson)
	compileOnly(libs.jsr305)
	runtimeOnly(libs.logback.classic)
	testImplementation(libs.logback.classic)
	testImplementation(platform(libs.junit.bom))
	testImplementation(libs.junit.jupiter)
	testImplementation(libs.mockito.core)
	testImplementation(libs.assertj)
	testImplementation(libs.awaitility)
	testImplementation(libs.wiremock.jetty12)
	testRuntimeOnly(libs.junit.launcher)
	"sqplugins"(libs.bundles.sonar.analyzers)
	mockitoAgent(libs.mockito.core) { isTransitive = false }
}

tasks {
	test {
		useJUnitPlatform()
		systemProperty("TELEMETRY_DISABLED", "true")
		systemProperty("sonarqube.mcp.server.version", project.version)
		// Reduce MCP client timeout for tests to speed up failure scenarios
		systemProperty("mcp.client.timeout.seconds", "2")
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
			exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/maven/**",
				// module-info comes from sslcontext-kickstart and is looking for slf4j
				"META-INF/versions/**/module-info.class", "module-info.class")
		}

		duplicatesStrategy = DuplicatesStrategy.EXCLUDE
	}

	jacocoTestReport {
		reports {
			xml.required.set(true)
		}
		classDirectories.setFrom(
			files(classDirectories.files.map {
				fileTree(it) {
					exclude(
						"**/ManagedStdioClientTransport.class",
						"**/StdioServerTransportProvider.class"
					)
				}
			})
		)
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
	clientConfig.info.addEnvironmentProperty("ARTIFACTS_TO_PUBLISH", "org.sonarsource.sonarqube.mcp.server:sonarqube-mcp-server:jar")
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
					"vcs.revision" to System.getenv("GITHUB_SHA"),
					"vcs.branch" to (System.getenv("GITHUB_BASE_REF")
						?: System.getenv("GITHUB_HEAD_REF") ?: System.getenv("GITHUB_REF_NAME")),
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
		property("sonar.projectKey", "SonarSource_sonarqube-mcp-server")
		property("sonar.projectName", "SonarQube MCP Server")
		property("sonar.links.ci", "https://github.com/SonarSource/sonarqube-mcp-server/actions")
		property("sonar.links.scm", "https://github.com/SonarSource/sonarqube-mcp-server")
		property("sonar.links.issue", "https://jira.sonarsource.com/browse/MCP")
		property("sonar.exclusions", "**/build/**/*")
		property("sonar.coverage.exclusions", "**/ManagedStdioClientTransport.java,**/StdioServerTransportProvider.java")
	}
}

signing {
    setRequired {
        val branch = System.getenv("GITHUB_REF_NAME") ?: ""
        val pr = System.getenv("GITHUB_HEAD_REF") ?: ""
        (branch == "master" || branch.matches("branch-[\\d.]+".toRegex())) &&
            pr == "" &&
            gradle.taskGraph.hasTask(":artifactoryPublish")
    }
    val signingKeyId: String? by project
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword)
    sign(publishing.publications["mavenJava"])
}

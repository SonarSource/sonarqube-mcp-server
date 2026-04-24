/*
 * Defines the tasks that assemble the npx-distributable npm packages:
 *   - Downloads Temurin JDKs and fetches `sonar-context-augmentation` per target
 *   - Runs jdeps once against the fat JAR to determine the required JDK modules
 *   - Runs jlink to produce a minimal per-platform JRE
 *   - Stages the platform package directories (jre/, jar, bin/cag)
 *   - Stages the meta package directory with the JS launcher
 *
 * Actual `npm pack` / `npm publish` happens in CI (or can be invoked manually
 * on a machine that has Node.js installed).
 */

import java.io.ByteArrayOutputStream
import java.net.URI
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.jvm.toolchain.JavaToolchainService

data class NpmTarget(
    val npmOs: String,
    val npmCpu: String,
    val adoptiumOs: String,
    val adoptiumArch: String,
    /**
     * `sonar-context-augmentation` variant suffix, e.g. `alpine-x64`, or null when SonarSource
     * does not publish a binary for this platform (macOS, Windows as of 0.7.0.70). When null
     * the platform package is shipped without a CAG binary; the JS launcher will not set
     * `SONAR_CAG_BIN`, `ProxiedToolsLoader` will log a warning and skip the `sonar-cag` server,
     * and tools in the `cag` toolset are simply not registered on that platform.
     */
    val cagVariant: String?,
) {
    val id: String get() = "$npmOs-$npmCpu"
    val isWindows: Boolean get() = adoptiumOs == "windows"
    val archiveExtension: String get() = if (isWindows) "zip" else "tar.gz"
    val titleCase: String
        get() = npmOs.replaceFirstChar { it.uppercaseChar() } +
            npmCpu.replaceFirstChar { it.uppercaseChar() }
}

// `sonar-context-augmentation` is currently published only as `alpine-x64` / `alpine-arm64`
// on binaries.sonarsource.com (as of 0.7.0.70). We reuse these Alpine builds for the generic
// `linux-x64` / `linux-arm64` npm packages; they are expected to work on glibc distros when
// statically linked against musl, but this needs to be validated end-to-end before the first
// public release. macOS and Windows targets ship without a CAG binary - on those platforms
// `SONAR_CAG_BIN` is not set, the `${SONAR_CAG_BIN:-sonar-context-augmentation}` fallback in
// `proxied-mcp-servers.json` fails to resolve on PATH, and `ProxiedToolsLoader` skips the
// `sonar-cag` server (its tools simply do not get registered).
val npmTargets = listOf(
    NpmTarget("linux",  "x64",   "linux",   "x64",     "alpine-x64"),
    NpmTarget("linux",  "arm64", "linux",   "aarch64", "alpine-arm64"),
    NpmTarget("darwin", "x64",   "mac",     "x64",     null),
    NpmTarget("darwin", "arm64", "mac",     "aarch64", null),
    NpmTarget("win32",  "x64",   "windows", "x64",     null),
    NpmTarget("win32",  "arm64", "windows", "aarch64", null),
)

// Configurable versions (can be overridden via -P flags on the gradle command line)
val temurinJdkVersion = (findProperty("temurinJdkVersion") as String?) ?: "21.0.5+11"
val temurinJdkUrlVersion = temurinJdkVersion.replace("+", "%2B")
val temurinJdkFileVersion = temurinJdkVersion.replace("+", "_")
val cagVersion: String = (findProperty("sonarContextAugmentationVersion") as String?)
    ?: error("sonarContextAugmentationVersion must be defined in gradle.properties")

fun toNpmSemver(gradleVersion: String): String {
    val isSnapshot = gradleVersion.endsWith("-SNAPSHOT")
    val base = gradleVersion.removeSuffix("-SNAPSHOT")
    val parts = base.split(".").toMutableList()
    while (parts.size < 3) parts += "0"
    val normalized = parts.take(3).joinToString(".")
    return if (isSnapshot) {
        "$normalized-SNAPSHOT.${System.currentTimeMillis() / 1000}"
    } else {
        normalized
    }
}

val npmPackageVersion: String = toNpmSemver(project.version.toString())

val jdksDir = layout.buildDirectory.dir("npm-jdks")
val cagDir = layout.buildDirectory.dir("npm-cag")
val npmStageDir = layout.buildDirectory.dir("npm-stage")
val npmTarballDir = layout.buildDirectory.dir("npm-tarballs")

val modulesFile = layout.buildDirectory.file("npm-jdeps/modules.txt")

fun temurinDownloadUrl(target: NpmTarget): String {
    // Example:
    //   https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.5%2B11/
    //     OpenJDK21U-jdk_x64_linux_hotspot_21.0.5_11.tar.gz
    val majorVersion = temurinJdkVersion.substringBefore(".")
    val archiveName =
        "OpenJDK${majorVersion}U-jdk_${target.adoptiumArch}_${target.adoptiumOs}_hotspot_${temurinJdkFileVersion}.${target.archiveExtension}"
    return "https://github.com/adoptium/temurin${majorVersion}-binaries/releases/download/" +
        "jdk-${temurinJdkUrlVersion}/$archiveName"
}

fun cagDownloadUrl(target: NpmTarget): String {
    val variant = requireNotNull(target.cagVariant) {
        "No sonar-context-augmentation variant published for ${target.id}"
    }
    return "https://binaries.sonarsource.com/Distribution/sonar-context-augmentation-$variant/" +
        "sonar-context-augmentation-$variant-$cagVersion.tar.gz"
}

// ---------------------------------------------------------------------------
// jdeps: compute required JDK modules from the fat JAR (platform-independent)
// ---------------------------------------------------------------------------

// Resolve the host toolchain once, at configuration time, in the project scope.
// Inside a `doLast { }` block the implicit receiver is the Task (whose extensions
// container only exposes ExtraPropertiesExtension), so we must not call
// `extensions.getByType(JavaPluginExtension::class.java)` from there.
val hostJavaLauncher: Provider<JavaLauncher> = run {
    val javaExtension = project.extensions.getByType(JavaPluginExtension::class.java)
    val toolchainService = project.extensions.getByType(JavaToolchainService::class.java)
    toolchainService.launcherFor(javaExtension.toolchain)
}

val computeJdkModules by tasks.registering {
    description = "Run jdeps against the fat JAR to compute required JDK modules for jlink."
    group = "npm"

    val fatJar = tasks.named<Jar>("jar")
    dependsOn(fatJar)

    inputs.files(fatJar)
    outputs.file(modulesFile)

    val launcherProvider = hostJavaLauncher

    doLast {
        val launcher = launcherProvider.get()
        val jdepsExecutable = launcher.metadata.installationPath.file("bin/jdeps").asFile
        val jarFile = fatJar.get().archiveFile.get().asFile

        val output = ByteArrayOutputStream()
        val result = exec {
            commandLine(
                jdepsExecutable.absolutePath,
                "--ignore-missing-deps",
                "-q",
                "--recursive",
                "--multi-release", "21",
                "--print-module-deps",
                jarFile.absolutePath,
            )
            standardOutput = output
            isIgnoreExitValue = false
        }
        result.assertNormalExitValue()
        val modules = output.toString(Charsets.UTF_8).trim()
        require(modules.isNotEmpty()) { "jdeps produced no modules for $jarFile" }
        val target = modulesFile.get().asFile
        target.parentFile.mkdirs()
        target.writeText(modules)
    }
}

// ---------------------------------------------------------------------------
// Per-target tasks
// ---------------------------------------------------------------------------

npmTargets.forEach { target ->
    val targetJdkArchive = jdksDir.map { it.file("temurin-${target.id}.${target.archiveExtension}") }
    val targetJdkDir = jdksDir.map { it.dir(target.id) }

    val downloadJdkTask = tasks.register("downloadJdk${target.titleCase}") {
        description = "Download Temurin ${temurinJdkVersion} JDK for ${target.id}."
        group = "npm"

        val url = temurinDownloadUrl(target)
        inputs.property("url", url)
        outputs.file(targetJdkArchive)

        doLast {
            val dest = targetJdkArchive.get().asFile
            dest.parentFile.mkdirs()
            logger.lifecycle("Downloading Temurin JDK for ${target.id} from $url")
            URI(url).toURL().openStream().use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }

    val extractJdkTask = tasks.register<Copy>("extractJdk${target.titleCase}") {
        description = "Extract Temurin JDK archive for ${target.id}."
        group = "npm"
        dependsOn(downloadJdkTask)

        from(
            if (target.isWindows) {
                zipTree(targetJdkArchive)
            } else {
                tarTree(resources.gzip(targetJdkArchive))
            }
        )
        into(targetJdkDir)
    }

    // On macOS archives, the real JDK sits under Contents/Home. Other platforms have it at the root.
    fun jmodsDir(): File {
        val base = targetJdkDir.get().asFile
        val roots = base.listFiles()?.filter { it.isDirectory && it.name.startsWith("jdk") }.orEmpty()
        val root = roots.firstOrNull() ?: base
        val macHome = File(root, "Contents/Home/jmods")
        return if (macHome.isDirectory) macHome else File(root, "jmods")
    }

    val jreOutputDir = npmStageDir.map {
        it.dir("sonarqube-mcp-server-${target.id}/jre")
    }

    val jlinkTask = tasks.register("jlinkRuntime${target.titleCase}") {
        description = "Produce a jlink-stripped JRE for ${target.id}."
        group = "npm"
        dependsOn(extractJdkTask, computeJdkModules)

        inputs.dir(targetJdkDir)
        inputs.file(modulesFile)
        outputs.dir(jreOutputDir)

        val launcherProvider = hostJavaLauncher

        doLast {
            val outDir = jreOutputDir.get().asFile
            if (outDir.exists()) outDir.deleteRecursively()
            outDir.parentFile.mkdirs()

            val modules = modulesFile.get().asFile.readText().trim()
            val launcher = launcherProvider.get()
            val jlinkExecutable = launcher.metadata.installationPath.file("bin/jlink").asFile
            val jmods = jmodsDir()
            require(jmods.isDirectory) { "jmods directory not found for ${target.id} at $jmods" }

            val result = exec {
                commandLine(
                    jlinkExecutable.absolutePath,
                    "--module-path", jmods.absolutePath,
                    "--add-modules", "$modules,jdk.crypto.cryptoki,jdk.crypto.ec",
                    "--strip-debug",
                    "--no-man-pages",
                    "--no-header-files",
                    "--compress=2",
                    "--output", outDir.absolutePath,
                )
                isIgnoreExitValue = false
            }
            result.assertNormalExitValue()
        }
    }

    // -----------------------------------------------------------------------
    // sonar-context-augmentation download + extraction (Linux only today)
    // -----------------------------------------------------------------------

    val cagArchive = cagDir.map { it.file("sonar-context-augmentation-${target.id}.tar.gz") }
    val cagExtractDir = cagDir.map { it.dir(target.id) }

    val extractCagTask: TaskProvider<out Task>? = if (target.cagVariant != null) {
        val downloadCagTask = tasks.register("downloadCag${target.titleCase}") {
            description = "Download sonar-context-augmentation ${cagVersion} for ${target.id}."
            group = "npm"

            val url = cagDownloadUrl(target)
            inputs.property("url", url)
            outputs.file(cagArchive)

            doLast {
                val dest = cagArchive.get().asFile
                dest.parentFile.mkdirs()
                logger.lifecycle("Downloading sonar-context-augmentation for ${target.id} from $url")
                URI(url).toURL().openStream().use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }

        tasks.register<Copy>("extractCag${target.titleCase}") {
            description = "Extract sonar-context-augmentation archive for ${target.id}."
            group = "npm"
            dependsOn(downloadCagTask)

            from(tarTree(resources.gzip(cagArchive)))
            into(cagExtractDir)
        }
    } else {
        null
    }

    // -----------------------------------------------------------------------
    // Stage the npm platform package directory
    // -----------------------------------------------------------------------

    val platformStageDir = npmStageDir.map { it.dir("sonarqube-mcp-server-${target.id}") }

    val stagePlatformTask = tasks.register<Copy>("assembleNpmPackage${target.titleCase}") {
        description = "Stage the @sonarsource/sonarqube-mcp-server-${target.id} npm package directory."
        group = "npm"

        val fatJar = tasks.named<Jar>("jar")
        dependsOn(jlinkTask, fatJar)
        extractCagTask?.let { dependsOn(it) }

        into(platformStageDir)

        // JAR
        from(fatJar.map { it.archiveFile }) {
            rename { "sonarqube-mcp-server.jar" }
        }

        // sonar-context-augmentation binary (only when a variant is published)
        if (target.cagVariant != null) {
            from(cagExtractDir) {
                include("sonar-context-augmentation", "sonar-context-augmentation.exe")
                into("bin")
                fileMode = 0b111_101_101 // 0755
            }
        }

        // package.json (substituted from template)
        from(rootProject.file("npm/platform/package.json.template")) {
            rename { "package.json" }
            filter { line ->
                line
                    .replace("@@TARGET_ID@@", target.id)
                    .replace("@@VERSION@@", npmPackageVersion)
                    .replace("@@NPM_OS@@", target.npmOs)
                    .replace("@@NPM_CPU@@", target.npmCpu)
            }
        }

        // README.md (substituted from template)
        from(rootProject.file("npm/platform/README.md.template")) {
            rename { "README.md" }
            filter { line ->
                line
                    .replace("@@TARGET_ID@@", target.id)
                    .replace("@@VERSION@@", npmPackageVersion)
            }
        }

        from(rootProject.file("LICENSE"))
    }

    // -----------------------------------------------------------------------
    // Optional: pack the staged directory into a tarball (requires npm on PATH)
    // -----------------------------------------------------------------------

    tasks.register<Exec>("packNpmPackage${target.titleCase}") {
        description = "Run `npm pack` on the staged @sonarsource/sonarqube-mcp-server-${target.id} package."
        group = "npm"
        dependsOn(stagePlatformTask)

        val outDir = npmTarballDir
        doFirst { outDir.get().asFile.mkdirs() }

        workingDir = platformStageDir.get().asFile
        commandLine("npm", "pack", "--pack-destination", outDir.get().asFile.absolutePath)
    }
}

// ---------------------------------------------------------------------------
// Meta package staging
// ---------------------------------------------------------------------------

val metaStageDir = npmStageDir.map { it.dir("sonarqube-mcp-server-meta") }

val assembleNpmMetaPackage by tasks.registering(Copy::class) {
    description = "Stage the @sonarsource/sonarqube-mcp-server meta npm package directory."
    group = "npm"

    into(metaStageDir)

    from(rootProject.file("npm/meta/bin")) {
        into("bin")
        fileMode = 0b111_101_101 // 0755
    }
    from(rootProject.file("npm/meta/README.md"))
    from(rootProject.file("LICENSE"))
    from(rootProject.file("npm/meta/package.json.template")) {
        rename { "package.json" }
        filter { line -> line.replace("@@VERSION@@", npmPackageVersion) }
    }
}

tasks.register<Exec>("packNpmMetaPackage") {
    description = "Run `npm pack` on the staged meta package."
    group = "npm"
    dependsOn(assembleNpmMetaPackage)

    val outDir = npmTarballDir
    doFirst { outDir.get().asFile.mkdirs() }

    workingDir = metaStageDir.get().asFile
    commandLine("npm", "pack", "--pack-destination", outDir.get().asFile.absolutePath)
}

// ---------------------------------------------------------------------------
// Umbrella tasks
// ---------------------------------------------------------------------------

tasks.register("assembleAllNpmPackages") {
    description = "Stage all npm package directories (meta + every platform)."
    group = "npm"
    dependsOn(assembleNpmMetaPackage)
    npmTargets.forEach { target ->
        dependsOn("assembleNpmPackage${target.titleCase}")
    }
}

tasks.register("packAllNpmPackages") {
    description = "Run `npm pack` for the meta package and every platform package."
    group = "npm"
    dependsOn("packNpmMetaPackage")
    npmTargets.forEach { target ->
        dependsOn("packNpmPackage${target.titleCase}")
    }
}

// Thanks to Lukas04 and atlanticaccent


//Automatically points to the starsector folder if the mod is placed in to the "mods" folder.
//If you do not place the project in to your mods folder, replace this with the path to Starsectors root folder.
val starsectorPath = "../../"


val modId = "SN_FleetBuilder"
val modName = "FleetBuilder" // For the developer to see. Don't use spaces here
val modPlugin = "fleetBuilder.core.integration.plugin.FleetBuilderPlugin"
val modVersion = "1.40.0"
val gameVersion = "0.98a-RC8"
val isUtilityMod = true

val modAuthor = "S-Numan"
val modDescription = "Help with easily managing fleets by providing tools to copy, add, and save; fleets, officers, ships, variants, and more.\n\nThis mod can be safely added and removed at any time."
val displayName = " $modName" // For the user to see.

val shouldAutomaticallyCreateMetadataFiles = true

//Other mods to load as compile-time dependencies. Adding them will provide auto-complete for their functions.
//Each entry is the jar name. The build searches every mod /jars/ folder for a matching file ("LazyLib.jar" -> "Starsector/mods/LazyLib/jars/LazyLib.jar")
//Mods added this way still need to be added to mod_info.json if they are always required (hard-dependency).
val modDependencies = listOf(
    "LazyLib.jar", //LazyLib
    "LazyLib-Kotlin.jar",

    "MagicLib.jar", //MagicLib
    "MagicLib-Kotlin.jar",

    "LunaLib.jar", //LunaLib

    "lw_Console.jar",
    "CombatChatter.jar",
    "SecondInCommand.jar",
)

val modDependenciesData = listOf(
    ModDependency(
        id = "lw_lazylib",
        name = "LazyLib"
        //version = "2.7b"
    ),
    ModDependency(
        id = "MagicLib",
        name = "MagicLib"

    )
)


// Version checker. OPTIONAL
val directDownloadURL = "https://github.com/S-Numan/FleetBuilder/releases/latest/download/FleetBuilder.zip"
val changelogURL = "https://raw.githubusercontent.com/S-Numan/FleetBuilder/master/CHANGELOGS.md"
val masterVersionFile = "https://raw.githubusercontent.com/S-Numan/FleetBuilder/master/fleetbuilder.version"
val modThreadId = "33414"


//Files and folders (relative to the project root) included in the packaged zip.
//Directories keep their structure in the zip; files are placed at the zip root.
//Missing entries are silently skipped by Gradle.
val packageIncludes = listOf(
    "mod_info.json",
    "data",
    "graphics",
    "sounds",
    "src",
    "LICENSE"
)

//File extensions to include from the project root in the packaged zip.
//Each entry is matched as "*.<ext>" against files directly in the project root.
val packageIncludeExtensions = listOf(
    "version",
    "md",
    "txt"
)

//Additional jars to include, like libraries you ship with your mod.
//Paths are relative to this projects root directory.
val otherDependencies = listOf<String>(
    // "jars/dependency.jar",
)

//Folder (relative to this project root) that is also searched for modDependencies and otherDependencies.
//Drop jars here when you don't have the source mod installed under /mods/, or want to pin a specific version.
//For modDependencies, files are matched by filename (recursively).
//For otherDependencies, the entry's path is also tried relative to this folder.
val libsFolder = "libs"

//Java version to use. Should be 17, as it is what starsector itself uses.
val javaVersion = 17


val modFolderName = modName.replace(" ", "-") // Defaults to the name of your mod, with spaces replaced by hyphens.
val modVersionName = "fleetbuilder"//modId // Defaults to the mod id.
val jarFileName = "${modName}.jar"
val jars = arrayOf("jars/$jarFileName")


/// BUILD PIPELINE
/// In Most cases, you should not need to change anything below here.

fun File.writeIfChanged(content: String) {
    if (!exists() || readText() != content) {
        writeText(content)
    }
}

fun String.jsonEscape(): String =
    this
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")

data class ModDependency(
    val id: String,
    val name: String,
    val version: String? = null
)

fun ModDependency.toJson(): String {
    val versionPart = version?.let { """"version": "$it"""" }

    return buildString {
        append("{")
        append(""""id": "$id", """)
        append(""""name": "$name"""")

        if (versionPart != null) {
            append(", ")
            append(versionPart)
        }

        append("}")
    }
}

val docsRepoDir = layout.buildDirectory.dir("communityApiDocs")

dependencies {
    addModJars(modDependencies)
    otherDependencies.forEach { addCompileOnlyJar(it) }

    //Loads basic starsector dependencies.
    addStarsectorCoreDependencies()
}

fun DependencyHandler.addStarsectorCoreDependencies() {

    //Starsectors core jars live in different folders per OS, so look them up through the layout.
    val coreDir = starsectorLayout().gameWorkingDir

    //Starsector. The API jar comes through the local Maven repo (see repositories block) so IntelliJ can attach its source.
    //starfarer_obf is obfuscated with no source available, so it stays a plain file dependency.
    compileOnly("com.fs.starfarer:starfarer-api:local")

    //All other core jars in one files(...) call.
    compileOnly(
        files(
            File(coreDir, "starfarer_obf.jar"),
            File(coreDir, "commons-compiler.jar"),
            File(coreDir, "commons-compiler-jdk.jar"),
            File(coreDir, "fs.common_obf.jar"),
            File(coreDir, "fs.sound_obf.jar"),
            File(coreDir, "janino.jar"),
            File(coreDir, "jaxb-api-2.4.0-b180830.0359.jar"),
            File(coreDir, "jaxb-api-2.4.0-b180830.0359-sources.jar"),
            File(coreDir, "jinput.jar"),
            File(coreDir, "jogg-0.0.7.jar"),
            File(coreDir, "jorbis-0.0.15.jar"),
            File(coreDir, "json.jar"),
            File(coreDir, "log4j-1.2.9.jar"),
            File(coreDir, "lwjgl.jar"),
            File(coreDir, "lwjgl_util.jar"),
            File(coreDir, "txw2-3.0.2.jar"),
            File(coreDir, "webp-imageio-0.1.6.jar"),
            File(coreDir, "xstream-1.4.10.jar"),
        )
    )
}



plugins {
    // Apply the org.jetbrains.kotlin.jvm Plugin to add support for Kotlin.
    alias(libs.plugins.kotlin.jvm)

    // Apply the java-library plugin for API and implementation separation.
    `java-library`
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()

    //Local Maven repo of staged Starsector API artifacts. The maven layout (vs flatDir) is what
    //actually lets IntelliJ pick up the "-sources.jar" sibling for autocomplete and navigation.
    maven { url = uri(stageStarsectorApi()) }
}

// Apply a specific Java toolchain to ease working on different environments.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(javaVersion)
    }
}

sourceSets {
    main {
        java {
            setSrcDirs(listOf("src"))
        }
        kotlin {
            setSrcDirs(listOf("src"))
        }
    }
}


tasks.test {
    enabled = false
}

fun DependencyHandler.addModJars(jarNames: List<String>) {
    if (jarNames.isEmpty()) return

    val modsDir = file("$starsectorPath/mods/")
    // Exclude this project's own folder. Otherwise, the configuration cache
    // treats the mods own /jars/ directory listing as a config-time input, and
    // every rebuild of the mod jar invalidates the cache.
    val thisProjectFolder = projectDir.name
    val modJarFiles = fileTree(modsDir) {
        jarNames.forEach { include("*/jars/**/$it") }
        exclude("$thisProjectFolder/**")
    }

    // Also look inside the local libs folder, if present. Matched by filename, recursively.
    val libsDir = file(libsFolder)
    val libsJarFiles = if (libsDir.exists()) {
        fileTree(libsDir) {
            jarNames.forEach { include("**/$it") }
        }
    } else {
        files()
    }

    val allJarFiles = modJarFiles + libsJarFiles

    // Realize the file tree once to detect missing entries.
    val foundNames = allJarFiles.files.map { it.name }.toSet()
    jarNames.filterNot { it in foundNames }.forEach { missing ->
        logger.error(
            "Mod dependency '$missing' was not found in any mod's " +
                    "/jars folder under ${modsDir.absolutePath} " +
                    "or in ${libsDir.absolutePath}."
        )
    }

    compileOnly(allJarFiles)
}

fun DependencyHandler.addCompileOnlyJar(path: String) {
    val jarFile = file(path)
    if (jarFile.exists()) {
        compileOnly(files(jarFile))
        return
    }
    // Fallback: try resolving the same path relative to the libs folder.
    val libsFile = file("$libsFolder/$path")
    if (libsFile.exists()) {
        compileOnly(files(libsFile))
        return
    }
    logger.error(
        "Dependency '$path' was not found at ${jarFile.absolutePath} " +
                "or at ${libsFile.absolutePath}."
    )
}

enum class StarsectorPlatform { WINDOWS, LINUX, MAC }

// Functions rather than vals so they can be called from the `dependencies {}`
// block at the top of the script, which runs before any val declared below it
// would be initialized.
fun currentPlatform(): StarsectorPlatform = System.getProperty("os.name").lowercase().let { os ->
    when {
        "win" in os -> StarsectorPlatform.WINDOWS
        "mac" in os || "darwin" in os -> StarsectorPlatform.MAC
        else -> StarsectorPlatform.LINUX
    }
}

//Holds the per-OS paths Starsector needs: the launcher file, the bundled java executable, and the games working dir.
data class StarsectorLayout(
    val launcherFile: File,
    val javaExecutable: File,
    val gameWorkingDir: File,
)

//Resolves all three paths for the current OS. Starsector ships a different folder structure on each platform.
fun starsectorLayout(): StarsectorLayout = file(starsectorPath).let { root ->
    when (currentPlatform()) {
        StarsectorPlatform.WINDOWS -> StarsectorLayout(
            launcherFile = File(root, "vmparams"),
            javaExecutable = File(root, "jre/bin/java.exe"),
            gameWorkingDir = File(root, "starsector-core"),
        )
        StarsectorPlatform.LINUX -> StarsectorLayout(
            launcherFile = File(root, "starsector.sh"),
            javaExecutable = File(root, "jre_linux/bin/java"),
            gameWorkingDir = root,
        )
        StarsectorPlatform.MAC -> StarsectorLayout(
            launcherFile = File(root, "Contents/MacOS/starsector_mac.sh"),
            javaExecutable = File(root, "Contents/Home/bin/java"),
            gameWorkingDir = File(root, "Contents/Resources/Java"),
        )
    }
}

//Reads a file's modification time in a way the configuration cache will track as an input.
//Plain File.lastModified() calls at config time are NOT tracked by Gradle, so without this
//a Starsector update would not invalidate the cache and we'd keep serving the old staged
//API jar. Routing through a ValueSource is the documented escape hatch for "track external
//file state at configuration time".
abstract class FileMtimeSource : ValueSource<Long, FileMtimeSource.Parameters> {
    interface Parameters : ValueSourceParameters {
        val path: Property<String>
    }

    override fun obtain(): Long = File(parameters.path.get()).let {
        if (it.exists()) it.lastModified() else -1L
    }
}

//Stages the Starsector API as a local Maven repo under build/starsector-api/.
//Using a maven layout (not flatDir) because IntelliJ only reliably attaches sources when the
//artifact has a POM and follows the standard "<name>-<version>-sources.jar" classifier convention.
//A jar IS a zip with optional manifest, so the source side is just a copy with the right filename.
//If you ever hit a zip layout IntelliJ does not like, swap the copy for a real extract + repack.
//Runs at configuration time so the files exist before Gradle resolves dependencies (including IDE sync).
fun stageStarsectorApi(): File {
    val repoDir = layout.buildDirectory.dir("starsector-api").get().asFile
    val artifactDir = File(repoDir, "com/fs/starfarer/starfarer-api/local")
    val coreDir = starsectorLayout().gameWorkingDir

    val srcJar = File(coreDir, "starfarer.api.jar")
    val srcZip = File(coreDir, "starfarer.api.zip")
    val dstJar = File(artifactDir, "starfarer-api-local.jar")
    val dstSources = File(artifactDir, "starfarer-api-local-sources.jar")
    val pomFile = File(artifactDir, "starfarer-api-local.pom")

    //Force the configuration cache to depend on the source file mtimes. The `.get()` calls
    //pull the values, which makes them part of the cache fingerprint. When Starsector is
    //updated and these mtimes change, the cache invalidates and the staging logic re-runs.
    providers.of(FileMtimeSource::class.java) { parameters.path.set(srcJar.absolutePath) }.get()
    providers.of(FileMtimeSource::class.java) { parameters.path.set(srcZip.absolutePath) }.get()

    require(srcJar.exists()) {
        "Starsector API jar not found at ${srcJar.absolutePath}. " +
                "Check starsectorPath at the top of this build script."
    }

    val docsSrcDir = docsRepoDir.get().asFile.resolve("src")

    if (docsSrcDir.exists()) {
        project.copy {
            from(docsSrcDir)
            into(layout.buildDirectory.dir("tmp/communitySources").get().asFile)
        }

        ant.withGroovyBuilder {
            "zip"(
                "destfile" to dstSources.absolutePath,
                "basedir" to docsSrcDir.absolutePath
            )
        }
    }

    //Fast path: staged files match (or post-date) their sources, so we can return without doing anything.
    val jarFresh = dstJar.exists() && dstJar.lastModified() >= srcJar.lastModified()
    val sourcesFresh = !srcZip.exists() || (dstSources.exists() && dstSources.lastModified() >= srcZip.lastModified())
    if (jarFresh && sourcesFresh && pomFile.exists()) return repoDir

    artifactDir.mkdirs()

    //Only copy if the source is newer than the staged file, so repeat syncs are cheap.
    fun stageIfStale(src: File, dst: File) {
        if (!src.exists()) return
        if (!dst.exists() || dst.lastModified() < src.lastModified()) {
            src.copyTo(dst, overwrite = true)
        }
    }

    stageIfStale(srcJar, dstJar)
    stageIfStale(srcZip, dstSources)

    //Minimal POM. Gradle's maven resolver needs one to recognise the artifact and to look up the -sources classifier.
    if (!pomFile.exists()) {
        pomFile.writeIfChanged(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.fs.starfarer</groupId>
                <artifactId>starfarer-api</artifactId>
                <version>local</version>
            </project>
            """.trimIndent()
        )
    }
    return repoDir
}

data class StarsectorLaunchSpec(
    val jvmArgs: List<String>,
    val classpath: List<File>,
    val mainClass: String,
)

//Intermediate value returned by each per-platform parser. Classpath entries are still
//strings here. parseLauncher() resolves them to absolute Files against the working dir.
data class RawLaunchSpec(
    val jvmArgs: List<String>,
    val classpath: List<String>,
    val mainClass: String,
)

//Whitespace-aware tokenizer that keeps quoted content as a single token. Single or double
//quotes group their content; the quote chars themselves are consumed. Needed so launcher
//flags like -Dfoo="bar baz" survive instead of becoming two tokens.
fun shellTokenize(s: String): List<String> {
    val out = mutableListOf<String>()
    val cur = StringBuilder()
    var quote: Char? = null
    for (c in s) when {
        quote != null -> if (c == quote) quote = null else cur.append(c)
        c == '"' || c == '\'' -> quote = c
        c.isWhitespace() -> if (cur.isNotEmpty()) {
            out += cur.toString(); cur.clear()
        }
        else -> cur.append(c)
    }
    if (cur.isNotEmpty()) out += cur.toString()
    return out
}

//vmparams is a single line listing every flag, separated by whitespace.
fun parseWindowsLauncher(file: File): RawLaunchSpec {
    val tokens = shellTokenize(file.readText().trim())
    return sliceJavaCommand(tokens, classpathSeparator = ';', sourceForError = file)
}

//starsector.sh is a multi-line shell script with one flag per line, joined by `\`-continuations.
//Drop comment lines, then collapse each `\<newline>` into a space so the whole java invocation
//lands on one logical line before tokenizing.
fun parseLinuxLauncher(file: File): RawLaunchSpec {
    val joined = file.readLines()
        .filterNot { it.trim().startsWith("#") }
        .joinToString("\n")
        .replace(Regex("""\\\r?\n"""), " ")
    val tokens = shellTokenize(joined)
    return sliceJavaCommand(tokens, classpathSeparator = ':', sourceForError = file)
}

//Same as Linux but additionally drops `${VAR}` placeholders. The mac script has `${EXTRAARGS}`
//as an injection point that would normally be expanded by the shell; we have nothing to expand
//it to, so we skip the token.
fun parseMacLauncher(file: File): RawLaunchSpec {
    val joined = file.readLines()
        .filterNot { it.trim().startsWith("#") }
        .joinToString("\n")
        .replace(Regex("""\\\r?\n"""), " ")
    val tokens = shellTokenize(joined).filterNot { it.startsWith("\${") }
    return sliceJavaCommand(tokens, classpathSeparator = ':', sourceForError = file)
}

//Pulls jvmArgs, classpath entries, and main class out of the tokenized java invocation.
//Expected layout: [java] [jvmArgs...] [-classpath|-cp] [cp string] [mainClass] [args...]
fun sliceJavaCommand(
    tokens: List<String>,
    classpathSeparator: Char,
    sourceForError: File,
): RawLaunchSpec {
    //Match the executable by basename. Case-sensitive on purpose: the Mac script does
    //`cd ../Resources/Java`, and lowercase `java` must not match the uppercase `Java` dir.
    val javaIdx = tokens.indexOfFirst { token ->
        val basename = token.substringAfterLast('/').substringAfterLast('\\')
        basename == "java" || basename == "java.exe"
    }
    require(javaIdx >= 0) { "Could not locate the java invocation in $sourceForError" }

    //First -classpath/-cp after the java token. If Starsector ever switches to --module-path,
    //this is where it would break; extend the parser then.
    val cpIdx = (javaIdx + 1 until tokens.size).firstOrNull { i ->
        tokens[i] == "-classpath" || tokens[i] == "-cp"
    } ?: error("Could not locate -classpath/-cp in $sourceForError")
    require(cpIdx + 2 < tokens.size) {
        "Missing classpath value or main class in $sourceForError"
    }

    val classpath = tokens[cpIdx + 1].split(classpathSeparator)
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    return RawLaunchSpec(
        //Everything between `java` and `-classpath` is treated as a jvm arg.
        jvmArgs = tokens.subList(javaIdx + 1, cpIdx),
        classpath = classpath,
        mainClass = tokens[cpIdx + 2],
    )
}

//Reads the launcher for the current OS and returns a fully resolved launch spec.
//Relative classpath entries get resolved against the games working directory.
fun parseLauncher(): StarsectorLaunchSpec {
    val layout = starsectorLayout()
    val launcherFile = layout.launcherFile
    require(launcherFile.exists()) {
        "Starsector launcher file not found at ${launcherFile.absolutePath} " +
                "(expected for platform=${currentPlatform()})"
    }

    val raw = when (currentPlatform()) {
        StarsectorPlatform.WINDOWS -> parseWindowsLauncher(launcherFile)
        StarsectorPlatform.LINUX -> parseLinuxLauncher(launcherFile)
        StarsectorPlatform.MAC -> parseMacLauncher(launcherFile)
    }

    val workingDirPath = layout.gameWorkingDir.toPath()
    val classpath = raw.classpath.map { workingDirPath.resolve(it).normalize().toFile() }
    return StarsectorLaunchSpec(raw.jvmArgs, classpath, raw.mainClass)
}

val launcherInfo by lazy { starsectorLayout() to parseLauncher() }

val jbrLauncher = javaToolchains.launcherFor {
    languageVersion = JavaLanguageVersion.of(javaVersion)
    vendor = JvmVendorSpec.AZUL
}

//AllowEnhancedClassRedefinition requires Serial or G1 GC, but Starsector's vmparams
//configures Shenandoah. Drop the Shenandoah-specific flags so JBR falls back to its
//default (G1). Only affects these gradle tasks; the in-game launcher (vmparams) is
//untouched, so normal runs still use Shenandoah.
fun List<String>.forJbr(): List<String> = filterNot { it.contains("Shenandoah") }

runCatching {
    val (layout, parsed) = launcherInfo

    val outputFile = File(layout.gameWorkingDir, "devvmparams.txt")

    // 1. Remove the unwanted JVM arg
    val filteredJvmArgs = parsed.jvmArgs
        .filterNot { it == "-XX:+PrintCodeCache" }

    // 2. Platform-specific classpath tail
    val tail = when (currentPlatform()) {
        StarsectorPlatform.WINDOWS ->
            """-classpath janino.jar;commons-compiler.jar;commons-compiler-jdk.jar;starfarer.api.jar;starfarer_obf.jar;jogg-0.0.7.jar;jorbis-0.0.15.jar;json.jar;lwjgl.jar;jinput.jar;log4j-1.2.9.jar;lwjgl_util.jar;fs.sound_obf.jar;fs.common_obf.jar;xstream-1.4.10.jar;txw2-3.0.2.jar;jaxb-api-2.4.0-b180830.0359.jar;webp-imageio-0.1.6.jar com.fs.starfarer.StarfarerLauncher"""

        StarsectorPlatform.LINUX, StarsectorPlatform.MAC ->
            """-classpath janino.jar:commons-compiler.jar:commons-compiler-jdk.jar:starfarer.api.jar:starfarer_obf.jar:jogg-0.0.7.jar:jorbis-0.0.15.jar:json.jar:lwjgl.jar:jinput.jar:log4j-1.2.9.jar:lwjgl_util.jar:fs.sound_obf.jar:fs.common_obf.jar:xstream-1.4.10.jar:txw2-3.0.2.jar:jaxb-api-2.4.0-b180830.0359.jar:webp-imageio-0.1.6.jar com.fs.starfarer.StarfarerLauncher "$@""""
    }

    // 3. Combine everything
    val content = (filteredJvmArgs + tail)
        .joinToString(System.lineSeparator())

    // 4. Only write if changed (prevents spam)
    if (!outputFile.exists() || outputFile.readText() != content) {
        outputFile.writeText(content)
        logger.lifecycle("Updated devvmparams.txt")
    }

}.onFailure { e ->
    logger.warn("Failed to write devvmparams.txt (non-fatal): ${e.message}")
}

runCatching {
    val gradleXml = file(".idea/gradle.xml")
    if (gradleXml.exists()) {
        val text = gradleXml.readText()
        val canonical = """<option name="delegatedBuild" value="false" />"""
        val existingLine = Regex("""<option name="delegatedBuild" value="(?:true|false)"\s*/>""")
        val updated = if (existingLine.containsMatchIn(text)) {
            text.replace(existingLine, canonical)
        } else {
            //Insert as first child of the GradleProjectSettings block if present.
            Regex("""<GradleProjectSettings[^>]*>""").find(text)?.let { match ->
                text.replaceRange(match.range.last + 1, match.range.last + 1, "\n        $canonical")
            } ?: text
        }
        if (updated != text) gradleXml.writeText(updated)
    }
}.onFailure { e ->
    logger.warn("Could not enforce delegatedBuild=false in .idea/gradle.xml (non-fatal): ${e.message}")
}

// Prevent gradle from compiling
tasks.matching {
    it.name in setOf("build", "assemble", "jar", "test", "check", "compileJava", "compileKotlin")
}.configureEach {
    doFirst {
        throw GradleException("Configuration updated. Run build again.")
    }
}


// Change WORKING_DIRECTORY's in .run files, and the modName.
runCatching {
    val runDir = file(".run")
    if (!runDir.exists()) return@runCatching

    val desiredWorkingDir = when (currentPlatform()) {
        StarsectorPlatform.WINDOWS -> "\$ProjectFileDir\$/../../starsector-core"
        StarsectorPlatform.LINUX, StarsectorPlatform.MAC -> "\$ProjectFileDir\$/../../"
    }

    val regex = Regex("""<option name="WORKING_DIRECTORY" value="[^"]*" />""")

    runDir.listFiles { f -> f.extension == "xml" }?.forEach { file ->
        val original = file.readText()

        val workingDirReplacement =
            """<option name="WORKING_DIRECTORY" value="$desiredWorkingDir" />"""
        val safeWorkingDirReplacement = Regex.escapeReplacement(workingDirReplacement)

        val moduleRegex = Regex("""<module name="[^"]*\.main"\s*/>""")
        val moduleReplacement = """<module name="$modName.main" />"""
        val safeModuleReplacement = Regex.escapeReplacement(moduleReplacement)

        var updated = original

        // Replace WORKING_DIRECTORY
        updated = if (regex.containsMatchIn(updated)) {
            updated.replace(regex, safeWorkingDirReplacement)
        } else {
            Regex("""<configuration[^>]*>""").find(updated)?.let { match ->
                updated.replaceRange(
                    match.range.last + 1,
                    match.range.last + 1,
                    "\n    $workingDirReplacement"
                )
            } ?: updated
        }

        // Replace module name
        if (moduleRegex.containsMatchIn(updated)) {
            updated = updated.replace(moduleRegex, safeModuleReplacement)
        }

        if (updated != original) {
            file.writeText(updated)
            logger.lifecycle("Updated ${file.name}")
        }
    }

}.onFailure { e ->
    logger.warn("Failed to patch .run configs (non-fatal): ${e.message}")
}

// Change artifact names
runCatching {
    val artifactFile = file(".idea/artifacts/Create_jar.xml")
    if (!artifactFile.exists()) return@runCatching

    val original = artifactFile.readText()

    var updated = original

    // Replace module-output name="X.main"
    val moduleRegex = Regex("""name="[^"]+\.main"""")
    updated = updated.replace(moduleRegex, """name="$modName.main"""")

    // Replace jar output name="Something.jar"
    val jarRegex = Regex("""name="[^"]+\.jar"""")
    updated = updated.replace(jarRegex, """name="$jarFileName"""")

    if (updated != original) {
        artifactFile.writeText(updated)
        logger.lifecycle("Updated artifact Create_jar.xml to $jarFileName")
    }

}.onFailure { e ->
    logger.warn("Failed to patch artifact XML (non-fatal): ${e.message}")
}


runCatching {

    val version = modVersion.split(".").let { Triple(it[0], it[1], it[2]) }
    System.setProperty("line.separator", "\n") // Use LF instead of CRLF like a normal person

    if (shouldAutomaticallyCreateMetadataFiles) {
        // Generates a mod_info.json from the variables defined at the top of this script.
        File(projectDir, "mod_info.json")
            .writeIfChanged(
                """
{
    "id": "$modId",
    "name": "$displayName",
    "author": "$modAuthor",
    "utility": "$isUtilityMod",
    "version": { "major":"${version.first}", "minor": "${version.second}", "patch": "${version.third}" },
    "description": "${modDescription.jsonEscape()}",
    "jars": [
        ${jars.joinToString { "\"$it\"" }}
    ],
    "modPlugin":"$modPlugin",
    "gameVersion": "$gameVersion",
    "dependencies": [
${modDependenciesData.joinToString(",\n") { "       " + it.toJson() }}
    ],
}
""".trimIndent()
            )

        if (directDownloadURL.isNotBlank() || changelogURL.isNotBlank() || masterVersionFile.isNotBlank() || modThreadId.isNotBlank()) {
            // Generates a Version Checker csv file from the variables defined at the top of this script.
            with(File(projectDir, "data/config/version/version_files.csv")) {
                this.parentFile.mkdirs()
                this.writeIfChanged(
                    """
                    version file
                    ${modVersionName}.version

                    """.trimIndent()
                )
            }

            // Generates a Version Checker .version file from the variables defined at the top of this script.
            val fields = mutableListOf<String>()

            if (directDownloadURL.isNotBlank()) {
                fields += """"directDownloadURL":"$directDownloadURL""""
            }

            if (changelogURL.isNotBlank()) {
                fields += """"changelogURL":"$changelogURL""""
            }

            if (masterVersionFile.isNotBlank()) {
                fields += """"masterVersionFile":"$masterVersionFile""""
            }

            fields += """"modName":"$displayName""""

            if (modThreadId.isNotBlank()) {
                fields += """"modThreadId":$modThreadId"""
            }

            fields += """
"modVersion":
    {
        "major":${version.first},
        "minor":${version.second},
        "patch":${version.third}
    }
        """.trimIndent()

            File(projectDir, "${modVersionName}.version").writeIfChanged(
                """
{
    ${fields.joinToString(",\n    ")}
}
        """.trimIndent()
            )
        }

        // Creates a file with the mod name to tell the Github Actions script the name of the mod.
        // Not needed if not using Github Actions (but doesn't hurt to keep).
        with(File(projectDir, ".github/workflows/mod-folder-name.txt")) {
            this.parentFile.mkdirs()
            this.writeIfChanged(modFolderName)
        }

    }
}


val cloneCommunityApiDocs = tasks.register<Exec>("cloneCommunityApiDocs") {
    isIgnoreExitValue = true

    val targetDir = docsRepoDir.map { it.asFile }

    onlyIf {
        !targetDir.get().resolve(".git").exists()
    }

    doFirst {
        val dir = targetDir.get()
        dir.mkdirs()
    }

    commandLine(
        "git", "clone",
        "--no-checkout",
        "--filter=blob:none",
        "https://github.com/StarsectorCommunityApiDocs/CommunityApiDocs.git",
        targetDir.get().absolutePath
    )
}
val sparseCheckoutCommunityApiDocs = tasks.register<Exec>("sparseCheckoutCommunityApiDocs") {
    isIgnoreExitValue = true

    val repoDir = docsRepoDir.get().asFile

    onlyIf {
        repoDir.resolve(".git").exists()
    }

    workingDir(repoDir)

    commandLine(
        "git", "sparse-checkout",
        "set", "src"
    )
}
val checkoutCommunityApiDocs = tasks.register<Exec>("checkoutCommunityApiDocs") {
    isIgnoreExitValue = true

    val repoDir = docsRepoDir.get().asFile

    onlyIf {
        repoDir.resolve(".git").exists()
    }

    workingDir(repoDir)

    commandLine("git", "checkout")
}
val updateCommunityApiDocs = tasks.register<Exec>("updateCommunityApiDocs") {
    isIgnoreExitValue = true

    val repoDir = docsRepoDir.get().asFile

    onlyIf {
        repoDir.resolve(".git").exists()
    }

    // Much cheaper than scanning entire repo
    inputs.file(repoDir.resolve(".git/HEAD"))
    outputs.file(repoDir.resolve(".git/FETCH_HEAD"))


    workingDir(repoDir)

    commandLine("git", "fetch", "--quiet")
}
val createCommunityDocs = tasks.register<Jar>("createCommunityDocs") {
    dependsOn(
        cloneCommunityApiDocs,
        sparseCheckoutCommunityApiDocs,
        checkoutCommunityApiDocs,
        updateCommunityApiDocs
    )
}

// Force run createCommunityDocs every gradle sync
tasks.named("prepareKotlinBuildScriptModel") {
    dependsOn(createCommunityDocs)
}

updateCommunityApiDocs.configure {
    mustRunAfter(checkoutCommunityApiDocs)
}

checkoutCommunityApiDocs.configure {
    mustRunAfter(sparseCheckoutCommunityApiDocs)
}

sparseCheckoutCommunityApiDocs.configure {
    mustRunAfter(cloneCommunityApiDocs)
}
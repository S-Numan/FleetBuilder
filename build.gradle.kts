import java.util.zip.ZipFile

/**
 * The name of your mod. Used to create a mod folder name (and the name of your mod, if using auto-updated mod_info.json).
 * Defaults to the name of the mod's folder.
 */
val modName = rootDir.name

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

/** Set below to `true` to automatically create mod_info.json and Version Checker files. */
val shouldAutomaticallyCreateMetadataFiles = true

// REQUIRED CORE SETTINGS
val modId = "SN_FleetBuilder" // Unique ID of your mod. Ensure it is unique from other mods.
val gameVersion = "0.98a-RC8" // The version of Starsector that the mod is intended for.
val modVersion = "1.40.1" // Your mod version
val isUtilityMod = true // Whether this mod can be safely removed from a save.
var modPlugin = "fleetBuilder.core.integration.plugin.FleetBuilderPlugin" // Path to your main plugin class

// DEPENDENCIES (mods required to run yours)
val modInfoDependencyData = listOf(
    ModDependency(id = "lw_lazylib", name = "LazyLib"),
    ModDependency(id = "MagicLib", name = "MagicLib"),
)

// USER-FACING INFO
val modAuthor = "S-Numan"
val displayName = "FleetBuilder" // This is the user facing name of your mod.
val modDescription = "Help with easily managing fleets by providing tools to copy, add, and save; fleets, officers, ships, variants, and more.\n\nThis mod can be safely added and removed at any time." // The description of your mod as it appears in the mod loader.


// OPTIONAL: VERSION CHECKER (leave blank if not using)
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

val modNameNoSpaces = modName.replace(" ", "-") // Defaults to the name of your mod, with spaces replaced by hyphens.

//Folder (relative to this project root) that is also searched for modDependencies and otherDependencies.
//Drop jars here when you don't have the source mod installed under /mods/, or want to pin a specific version.
//For modDependencies, files are matched by filename (recursively).
//For otherDependencies, the entry's path is also tried relative to this folder.
val libsFolder = "libs"

val devResolution = "1920x1080"

//Java version to use. Should be 17, as it is what starsector itself uses.
val javaVersion = 17

//When set to true, .java and .kt source files will be bundled with your jar. This will provide people
//with real javadocs/comments when viewing things from your mod within their IDE. Does increase the jar size.
//You should set this to "true" if you expect other people to add your mod as a dependency
val isLibrary = true

val modVersionName = "fleetbuilder" // Name of the .version file. Defaults to the mod id.

val jarName = "${modNameNoSpaces}.jar"
val jars = arrayOf("jars/$jarName")

//Name for the Zip that is created when you run package_mod.bat.
//This zip includes the data, graphics, jars, sounds and src folder.
//It also includes the mod_info.json and .version files at the root folder.
val zipName = "$modNameNoSpaces.zip"

//Automatically points to the starsector folder if the mod is placed in to the "mods" folder.
//If you do not place the project in to your mods folder, replace this with the path to Starsectors root folder.
val starsectorPath = "../../"





runCatching {
    // Auto detect mod plugin if it was not found
    if (!classExistsInSrc(modPlugin, file("src")))
        modPlugin = findModPluginClass(file("src")) ?: modPlugin
}

/// BUILD PIPELINE
/// In Most cases, you should not need to change anything below here.

//Local Maven repo where mod-dependency jars get staged, along with a matching "-sources.jar"
//(see stageModDependency / addModJars below). Declared up here (rather than next to docsRepoDir)
//because it needs to be initialized before the dependencies{} block runs, which happens earlier
//in this file.
val modDepsRepoDir = layout.buildDirectory.dir("modDepsRepo").get().asFile

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

    // The built-in `idea` plugin lets us steer IntelliJ's module config from this script,
    // namely the compile-output dirs (see the `idea { ... }` block below).
    idea
}

// Move IntelliJ's compiled output from out/ to build/idea-out/ so we only have one top-level
// build folder.
idea {
    module {
        outputDir = file("build/idea-out/main")
        testOutputDir = file("build/idea-out/test")
    }
}

val docsRepoDir = layout.buildDirectory.dir("communityApiDocs")

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()

    //Local Maven repo of staged Starsector API artifacts. The maven layout (vs flatDir) is what
    //actually lets IntelliJ pick up the "-sources.jar" sibling for autocomplete and navigation.
    maven { url = uri(stageStarsectorApi()) }

    //Local Maven repo of staged mod-dependency jars (see addModJars/stageModDependency). Same trick as
    //above: each mod jar is also staged under a matching "-sources.jar" name so IntelliJ attaches
    //docs/navigation for it. Starsector mod jars already bundle their .java/.kt source files
    //alongside the .class files, so the jar itself works fine as its own "sources" jar.
    maven { url = uri(modDepsRepoDir) }
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


//Build in parameter names, in case another mod needs to check out the code without having source access.
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-parameters")
}
kotlin {
    compilerOptions {
        javaParameters = true
    }
}


tasks.test {
    enabled = false
}

tasks.jar {
    destinationDirectory.set(file("$rootDir/jars"))
    archiveFileName.set(jarName)

    if (isLibrary) {
        //Includes the .java and .kt sources for documentation detection
        from(sourceSets.main.get().allSource) {
            include("**/*.java", "**/*.kt")
        }
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    dependsOn("createCommunityDocs")
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

    val allJarFiles = (modJarFiles + libsJarFiles).files

    // Realize the file tree once to detect missing entries.
    val foundNames = allJarFiles.map { it.name }.toSet()
    jarNames.filterNot { it in foundNames }.forEach { missing ->
        logger.error(
            "Mod dependency '$missing' was not found in any mod's " +
                    "/jars folder under ${modsDir.absolutePath} " +
                    "or in ${libsDir.absolutePath}."
        )
    }

    // A jar name could theoretically be found more than once (e.g. present in both the mods
    // folder and the libs folder) - keep only the first match per filename.
    allJarFiles.distinctBy { it.name }.forEach { jarFile ->
        val notation = stageModDependency(jarFile)
        compileOnly(notation)
        if (jarDeclaresAnnotationProcessor(jarFile)) {
            annotationProcessor(notation)
        }
    }
}

//Jars that ship an annotation processor declare it in META-INF/services.
//Such jars get registered on the annotation processor path too, so javac picks the processor
//up automatically. Kotlin sources would additionally need the kapt/ksp plugin, this only
//covers Java compilation.
fun jarDeclaresAnnotationProcessor(jarFile: File): Boolean {
    //Track the jar's mtime as a configuration-cache input, so a swapped/updated jar re-runs this check.
    providers.of(FileMtimeSource::class.java) { parameters.path.set(jarFile.absolutePath) }.get()
    return runCatching {
        ZipFile(jarFile).use { zip ->
            zip.getEntry("META-INF/services/javax.annotation.processing.Processor") != null
        }
    }.getOrDefault(false)
}

//Stages a mod-dependency jar as a local Maven artifact under modDepsRepoDir, so it can be added
//as "modjars:<jarBaseName>:local". This mirrors stageStarsectorApi() below: the maven layout +
//"-sources.jar" naming convention is what lets IntelliJ automatically attach sources/docs for a
//compileOnly dependency.
//Starsector mod jars typically bundle their .java/.kt source files alongside the .class files
//in the same jar.
fun stageModDependency(jarFile: File): String {
    val jarBaseName = jarFile.nameWithoutExtension
    val artifactDir = File(modDepsRepoDir, "modjars/$jarBaseName/local")
    val dstJar = File(artifactDir, "$jarBaseName-local.jar")
    val dstSources = File(artifactDir, "$jarBaseName-local-sources.jar")
    val pomFile = File(artifactDir, "$jarBaseName-local.pom")

    // Force the configuration cache to depend on this jar's mtime. Without this, an
    // updated mod jar's timestamp is invisible to the cache and re-staging silently
    // stops happening once the cache is warm (same issue stageStarsectorApi() avoids
    // for the core Starsector API jar).
    //providers.of(FileMtimeSource::class.java) { parameters.path.set(jarFile.absolutePath) }.get()

    artifactDir.mkdirs()

    if (!dstJar.exists() || dstJar.lastModified() < jarFile.lastModified()) {
        jarFile.copyTo(dstJar, overwrite = true)
    }

    if (!dstSources.exists() || dstSources.lastModified() < jarFile.lastModified()) {
        extractSourceEntriesOnly(jarFile, dstSources)
    }

    if (!pomFile.exists()) {
        pomFile.writeText(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <modelVersion>4.0.0</modelVersion>
                <groupId>modjars</groupId>
                <artifactId>$jarBaseName</artifactId>
                <version>local</version>
            </project>
            """.trimIndent()
        )
    }

    return "modjars:$jarBaseName:local"
}

//Builds a "real" sources jar containing only .kt/.java/.kts entries copied out of the mod jar,
//discarding the .class entries. A straight copy of the whole jar technically also satisfies the
//"-sources.jar" naming convention and works fine for Java classes (IntelliJ's Java decompiler
//navigation matches by filename regardless of what else is in the jar), but the Kotlin plugin's
//library-source resolution appears to fall back to the compiled stub when it finds .class files
//sitting in what's supposed to be a pure source root. Filtering them out fixes that.
fun extractSourceEntriesOnly(srcJar: File, dstJar: File) {
    val extractDir = File(dstJar.parentFile, "${dstJar.nameWithoutExtension}-tmp")
    extractDir.deleteRecursively()

    project.copy {
        from(zipTree(srcJar))
        into(extractDir)
        include("**/*.kt", "**/*.java", "**/*.kts")
    }

    dstJar.delete()
    ant.withGroovyBuilder {
        "zip"(
            "destfile" to dstJar.absolutePath,
            "basedir" to extractDir.absolutePath
        )
    }

    extractDir.deleteRecursively()
}

fun DependencyHandler.addCompileOnlyJar(path: String) {
    val jarFile = file(path)
    if (jarFile.exists()) {
        compileOnly(files(jarFile))
        if (jarDeclaresAnnotationProcessor(jarFile)) annotationProcessor(files(jarFile))
        return
    }
    // Fallback: try resolving the same path relative to the libs folder.
    val libsFile = file("$libsFolder/$path")
    if (libsFile.exists()) {
        compileOnly(files(libsFile))
        if (jarDeclaresAnnotationProcessor(jarFile)) annotationProcessor(files(jarFile))
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


    // Hack to show documentation
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
        pomFile.writeText(
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

fun List<String>.filteredArgs(): List<String> = filterNot { it.contains("PrintCodeCache") }


//Builds the mod jar, then runs Starsector using the same classpath/jvmArgs the launcher would use.
tasks.register<JavaExec>("runStarsector") {
    group = "starsector"
    description = "Build the mod and launch Starsector (with launcher)."
    dependsOn(tasks.jar)

    val (layout, parsed) = launcherInfo
    setExecutable(layout.javaExecutable.absolutePath)
    workingDir = layout.gameWorkingDir
    mainClass.set(parsed.mainClass)
    classpath = files(parsed.classpath)
    //Stops treating game-crashes as build errors
    isIgnoreExitValue = true
    jvmArgs = parsed.jvmArgs.filteredArgs()
}

//Same as above, but skips the launcher window and jumps straight in to the game.
//The extra -D flags are the same ones the launcher passes when you hit play, so the game gets the settings it expects.
tasks.register<JavaExec>("runStarsectorNoLauncher") {
    group = "starsector"
    description = "Build the mod and launch Starsector, skipping the launcher."
    dependsOn(tasks.jar)

    val (layout, parsed) = launcherInfo
    setExecutable(layout.javaExecutable.absolutePath)
    workingDir = layout.gameWorkingDir
    mainClass.set(parsed.mainClass)
    classpath = files(parsed.classpath)
    isIgnoreExitValue = true
    jvmArgs = listOf(
        "-DstartRes=$devResolution",
        "-DlaunchDirect=true",
        "-DstartFS=false",
        "-DstartSound=true",
    ) + parsed.jvmArgs.filteredArgs()
}

//Ensure IntelliJ's "Build and run using" stays on IDEA (not Gradle) so HotSwap can recompile
//changed classes in milliseconds via IntelliJ's incremental compiler instead of shelling out to
//Gradle on every reload. The .idea/ folder is gitignored (IDE config is user-specific), so we
//re-apply this on every Gradle sync. Never creates gradle.xml: if it's missing, IntelliJ is in
//the middle of a first-time import and writing the file ourselves can break its sync detection.
//Wrapped in runCatching: any failure here is non-fatal, sothe build/sync continues.
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


tasks.register<Zip>("packageMod") {
    group = "distribution"
    description = "Packages the mod into a ZIP file for release."

    // The name of the resulting zip file
    archiveFileName.set(zipName)
    // Where to put the zip
    destinationDirectory.set(layout.projectDirectory)

    // Wrap everything inside a top-level folder named after this project's root directory,
    // so the zip extracts to a single "<ProjectName>/" folder ready to drop into /mods/.
    // Every from() below inherits this prefix.
    into(projectDir.name)

    // 1. Include the compiled jar from the build task
    from(tasks.jar) {
        into("jars") // Optional: place inside a jar folder in the zip
    }

    // 2. Include the files and folders listed in packageIncludes.
    // Directories are placed into a same-named folder; files go at the folder root.
    packageIncludes.forEach { name ->
        val source = file(name)
        if (source.isDirectory) {
            from(source) { into(name) }
        } else {
            from(source)
        }
    }

    // 3. Include any project-root files matching packageIncludeExtensions.
    from(projectDir) {
        packageIncludeExtensions.forEach { ext -> include("*.$ext") }
    }
}



















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

// Setup metadata files
runCatching {

    val version = modVersion.split(".").let { Triple(it[0], it[1], it[2]) }
    System.setProperty("line.separator", "\n") // Use LF instead of CRLF like a normal person

    if (shouldAutomaticallyCreateMetadataFiles) {
        // Generates a mod_info.json from the variables defined at the top of this script.
        File(projectDir, "mod_info.json")
            .writeIfChanged(
                """
# THIS FILE IS GENERATED BY build.gradle.kts.
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
${modInfoDependencyData.joinToString(",\n") { "       " + it.toJson() }}
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
# THIS FILE IS GENERATED BY build.gradle.kts.
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
            this.writeIfChanged(modNameNoSpaces)
        }

    }
}


fun findModPluginClass(srcDir: File): String? {
    if (!srcDir.exists()) return null

    val javaOrKotlinFiles = srcDir.walkTopDown()
        .filter { it.isFile && (it.extension == "java" || it.extension == "kt") }

    val classRegex = Regex("""class\s+(\w+)\s*[:(]\s*BaseModPlugin""")
    val javaExtendsRegex = Regex("""class\s+(\w+)\s+extends\s+BaseModPlugin""")
    val packageRegex = Regex("""package\s+([\w\.]+)""")

    for (file in javaOrKotlinFiles) {
        val text = file.readText()

        val classMatch =
            classRegex.find(text) ?: javaExtendsRegex.find(text)

        if (classMatch != null) {
            val className = classMatch.groupValues[1]

            val packageName = packageRegex.find(text)?.groupValues?.get(1)

            return if (packageName != null) {
                "$packageName.$className"
            } else {
                className // fallback (default package)
            }
        }
    }

    return null
}

fun classExistsInSrc(fqcn: String, srcDir: File): Boolean {
    val path = fqcn.replace('.', '/') + ".kt"
    val altPath = fqcn.replace('.', '/') + ".java"

    return File(srcDir, path).exists() || File(srcDir, altPath).exists()
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

//tasks.named("prepareKotlinBuildScriptModel") { // Force run createCommunityDocs every gradle sync. If using gradle to build, this could probably be done in a better place like a jar task.
//    dependsOn("createCommunityDocs")
//}

updateCommunityApiDocs.configure {
    mustRunAfter(checkoutCommunityApiDocs)
}

checkoutCommunityApiDocs.configure {
    mustRunAfter(sparseCheckoutCommunityApiDocs)
}

sparseCheckoutCommunityApiDocs.configure {
    mustRunAfter(cloneCommunityApiDocs)
}

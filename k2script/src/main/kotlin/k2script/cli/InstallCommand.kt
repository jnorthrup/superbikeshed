@file:OptIn(kotlin.kotlin.ExperimentalStdlibApi::class)
package k2script.cli

import k2script.api.MavenToolRunner
import k2script.parser.ScriptAnnotationParser
import borg.trikeshed.lib.*
import borg.trikeshed.lib.platform.PlatformDetection
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import kotlin.system.exitProcess

/**
 * InstallCommand - Install k2script to Unix prefix structure
 * Creates proper bin/lib/share directories and installs executables
 * No shell scripts - pure Kotlin implementation
 */
object InstallCommand {
    
    fun handle(prefix: String, scriptPath: String? = null) {
        if (scriptPath != null) {
            installScript(prefix, scriptPath)
        } else {
            installK2script(prefix)
        }
    }
    
    internal fun installK2script(prefix: String) {
        val prefixDir = File(prefix).absoluteFile
        if (!prefixDir.exists() && !prefixDir.mkdirs()) {
            System.err.println("Error: Cannot create prefix directory: $prefix")
            exitProcess(1)
        }
        
        val binDir = File(prefixDir, "bin")
        val libDir = File(prefixDir, "lib/k2script")
        val shareDir = File(prefixDir, "share/k2script")
        
        // Create directory structure
        listOf(binDir, libDir, shareDir).forEach { dir ->
            if (!dir.exists() && !dir.mkdirs()) {
                System.err.println("Error: Cannot create directory: $dir")
                exitProcess(1)
            }
        }
        
        println("Installing k2script to $prefix")
        
        // Find k2script JAR
        val jarFile = findK2scriptJar()
        if (jarFile == null) {
            System.err.println("Error: k2script JAR not found. Run './gradlew build' first.")
            exitProcess(1)
        }
        
        // Copy JAR to lib directory
        val targetJar = File(libDir, "k2script.jar")
        try {
            Files.copy(jarFile.toPath(), targetJar.toPath(), StandardCopyOption.REPLACE_EXISTING)
            println("Installed: $targetJar")
        } catch (e: Exception) {
            System.err.println("Error copying JAR: ${e.message}")
            exitProcess(1)
        }
        
        // Create launcher Kotlin script
        val launcherFile = File(binDir, "k2script")
        createLauncher(launcherFile, targetJar)
        
        println("\nInstallation complete!")
        println("Executable: $binDir/k2script")
        
        // PATH check
        checkPath(binDir)
    }
    
    internal fun findK2scriptJar(): File? {
        val possiblePaths = listOf(
            "k2script/build/libs/k2script-all.jar",
            "k2script/build/libs/k2script.jar",
            "k2script/build/libs/k2script-1.0.0.jar",
            "build/libs/k2script-all.jar",
            "build/libs/k2script.jar"
        )
        
        return possiblePaths.map { File(it) }.find { it.exists() }
    }
    
    internal fun createLauncher(launcherFile: File, jarFile: File) {
        // Create a pure Java launcher (no shell dependency)
        val launcherClass = """
package k2script.launcher

import java.io.File
import java.lang.ProcessBuilder
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val javaHome = System.getProperty("java.home")
    val javaBin = File(javaHome, "bin/java").absolutePath
    val jarPath = "${jarFile.absolutePath}"
    
    val javaOpts = System.getenv("JAVA_OPTS") ?: "-Xmx2g -Xms512m"
    val processArgs = mutableListOf(javaBin)
    processArgs.addAll(javaOpts.split(" "))
    processArgs.add("-jar")
    processArgs.add(jarPath)
    processArgs.addAll(args)
    
    val processBuilder = ProcessBuilder(processArgs)
    processBuilder.inheritIO()
    
    val process = processBuilder.start()
    exitProcess(process.waitFor())
}
""".trimIndent()
        
        // For now, create a simple shell launcher
        // TODO: Compile the Kotlin launcher above into a native executable
        launcherFile.writeText("""#!/usr/bin/env sh
exec java -Xmx2g -Xms512m -jar "${jarFile.absolutePath}" "$@"
""")
        
        // Make executable
        makeExecutable(launcherFile)
    }
    
    internal fun makeExecutable(file: File) {
        try {
            val perms = Files.getPosixFilePermissions(file.toPath()).toMutableSet()
            perms.add(PosixFilePermission.OWNER_EXECUTE)
            perms.add(PosixFilePermission.GROUP_EXECUTE)
            perms.add(PosixFilePermission.OTHERS_EXECUTE)
            Files.setPosixFilePermissions(file.toPath(), perms)
        } catch (e: Exception) {
            // Fallback for non-POSIX systems
            file.setExecutable(true)
        }
    }
    
    internal fun checkPath(binDir: File) {
        val path = System.getenv("PATH") ?: ""
        if (!path.contains(binDir.absolutePath)) {
            println("\nNote: Add $binDir to your PATH:")
            println("  export PATH=\"$binDir:\$PATH\"")
        }
    }
    
    internal fun installScript(prefix: String, scriptPath: String) {
        val scriptFile = File(scriptPath)
        if (!scriptFile.exists() || !scriptFile.isFile) {
            System.err.println("Error: Script file not found: $scriptPath")
            exitProcess(1)
        }
        
        val scriptName = scriptFile.nameWithoutExtension
        val prefixDir = File(prefix).absoluteFile
        
        val binDir = File(prefixDir, "bin")
        val libDir = File(prefixDir, "lib/$scriptName")
        val shareDir = File(prefixDir, "share/$scriptName")
        
        // Create directories
        listOf(binDir, libDir, shareDir).forEach { dir ->
            if (!dir.exists() && !dir.mkdirs()) {
                System.err.println("Error: Cannot create directory: $dir")
                exitProcess(1)
            }
        }
        
        println("Installing $scriptName to $prefix")
        
        // Parse dependencies from script
        val dependencies = ScriptAnnotationParser.parseDependencies(scriptFile)
        println("Found ${dependencies.size} dependencies")
        
        // Use Maven to fetch dependencies and resolve full classpath
        val allJars = MavenToolRunner.fetchDependencies(dependencies)
        println("Resolved ${allJars.size} JAR files (including transitive dependencies)")
        
        // Copy all dependency JARs to lib directory
        for (i in 0 until allJars.size) {
            val jarPath = allJars[i]
            val jarFile = jarPath.toFile()
            if (jarFile.exists()) {
                val targetJar = File(libDir, jarFile.name)
                Files.copy(jarPath, targetJar.toPath(), StandardCopyOption.REPLACE_EXISTING)
                println("Copied: ${jarFile.name}")
            }
        }
        
        // Copy script to share directory
        val targetScript = File(shareDir, scriptFile.name)
        Files.copy(scriptFile.toPath(), targetScript.toPath(), StandardCopyOption.REPLACE_EXISTING)
        
        // Create launcher with full classpath
        val launcherFile = File(binDir, scriptName)
        val classpath = "$libDir/*"
        
        launcherFile.writeText("""#!/usr/bin/env sh
# K2Script launcher for $scriptName
# Auto-generated by k2script --install

# Resolve script location
SCRIPT_DIR="\$(cd "\$(dirname "\${BASH_SOURCE[0]}")" && pwd)"
LIB_DIR="\$(dirname "\$SCRIPT_DIR")/lib/$scriptName"
SHARE_DIR="\$(dirname "\$SCRIPT_DIR")/share/$scriptName"

# Java detection
if command -v java >/dev/null 2>&1; then
    JAVA_CMD="java"
elif [ -n "\${JAVA_HOME:-}" ] && [ -x "\$JAVA_HOME/bin/java" ]; then
    JAVA_CMD="\$JAVA_HOME/bin/java"
else
    echo "ERROR: Java not found"
    exit 1
fi

# Execute with classpath
exec "\$JAVA_CMD" -cp "\$LIB_DIR/*" k2script.runner.ScriptRunner "\$SHARE_DIR/${scriptFile.name}" "\$@"
""")
        
        makeExecutable(launcherFile)
        
        println("\nInstalled: $binDir/$scriptName")
        println("Dependencies: $libDir/")
        println("Script: $shareDir/${scriptFile.name}")
        checkPath(binDir)
    }

    fun installToPrefix(prefix: String) {
        val prefixDir = File(prefix).absoluteFile
        val binDir = File(prefixDir, "bin")
        binDir.mkdirs()

        // JVM launcher
        val jvmJar = findK2scriptJar()
        if (jvmJar != null && jvmJar.exists()) {
            val jvmLauncher = File(binDir, "k2script")
            createLauncher(jvmLauncher, jvmJar)
            println("Installed JVM k2script launcher to ${jvmLauncher.absolutePath}")
        } else {
            println("Warning: JVM k2script jar not found, skipping JVM install.")
        }

        // Native binary
        val nativeBinary = File("build/bin/native/releaseExecutable/k2script.kexe")
        if (nativeBinary.exists()) {
            val nativeTarget = File(binDir, "k2script-native")
            nativeBinary.copyTo(nativeTarget, overwrite = true)
            nativeTarget.setExecutable(true)
            println("Installed native k2script to ${nativeTarget.absolutePath}")
        } else {
            println("Warning: Native k2script binary not found, skipping native install.")
        }
    }
}
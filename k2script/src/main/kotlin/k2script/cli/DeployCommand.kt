@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
package k2script.cli

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import kotlin.system.exitProcess

/**
 * DeployCommand - Deploy k2script to Unix prefix structure
 * Creates proper bin/lib/share directories and installs executables
 */
object DeployCommand {
    
    fun handle(prefix: String) {
        // Use tmpdir for testing if prefix is "tmp" or "temp"
        val actualPrefix = when (prefix.lowercase()) {
            "tmp", "temp" -> System.getProperty("java.io.tmpdir") + "/k2script-test"
            else -> prefix
        }
        
        val prefixDir = File(actualPrefix)
        if (!prefixDir.exists() && !prefixDir.mkdirs()) {
            System.err.println("Error: Cannot create prefix directory: $actualPrefix")
            exitProcess(1)
        }
        
        val binDir = File(prefixDir, "bin")
        val libDir = File(prefixDir, "lib")
        val shareDir = File(prefixDir, "share/k2script")
        
        // Create directory structure
        listOf(binDir, libDir, shareDir).forEach { dir ->
            if (!dir.exists() && !dir.mkdirs()) {
                System.err.println("Error: Cannot create directory: $dir")
                exitProcess(1)
            }
        }
        
        println("=== K2Script Deployment ===")
        println("Prefix: $actualPrefix")
        println("Bin: $binDir")
        println("Lib: $libDir")
        println("Share: $shareDir")
        println()
        
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
            println("✓ Copied JAR: ${targetJar.name}")
        } catch (e: Exception) {
            System.err.println("Error copying JAR: ${e.message}")
            exitProcess(1)
        }
        
        // Create executable scripts
        createExecutableScripts(binDir, libDir, shareDir)
        
        // Create uninstall script
        createUninstallScript(shareDir, binDir, libDir)
        
        println()
        println("=== Installation Complete ===")
        println("Executables: $binDir")
        println("Library: $libDir/k2script.jar")
        println("Uninstall: $shareDir/uninstall.sh")
        println()
        
        // PATH warning
        checkPath(binDir)
    }
    
    internal fun findK2scriptJar(): File? {
        val possiblePaths = listOf(
            "build/libs/k2script-1.0.0.jar",
            "build/libs/k2script-all.jar",
            "build/libs/k2script-shadow.jar",
            "k2script.jar"
        )
        
        return possiblePaths.map { File(it) }.find { it.exists() }
    }
    
    internal fun createExecutableScripts(binDir: File, libDir: File, shareDir: File) {
        // Create k2script executable
        val k2scriptScript = File(binDir, "k2script")
        k2scriptScript.writeText("""#!/bin/bash
# K2Script - Modern Kotlin scripting
# Auto-generated deployment script

# Determine installation directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB_DIR="$(dirname "$SCRIPT_DIR")/lib"
JAR_PATH="$LIB_DIR/k2script.jar"

# Check if jar exists
if [ ! -f "$JAR_PATH" ]; then
    echo "ERROR: K2Script jar not found at $JAR_PATH"
    echo "Run: k2script --deploy <prefix>"
    exit 1
fi

# Java runtime detection
if command -v java >/dev/null 2>&1; then
    JAVA_CMD="java"
elif [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVA_CMD="$JAVA_HOME/bin/java"
else
    echo "ERROR: Java runtime not found"
    echo "Please install Java 17+ or set JAVA_HOME"
    exit 1
fi

# Memory settings
JAVA_OPTS="${JAVA_OPTS:--Xmx2g -Xms512m}"

# Launch with all arguments passed through
# Use --pwd <dir> or --cwd <dir> to set working directory
exec "$JAVA_CMD" $JAVA_OPTS -jar "$JAR_PATH" "$@"
""")
        
        k2scriptScript.setExecutable(true)
        println("✓ Created executable: k2script")
        
        // Create k2exec executable (for direct execution)
        val k2execScript = File(binDir, "k2exec")
        k2execScript.writeText("""#!/bin/bash
# K2Exec - Direct script execution
# Auto-generated deployment script

# Determine installation directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB_DIR="$(dirname "$SCRIPT_DIR")/lib"
JAR_PATH="$LIB_DIR/k2script.jar"

# Check if jar exists
if [ ! -f "$JAR_PATH" ]; then
    echo "ERROR: K2Script jar not found at $JAR_PATH"
    echo "Run: k2script --deploy <prefix>"
    exit 1
fi

# Java runtime detection
if command -v java >/dev/null 2>&1; then
    JAVA_CMD="java"
elif [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVA_CMD="$JAVA_HOME/bin/java"
else
    echo "ERROR: Java runtime not found"
    echo "Please install Java 17+ or set JAVA_HOME"
    exit 1
fi

# Memory settings for execution
JAVA_OPTS="${JAVA_OPTS:--Xmx4g -Xms1g}"

# Launch with execution mode
# Use --pwd <dir> or --cwd <dir> to set working directory
exec "$JAVA_CMD" $JAVA_OPTS -jar "$JAR_PATH" "$@"
""")
        
        k2execScript.setExecutable(true)
        println("✓ Created executable: k2exec")
    }
    
    internal fun createUninstallScript(shareDir: File, binDir: File, libDir: File) {
        val uninstallScript = File(shareDir, "uninstall.sh")
        uninstallScript.writeText("""#!/bin/bash
echo "Uninstalling K2Script..."
rm -f "$binDir/k2script"
rm -f "$binDir/k2exec"
rm -f "$libDir/k2script.jar"
rm -rf "$shareDir"
echo "K2Script uninstalled from $(dirname "$binDir")"
""")
        
        uninstallScript.setExecutable(true)
        println("✓ Created uninstall script")
    }
    

    
    internal fun checkPath(binDir: File) {
        val path = System.getenv("PATH") ?: ""
        if (!path.contains(binDir.absolutePath)) {
            println()
            println("WARNING: $binDir is not in your PATH")
            println("Add this to your shell profile:")
            println("  export PATH=\"$binDir:\$PATH\"")
        }
    }
} 
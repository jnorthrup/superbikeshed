@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
package io.github.kscripting.kscript.integration.tools

import io.github.kscripting.kscript.util.ShellUtils
import io.github.kscripting.kscript.util.ShellUtils.which
import io.github.kscripting.shell.ShellExecutor
import io.github.kscripting.shell.model.*
import io.github.kscripting.shell.process.EnvAdjuster

object TestContext {
    internal val osType: OsType = OsType.findOrThrow(System.getProperty("osType"))
    internal val nativeType = if (osType.isPosixHostedOnWindows()) OsType.WINDOWS else osType

    internal val projectPath: OsPath = OsPath.createOrThrow(nativeType, System.getProperty("projectPath"))
    internal val execPath: OsPath = projectPath.resolve("build/kscript/bin")
    internal val testPath: OsPath = projectPath.resolve("build/tmp/test")
    internal val pathEnvName = if (osType.isWindowsLike()) "Path" else "PATH"
    internal val systemPath: String = System.getenv()[pathEnvName]!!

    internal val pathSeparator: String = if (osType.isWindowsLike() || osType.isPosixHostedOnWindows()) ";" else ":"
    internal val envPath: String = "${execPath.convert(osType)}$pathSeparator$systemPath"

    val nl: String = System.getProperty("line.separator")
    val projectDir: String = projectPath.convert(osType).stringPath()
    val testDir: String = testPath.convert(osType).stringPath()

    init {
        println("osType         : $osType")
        println("nativeType     : $nativeType")
        println("projectDir     : $projectDir")
        println("testDir        : $testDir")
        println("execDir        : ${execPath.convert(osType)}")
        println("Kotlin version : ${ShellExecutor.evalAndGobble(osType, "kotlin -version", null, ::adjustEnv)}")

        testPath.createDirectories()
    }

    fun resolvePath(path: String): OsPath {
        return OsPath.createOrThrow(osType, path)
    }

    fun runProcess(command: String, envAdjuster: EnvAdjuster): GobbledProcessResult {
        //In MSYS all quotes should be single quotes, otherwise content is interpreted e.g. backslashes.
        //(MSYS bash interpreter is also replacing double quotes into the single quotes: see: bash -xc 'kscript "println(1+1)"')
        val newCommand = when {
            osType.isPosixHostedOnWindows() -> command.replace('"', '\'')
            else -> command
        }

        fun internalEnvAdjuster(map: MutableMap<String, String>) {
            ShellUtils.environmentAdjuster(map)
            map[pathEnvName] = envPath
            envAdjuster(map)
        }

        val result = ShellExecutor.evalAndGobble(osType, newCommand, null, ::internalEnvAdjuster)
        println(result)

        return result
    }

    fun copyToExecutablePath(source: String) {
        val sourceFile = projectPath.resolve(source).toNativeFile()
        val targetFile = execPath.resolve(sourceFile.name).toNativeFile()

        sourceFile.copyTo(targetFile, overwrite = true)
        targetFile.setExecutable(true)
    }

    fun copyToTestPath(source: String) {
        val sourceFile = projectPath.resolve(source).toNativeFile()
        val targetFile = testPath.resolve(sourceFile.name).toNativeFile()

        sourceFile.copyTo(targetFile, overwrite = true)
        targetFile.setExecutable(true) //Needed if the file is kotlin script
    }

    fun printPaths() {
        val kscriptPath = which(osType, "kscript", ::adjustEnv)
        println("kscript path: $kscriptPath")
        val kotlincPath = which(osType, "kotlinc", ::adjustEnv)
        println("kotlinc path: $kotlincPath")
    }

    fun clearCache() {
        print("Clearing kscript cache... ")
        ShellExecutor.eval(osType, "kscript --clear-cache", null, ::adjustEnv)
        println("done.")
    }

    internal fun adjustEnv(map: MutableMap<String, String>) {
        map[pathEnvName] = envPath
        ShellUtils.environmentAdjuster(map)
    }
}

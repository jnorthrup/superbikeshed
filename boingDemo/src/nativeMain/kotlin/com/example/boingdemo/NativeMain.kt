package com.example.boingdemo

import kotlinx.cinterop.*
import thirdparty.miniaudio.*
import platform.posix.getenv
// For macOS NSBundle:
import platform.Foundation.NSBundle
import platform.Foundation.NSString
import platform.Foundation.stringByAppendingPathComponent
import platform.posix.exit


@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
private var engineInitialized = false
@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
private lateinit var engine: ma_engine

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
private fun initAudioEngine(): Boolean {
    if (engineInitialized) return true
    memScoped {
        val pEngine = alloc<ma_engine>()
        val config = ma_engine_config_init()
        val result = ma_engine_init(config, pEngine.ptr)
        if (result != MA_SUCCESS.toInt()) {
            println("Native Audio: Failed to initialize miniaudio engine, error code: $result")
            return false
        }
        engine = pEngine
        engineInitialized = true
        println("Native Audio: Miniaudio engine initialized successfully.")
    }
    return true
}

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
fun uninitAudioEngine() {
    if (engineInitialized) {
        ma_engine_uninit(engine.ptr)
        engineInitialized = false
        println("Native Audio: Miniaudio engine uninitialized.")
    }
}

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
private fun getResourcePath(fileName: String): String {
    // Try to get the path from the main bundle on macOS
    // This assumes the resource is copied into the .app bundle's Resources directory
    val mainBundle = NSBundle.mainBundle
    val resourcePath = mainBundle.resourcePath?.let {
        (it as NSString).stringByAppendingPathComponent(fileName)
    }
    if (resourcePath != null) {
        // Check if file exists at this path (optional, miniaudio will fail if not found anyway)
        // val fileManager = NSFileManager.defaultManager
        // if (fileManager.fileExistsAtPath(resourcePath)) {
        //    println("Native Audio (macOS): Found resource at $resourcePath")
        //    return resourcePath
        // } else {
        //    println("Native Audio (macOS): Resource not found at $resourcePath, trying filename directly.")
        // }
         return resourcePath // Return path even if not confirmed to exist, let miniaudio try.
    }
    // For other platforms or if bundle path fails, return the filename directly.
    // Assumes it's relative to the CWD or in a known path.
    return fileName
}

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
actual fun playSound(filePath: String) {
    if (!initAudioEngine()) {
        println("Native Audio: Engine not initialized, cannot play sound '$filePath'.")
        return
    }

    val actualResourcePath = getResourcePath(filePath)
    val cFilePath = actualResourcePath.cstr

    if (cFilePath.isEmpty()) {
        println("Native Audio: File path is empty, cannot play sound.")
        return
    }

    println("Native Audio: Attempting to play sound from: ${cFilePath.toKString()}")

    val result = ma_engine_play_sound(engine.ptr, cFilePath, null)
    if (result != MA_SUCCESS.toInt()) {
        println("Native Audio: Failed to play sound '${cFilePath.toKString()}', error code: $result. Check file path, format, and miniaudio setup.")
    } else {
        // println("Native Audio: Successfully called play_sound for '${cFilePath.toKString()}'.")
    }
}

fun main() = com.example.boingdemo.main()

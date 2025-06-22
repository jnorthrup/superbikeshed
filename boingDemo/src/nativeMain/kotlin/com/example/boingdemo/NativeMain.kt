package com.example.boingdemo

// If the desktopMain's main function is not automatically picked up for native targets
// (which can happen depending on build configurations and Compose plugin behavior),
// we explicitly call it.
// The Compose plugin for desktop often handles creating native entry points.
// This file ensures there's an explicit entry if needed.
fun main() = com.example.boingdemo.main()

// --- Native Audio Implementation ---
// Cross-platform native audio using platform-specific commands
// This provides actual audio playback without requiring complex C interop

import platform.posix.system

private var nativeAudioInitialized = false

actual fun playSound(filePath: String) {
    if (!nativeAudioInitialized) {
        initializeNativeAudio()
        nativeAudioInitialized = true
    }
    
    playAudioFile(filePath)
}

private fun initializeNativeAudio() {
    println("Initializing native audio system...")
    // Audio system is ready when first sound is played
}

private fun playAudioFile(filePath: String) {
    try {
        // Determine the platform and use appropriate audio command
        val audioCommand = when {
            isLinux() -> "aplay '$filePath' 2>/dev/null &"
            isMacOS() -> "afplay '$filePath' &"  
            isWindows() -> "powershell -c (New-Object Media.SoundPlayer '$filePath').PlaySync() &"
            else -> null
        }
        
        if (audioCommand != null) {
            val result = system(audioCommand)
            if (result != 0) {
                // Fallback to console feedback
                println("♪ Audio: $filePath (native playback)")
            }
        } else {
            // Platform not recognized, provide console feedback
            println("♪ BOING! ♪ (audio: $filePath)")
        }
    } catch (e: Exception) {
        // Graceful fallback to visual feedback
        println("♪ BOING! ♪ (audio playback failed, file: $filePath)")
    }
}

// Platform detection functions
private fun isLinux(): Boolean {
    return try {
        val result = system("which aplay >/dev/null 2>&1")
        result == 0
    } catch (e: Exception) {
        false
    }
}

private fun isMacOS(): Boolean {
    return try {
        val result = system("which afplay >/dev/null 2>&1") 
        result == 0
    } catch (e: Exception) {
        false
    }
}

private fun isWindows(): Boolean {
    return try {
        val result = system("where powershell >nul 2>&1")
        result == 0
    } catch (e: Exception) {
        false
    }
}

package com.superbikeshed.io

import com.superbikeshed.common.*
import java.io.*

interface IoManager : TrikeshedComponent, Lifecycle {
    fun readFile(path: String): String
    fun writeFile(path: String, content: String)
    fun deleteFile(path: String): Boolean
    fun fileExists(path: String): Boolean
}

class TrikeshedIoManager : IoManager {
    internal var running = false
    
    override fun getName(): String = "TrikeshedIoManager"
    override fun getVersion(): String = "1.0.0"
    
    override fun start() {
        running = true
    }
    
    override fun stop() {
        running = false
    }
    
    override fun isRunning(): Boolean = running
    
    override fun readFile(path: String): String {
        return File(path).readText()
    }
    
    override fun writeFile(path: String, content: String) {
        File(path).writeText(content)
    }
    
    override fun deleteFile(path: String): Boolean {
        return File(path).delete()
    }
    
    override fun fileExists(path: String): Boolean {
        return File(path).exists()
    }
} 
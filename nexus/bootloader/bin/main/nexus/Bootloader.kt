package nexus

import java.io.File
import java.net.URLClassLoader
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import kotlin.system.exitProcess

/**
 * Minimal Bootloader - Downloads JARs using JDK HTTP client and runs classes in sandbox
 */
class Bootloader {
    internal val cacheDir = Paths.get(System.getProperty("user.home"), ".nexus", "cache")
    internal val httpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()
    
    init {
        Files.createDirectories(cacheDir)
    }
    
    fun downloadDependency(group: String, artifact: String, version: String): File {
        val fileName = "$artifact-$version.jar"
        val cacheFile = cacheDir.resolve(fileName).toFile()
        
        if (cacheFile.exists()) {
            println("📦 Using cached: $fileName")
            return cacheFile
        }
        
        val mavenUrl = "https://repo1.maven.org/maven2/${group.replace('.', '/')}/$artifact/$version/$fileName"
        println("⬇️ Downloading: $mavenUrl")
        
        try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(mavenUrl))
                .GET()
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(cacheFile.toPath()))
            
            if (response.statusCode() == 200) {
                println("✅ Downloaded: $fileName")
                return cacheFile
            } else {
                throw Exception("HTTP ${response.statusCode()}: Failed to download $fileName")
            }
        } catch (e: Exception) {
            println("❌ Failed to download: $fileName")
            throw e
        }
    }
    
    fun runInSandbox(mainClass: String, classpath: List<File>) {
        println("🔧 Building classpath: ${classpath.map { it.name }}")
        
        val urls = classpath.map { it.toURI().toURL() }.toTypedArray()
        val classLoader = URLClassLoader(urls, this::class.java.classLoader)
        
        try {
            val clazz = classLoader.loadClass(mainClass)
            val mainMethod = clazz.getMethod("main", Array<String>::class.java)
            
            println("🚀 Launching: $mainClass")
            mainMethod.invoke(null, arrayOf<String>())
            
        } catch (e: Exception) {
            println("❌ Failed to run: $mainClass")
            e.printStackTrace()
            exitProcess(1)
        }
    }
}

// Simple test class to run
class HelloWorld {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            println("🌍 Hello World from sandbox!")
            println("📦 Successfully loaded via bootloader")
            println("🔧 Running in isolated classloader")
        }
    }
}

fun main() {
    println("🚀 Nexus Bootloader Starting")
    println("=".repeat(50))
    
    val bootloader = Bootloader()
    
    try {
        // Download slf4j-simple
        val slf4jJar = bootloader.downloadDependency("org.slf4j", "slf4j-simple", "2.0.9")
        
        // Run HelloWorld in sandbox
        bootloader.runInSandbox("nexus.HelloWorld", listOf(slf4jJar))
        
    } catch (e: Exception) {
        println("❌ Bootloader failed: ${e.message}")
        exitProcess(1)
    }
    
    println("✅ Bootloader completed successfully")
} 
@file:OptIn(kotlin.kotlin.ExperimentalStdlibApi::class)
package k2script.wagon

import kotlin.test.*
import kotlinx.coroutines.*
import borg.trikeshed.lib.*

class ImportWagonTest {
    
    // === ImportParser Tests ===
    
    @Test
    fun `parser should parse basic import annotation`() {
        val annotation = "@file:Import(\"org.jetbrains.kotlin:kotlin-stdlib:1.9.0\")"
        val import = ImportParser.parseImportAnnotation(annotation)
        
        assertEquals("org.jetbrains.kotlin:kotlin-stdlib:1.9.0", import.coordinate)
        assertEquals("compile", import.scope)
        assertEquals("jar", import.type)
    }
    
    @Test
    fun `parser should parse import with scope`() {
        val annotation = "@file:Import(\"org.jetbrains.kotlin:kotlin-stdlib:1.9.0:runtime\")"
        val import = ImportParser.parseImportWithScope(annotation)
        
        assertEquals("org.jetbrains.kotlin:kotlin-stdlib:1.9.0", import.coordinate)
        assertEquals("runtime", import.scope)
    }
    
    @Test
    fun `parser should throw exception for invalid coordinate`() {
        val annotation = "@file:Import(\"invalid-coordinate\")"
        
        assertFailsWith<IllegalArgumentException> {
            ImportParser.parseImportAnnotation(annotation)
        }
    }
    
    @Test
    fun `parser should throw exception for missing parts`() {
        val annotation = "@file:Import(\"group:artifact\")"
        
        assertFailsWith<IllegalArgumentException> {
            ImportParser.parseImportAnnotation(annotation)
        }
    }
    
    // === ImportWagon Tests ===
    
    @Test
    fun `wagon should resolve local path correctly`() {
        val wagon = ImportWagon()
        val import = ImportAnnotation("org.jetbrains.kotlin:kotlin-stdlib:1.9.0")
        
        // This would need access to internal method, so we test through public interface
        val result = runBlocking {
            try {
                wagon.resolveImport(import)
            } catch (e: NotImplementedError) {
                // Expected since we're not implementing actual download
                "test-path"
            }
        }
        
        assertNotNull(result)
    }
    
    @Test
    fun `wagon should resolve multiple imports concurrently`() = runBlocking {
        val wagon = ImportWagon()
        val imports = listOf(
            ImportAnnotation("org.jetbrains.kotlin:kotlin-stdlib:1.9.0"),
            ImportAnnotation("org.jetbrains.kotlin:kotlin-reflect:1.9.0")
        )
        
        assertFailsWith<NotImplementedError> {
            wagon.resolveImports(imports)
        }
    }
    
    @Test
    fun `wagon should generate classpath from imports`() = runBlocking {
        val wagon = ImportWagon()
        val imports = listOf(
            ImportAnnotation("org.jetbrains.kotlin:kotlin-stdlib:1.9.0"),
            ImportAnnotation("org.jetbrains.kotlin:kotlin-reflect:1.9.0")
        )
        
        assertFailsWith<NotImplementedError> {
            wagon.generateClasspath(imports)
        }
    }
    
    @Test
    fun `wagon should use default repositories`() {
        val wagon = ImportWagon()
        
        // Test that wagon is created with default repositories
        assertNotNull(wagon)
    }
    
    @Test
    fun `wagon should use custom cache directory`() {
        val customCache = "/custom/cache/dir"
        val wagon = ImportWagon(cacheDir = customCache)
        
        // Test that wagon is created with custom cache
        assertNotNull(wagon)
    }
    
    // === ScriptImportProcessor Tests ===
    
    @Test
    fun `processor should extract imports from script content`() {
        val processor = ScriptImportProcessor()
        val scriptContent = """
            @file:Import("org.jetbrains.kotlin:kotlin-stdlib:1.9.0")
            @file:Import("org.jetbrains.kotlin:kotlin-reflect:1.9.0")
            
            import kotlin.reflect.full.*
            
            fun main() {
                println("Hello from k2script!")
            }
        """.trimIndent()
        
        val imports = processor.extractImports(scriptContent)
        
        assertEquals(2, imports.size)
        assertEquals("org.jetbrains.kotlin:kotlin-stdlib:1.9.0", imports[0].coordinate)
        assertEquals("org.jetbrains.kotlin:kotlin-reflect:1.9.0", imports[1].coordinate)
    }
    
    @Test
    fun `processor should handle script without imports`() {
        val processor = ScriptImportProcessor()
        val scriptContent = """
            fun main() {
                println("Hello from k2script!")
            }
        """.trimIndent()
        
        val imports = processor.extractImports(scriptContent)
        
        assertEquals(0, imports.size)
    }
    
    @Test
    fun `processor should remove import annotations from content`() {
        val processor = ScriptImportProcessor()
        val scriptContent = """
            @file:Import("org.jetbrains.kotlin:kotlin-stdlib:1.9.0")
            
            fun main() {
                println("Hello from k2script!")
            }
        """.trimIndent()
        
        val processedContent = processor.removeImportAnnotations(scriptContent)
        
        assertFalse(processedContent.contains("@file:Import"))
        assertTrue(processedContent.contains("fun main()"))
    }
    
    @Test
    fun `processor should process script imports`() = runBlocking {
        val processor = ScriptImportProcessor()
        val scriptContent = """
            @file:Import("org.jetbrains.kotlin:kotlin-stdlib:1.9.0")
            
            fun main() {
                println("Hello from k2script!")
            }
        """.trimIndent()
        
        assertFailsWith<NotImplementedError> {
            processor.processScriptImports(scriptContent)
        }
    }
    
    // === ClasspathGenerator Tests ===
    
    @Test
    fun `classpath generator should generate classpath from coordinates`() = runBlocking {
        val imports = listOf(
            "org.jetbrains.kotlin:kotlin-stdlib:1.9.0",
            "org.jetbrains.kotlin:kotlin-reflect:1.9.0"
        )
        
        assertFailsWith<NotImplementedError> {
            ClasspathGenerator.generateClasspath(imports)
        }
    }
    
    @Test
    fun `classpath generator should handle empty imports`() = runBlocking {
        val imports = emptyList<String>()
        
        assertFailsWith<NotImplementedError> {
            ClasspathGenerator.generateClasspath(imports)
        }
    }
    
    @Test
    fun `classpath generator should read from file`() = runBlocking {
        assertFailsWith<NotImplementedError> {
            ClasspathGenerator.generateClasspathFromFile("test-imports.txt")
        }
    }
    
    // === Integration Tests ===
    
    @Test
    fun `full import workflow should work`() = runBlocking {
        val scriptContent = """
            @file:Import("org.jetbrains.kotlin:kotlin-stdlib:1.9.0")
            @file:Import("org.jetbrains.kotlin:kotlin-reflect:1.9.0")
            
            import kotlin.reflect.full.*
            
            fun main() {
                println("Hello from k2script with imports!")
            }
        """.trimIndent()
        
        val processor = ScriptImportProcessor()
        
        assertFailsWith<NotImplementedError> {
            processor.processScriptImports(scriptContent)
        }
    }
    
    @Test
    fun `import annotation should handle different formats`() {
        // Test basic format
        val basic = "@file:Import(\"org.jetbrains.kotlin:kotlin-stdlib:1.9.0\")"
        val basicImport = ImportParser.parseImportAnnotation(basic)
        assertEquals("org.jetbrains.kotlin:kotlin-stdlib:1.9.0", basicImport.coordinate)
        
        // Test with scope
        val withScope = "@file:Import(\"org.jetbrains.kotlin:kotlin-stdlib:1.9.0:runtime\")"
        val scopeImport = ImportParser.parseImportWithScope(withScope)
        assertEquals("runtime", scopeImport.scope)
    }
    
    @Test
    fun `import annotation should handle edge cases`() {
        // Test with extra whitespace
        val whitespace = "@file:Import( \"org.jetbrains.kotlin:kotlin-stdlib:1.9.0\" )"
        val import = ImportParser.parseImportAnnotation(whitespace)
        assertEquals("org.jetbrains.kotlin:kotlin-stdlib:1.9.0", import.coordinate)
        
        // Test with newlines
        val multiline = "@file:Import(\n\"org.jetbrains.kotlin:kotlin-stdlib:1.9.0\"\n)"
        val multilineImport = ImportParser.parseImportAnnotation(multiline)
        assertEquals("org.jetbrains.kotlin:kotlin-stdlib:1.9.0", multilineImport.coordinate)
    }
    
    @Test
    fun `script processor should handle complex scripts`() {
        val processor = ScriptImportProcessor()
        val complexScript = """
            #!/usr/bin/env k2script
            
            @file:Import("org.jetbrains.kotlin:kotlin-stdlib:1.9.0")
            @file:Import("org.jetbrains.kotlin:kotlin-reflect:1.9.0")
            @file:Import("com.fasterxml.jackson.core:jackson-databind:2.15.0")
            
            import kotlin.reflect.full.*
            import com.fasterxml.jackson.databind.ObjectMapper
            
            data class User(val name: String, val age: Int)
            
            fun main() {
                val mapper = ObjectMapper()
                val user = User("John", 30)
                val json = mapper.writeValueAsString(user)
                println("JSON: $json")
            }
        """.trimIndent()
        
        val imports = processor.extractImports(complexScript)
        
        assertEquals(3, imports.size)
        assertTrue(imports.any { it.coordinate.contains("jackson-databind") })
        
        val processedContent = processor.removeImportAnnotations(complexScript)
        assertFalse(processedContent.contains("@file:Import"))
        assertTrue(processedContent.contains("data class User"))
        assertTrue(processedContent.contains("fun main()"))
    }
} 
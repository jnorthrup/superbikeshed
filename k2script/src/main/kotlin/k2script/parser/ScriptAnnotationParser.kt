@file:OptIn(kotlin.kotlin.ExperimentalStdlibApi::class)
package k2script.parser

import borg.trikeshed.lib.*
import java.io.File

/**
 * Parser for K2script annotations using TrikeShed data structures
 */
object ScriptAnnotationParser {
    
    /**
     * Parses @file:DependsOn annotations from a script file
     * Returns an Indexed collection of Maven coordinates
     */
    fun parseDependencies(scriptFile: File): Indexed<String> {
        if (!scriptFile.exists() || !scriptFile.isFile) {
            return emptyList<String>().toIdx()
        }
        
        val dependencies = mutableListOf<String>()
        val dependsOnPattern = Regex("""@file:DependsOn\s*\(\s*"([^"]+)"\s*\)""")
        
        scriptFile.forEachLine { line ->
            val match = dependsOnPattern.find(line.trim())
            if (match != null) {
                dependencies.add(match.groupValues[1])
            }
        }
        
        return dependencies.toIdx()
    }
    
    /**
     * Parses @file:Repository annotations from a script file
     * Returns an Indexed collection of repository URLs
     */
    fun parseRepositories(scriptFile: File): Indexed<String> {
        if (!scriptFile.exists() || !scriptFile.isFile) {
            return emptyList<String>().toIdx()
        }
        
        val repositories = mutableListOf<String>()
        val repoPattern = Regex("""@file:Repository\s*\(\s*"([^"]+)"\s*\)""")
        
        scriptFile.forEachLine { line ->
            val match = repoPattern.find(line.trim())
            if (match != null) {
                repositories.add(match.groupValues[1])
            }
        }
        
        return repositories.toIdx()
    }
    
    /**
     * Parses all script annotations and returns a Join of annotation type to values
     */
    fun parseAllAnnotations(scriptFile: File): Join<String, Indexed<String>> {
        val deps = parseDependencies(scriptFile)
        val repos = parseRepositories(scriptFile)
        
        return "dependencies" j deps
    }
    
    /**
     * Extracts the shebang line if present
     */
    fun parseShebang(scriptFile: File): String? {
        scriptFile.useLines { lines ->
            val firstLine = lines.firstOrNull()
            return if (firstLine?.startsWith("#!") == true) {
                firstLine
            } else {
                null
            }
        }
    }
}
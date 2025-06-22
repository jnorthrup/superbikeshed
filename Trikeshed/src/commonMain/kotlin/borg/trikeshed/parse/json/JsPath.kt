package borg.trikeshed.parse.json

import borg.trikeshed.lib.*

/**
 * Simple sealed class to represent either a string key or integer index
 */
sealed class JsPathElement {
    data class Key(val value: String) : JsPathElement()
    data class Index(val value: Int) : JsPathElement()
}

/**
 * JsPath - JSON path for navigation and querying
 * Represents a path through JSON structure using either string keys (for objects) or integer indices (for arrays)
 */
typealias JsPath = Indexed<JsPathElement>

/**
 * JsPath builder and utility functions
 */
object JsPathBuilder {
    
    /**
     * Create a JsPath from a dot-separated string path
     * Example: "user.profile.name" -> [Key("user"), Key("profile"), Key("name")]
     */
    fun fromString(path: String): JsPath {
        val elements: List<JsPathElement> = path.split(".").map { JsPathElement.Key(it) }
        return elements.toSeries()
    }
    
    /**
     * Create a JsPath from a list of path components
     */
    fun fromComponents(vararg components: JsPathElement): JsPath {
        return components.toList().toSeries()
    }
    
    /**
     * Create a JsPath for object key access
     */
    fun key(name: String): JsPath {
        return listOf(JsPathElement.Key(name)).toSeries()
    }
    
    /**
     * Create a JsPath for array index access
     */
    fun index(idx: Int): JsPath {
        return listOf(JsPathElement.Index(idx)).toSeries()
    }
    
    /**
     * Combine multiple paths
     */
    fun combine(vararg paths: JsPath): JsPath {
        val allElements = mutableListOf<JsPathElement>()
        paths.forEach { path ->
            path.play.forEach { element ->
                allElements.add(element)
            }
        }
        return allElements.toSeries()
    }
}

/**
 * Extension functions for easier path construction
 */
fun String.toJsPath(): JsPath = JsPathBuilder.fromString(this)

fun JsPath.append(element: JsPathElement): JsPath {
    val elements = mutableListOf<JsPathElement>()
    this.play.forEach { elements.add(it) }
    elements.add(element)
    return elements.toSeries()
}

fun JsPath.append(key: String): JsPath = append(JsPathElement.Key(key))
fun JsPath.append(index: Int): JsPath = append(JsPathElement.Index(index))
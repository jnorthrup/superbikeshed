package borg.trikeshed.parse.json

import borg.trikeshed.lib.*

/**
 * JsPath - JSON path for navigation and querying
 * Represents a path through JSON structure using either string keys (for objects) or integer indices (for arrays)
 */
typealias JsPathElement = Either<String, Int>
typealias JsPath = Series<JsPathElement>

/**
 * JsPath builder and utility functions
 */
object JsPathBuilder {
    
    /**
     * Create a JsPath from a dot-separated string path
     * Example: "user.profile.name" -> [String("user"), String("profile"), String("name")]
     */
    fun fromString(path: String): JsPath {
        return path.split(".").map { JsPathElement.left(it) }.toSeries()
    }
    
    /**
     * Create a JsPath from a list of path components
     */
    fun fromComponents(vararg components: JsPathElement): JsPath {
        return components.toSeries()
    }
    
    /**
     * Create a JsPath for object key access
     */
    fun key(key: String): JsPath {
        return 1 j { JsPathElement.left(key) }
    }
    
    /**
     * Create a JsPath for array index access
     */
    fun index(index: Int): JsPath {
        return 1 j { JsPathElement.right(index) }
    }
    
    /**
     * Combine multiple JsPaths
     */
    fun combine(vararg paths: JsPath): JsPath {
        val combined = mutableListOf<JsPathElement>()
        paths.forEach { path ->
            path.play.forEach { combined.add(it) }
        }
        return combined.toSeries()
    }
}

/**
 * Extension functions for JsPath
 */
fun JsPath.first(): JsPathElement = this[0]
fun JsPath.drop(n: Int): JsPath = (size - n) j { i -> this[i + n] }
fun JsPath.take(n: Int): JsPath = minOf(n, size) j { i -> this[i] }

/**
 * Convenience functions for creating JsPaths
 */
fun String.toJsPath(): JsPath = JsPathBuilder.fromString(this)
fun Int.toJsPath(): JsPath = JsPathBuilder.index(this)
fun List<JsPathElement>.toJsPath(): JsPath = toSeries()

/**
 * JsPath operators for easy path construction
 */
operator fun String.div(other: String): JsPath = JsPathBuilder.combine(this.toJsPath(), other.toJsPath())
operator fun JsPath.div(other: String): JsPath = JsPathBuilder.combine(this, other.toJsPath())
operator fun JsPath.div(other: Int): JsPath = JsPathBuilder.combine(this, other.toJsPath()) 
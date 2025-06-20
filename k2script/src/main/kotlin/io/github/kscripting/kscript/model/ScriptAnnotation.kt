// Entire content as provided (no changes)
package io.github.kscripting.kscript.model

import io.github.kscripting.shell.model.ScriptLocation

sealed interface ScriptAnnotation

@JvmInline
value class Include(val value: String) : ScriptAnnotation

@JvmInline
value class PackageName(val value: String) : ScriptAnnotation

@JvmInline
value class ImportName(val value: String) : ScriptAnnotation

@JvmInline
value class Dependency(val value: String) : ScriptAnnotation

@JvmInline
value class KotlinOpt(val value: String) : ScriptAnnotation

@JvmInline
value class CompilerOpt(val value: String) : ScriptAnnotation

@JvmInline
value class Entry(val value: String) : ScriptAnnotation

data class DeprecatedItem(val scriptLocation: ScriptLocation, val line: Int, val message: String) : ScriptAnnotation

data class Repository(val id: String, val url: String, val user: String = "", val password: String = "") :
  ScriptAnnotation

object SheBang : ScriptAnnotation
object Code : ScriptAnnotation
data class BaseClass(val value: String) : ScriptAnnotation

/**
 * Represents a markdown code block with language specification
 * @property language The language identifier (e.g., "kotlin", "java", "bash")
 * @property content The code content within the block
 * @property isStart Whether this is the start of a code block
 * @property isEnd Whether this is the end of a code block
 */
data class MarkdownCodeBlock(
    val language: String? = null,
    val content: String = "",
    val isStart: Boolean = false,
    val isEnd: Boolean = false
) : ScriptAnnotation

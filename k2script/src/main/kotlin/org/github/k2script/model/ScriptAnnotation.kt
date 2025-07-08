@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
package org.github.k2script.model

import org.github.k2script.shell.model.ScriptLocation

sealed interface ScriptAnnotation

@kotlin.jvm.JvmInline
value class Include(val value: String) : ScriptAnnotation

@kotlin.jvm.JvmInline
value class PackageName(val value: String) : ScriptAnnotation

@kotlin.jvm.JvmInline
value class ImportName(val value: String) : ScriptAnnotation

@kotlin.jvm.JvmInline
value class Dependency(val value: String) : ScriptAnnotation

@kotlin.jvm.JvmInline
value class KotlinOpt(val value: String) : ScriptAnnotation

@kotlin.jvm.JvmInline
value class CompilerOpt(val value: String) : ScriptAnnotation

@kotlin.jvm.JvmInline
value class Entry(val value: String) : ScriptAnnotation

data class DeprecatedItem(val scriptLocation: ScriptLocation, val line: Int, val message: String) : ScriptAnnotation

data class Repository(val id: String, val url: String, val user: String = "", val password: String = "") :
  ScriptAnnotation

object SheBang : ScriptAnnotation
object Code : ScriptAnnotation
data class BaseClass(val value: String) : ScriptAnnotation

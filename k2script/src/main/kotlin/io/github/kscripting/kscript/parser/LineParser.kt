package io.github.kscripting.kscript.parser

import io.github.kscripting.kscript.model.*
import io.github.kscripting.shell.model.ScriptLocation
// import borg.entityscanner.KotlinEntityScanner
// import borg.entityscanner.K2ScriptIntegration

@Suppress("UNUSED_PARAMETER")
object LineParser {
    private const val deprecatedAnnotation = "Deprecated annotation:"

    private val parsers = listOf(
        LineParser::parseSheBang,
        LineParser::parseBaseClass,
        LineParser::parsePackage,
        LineParser::parseRepository,
        LineParser::parseDependency,
        LineParser::parseEntry,
        LineParser::parseKotlinOpts,
        LineParser::parseCompilerOpts,
        LineParser::parseProjectCoordinates,
        LineParser::parseImport,
        LineParser::parseInclude,
        LineParser::parseMarkdownCodeBlock,
    )

    /**
     * Enhanced parsing using KotlinEntityScanner for more robust annotation detection
     * This modernized approach replaces regex-based parsing with structured entity analysis
     * NOTE: Currently disabled due to dependency issues - will be enabled when kotlin-entity-scanner is available
     */
    /*
    fun parseWithEntityScanner(scriptLocation: ScriptLocation, scriptContent: String): List<ScriptAnnotation> {
        val annotations = mutableListOf<ScriptAnnotation>()
        
        try {
            // Use K2ScriptIntegration to extract k2script-specific metadata
            val (k2annotations, dependencies) = K2ScriptIntegration.extractK2ScriptMetadata(scriptContent)
            
            // Convert dependency graph to ScriptAnnotation objects
            dependencies.play.forEach { dependency ->
                val (groupId, artifactId) = dependency
                val coordinate = "$groupId:$artifactId"
                annotations.add(Dependency(coordinate))
            }
            
            // Convert annotations list to ScriptAnnotation objects
            k2annotations.play.forEach { annotation ->
                when {
                    annotation.startsWith("@file:Import(") -> {
                        val value = extractQuotedValueFromAnnotation(annotation)
                        if (value != null) annotations.add(Include(value))
                    }
                    annotation.startsWith("@file:Repository(") -> {
                        val repository = parseRepositoryAnnotation(annotation)
                        if (repository != null) annotations.add(repository)
                    }
                    annotation == "shebang" -> {
                        annotations.addAll(sheBang)
                    }
                }
            }
            
            // Also extract imports using the entity scanner
            val imports = KotlinEntityScanner.scanImports(scriptContent)
            imports.play.forEach { importPath ->
                annotations.add(ImportName(importPath))
            }
            
        } catch (e: Exception) {
            // Fallback to line-by-line parsing if entity scanner fails
            return parseContentLineByLine(scriptLocation, scriptContent)
        }
        
        return annotations
    }
    */
    
    /**
     * Fallback line-by-line parsing using existing regex-based approach
     */
    private fun parseContentLineByLine(scriptLocation: ScriptLocation, scriptContent: String): List<ScriptAnnotation> {
        val annotations = mutableListOf<ScriptAnnotation>()
        
        scriptContent.lines().forEachIndexed { lineIndex, line ->
            parsers.forEach { parser ->
                try {
                    val result = parser(scriptLocation, lineIndex + 1, line)
                    annotations.addAll(result)
                } catch (e: Exception) {
                    // Skip parsing errors and continue with other parsers
                }
            }
        }
        
        return annotations
    }
    
    /**
     * Extract quoted value from annotation string like @file:Import("value")
     */
    private fun extractQuotedValueFromAnnotation(annotation: String): String? {
        val regex = """@file:\w+\("([^"]+)"\)""".toRegex()
        return regex.find(annotation)?.groupValues?.get(1)
    }
    
    /**
     * Parse repository annotation into Repository object
     */
    private fun parseRepositoryAnnotation(annotation: String): Repository? {
        val regex = """@file:Repository\("([^"]+)"(?:,\s*user="([^"]*)")?(?:,\s*password="([^"]*)")?\)""".toRegex()
        val match = regex.find(annotation) ?: return null
        
        val url = match.groupValues[1]
        val user = match.groupValues.getOrNull(2) ?: ""
        val password = match.groupValues.getOrNull(3) ?: ""
        
        return Repository("", url, user, password)
    }

    fun parseSheBang(scriptLocation: ScriptLocation, line: Int, text: String): List<ScriptAnnotation> {
        if (text.startsWith("#!/")) {
            return sheBang
        }
        return emptyList()
    }

    fun parseBaseClass(scriptLocation: ScriptLocation, line: Int, text: String): List<ScriptAnnotation> {
        val matchResult = BASE_CLASS_REGEX.find(text) ?: return emptyList()
        val className = matchResult.groups[1]?.value ?: return emptyList()
        return listOf(BaseClass(className))
    }

    fun parseInclude(scriptLocation: ScriptLocation, line: Int, text: String): List<ScriptAnnotation> {
        val fileImport = "@file:Import"
        val fileInclude = "@file:Include"
        val include = "//INCLUDE "

        text.trim().let {
            return when {
                it.startsWith(fileImport) -> {
                    val value = extractQuotedValueInParenthesis(it.substring(fileImport.length))
                    listOf(Include(value))
                }

                it.startsWith(fileInclude) -> {
                    val value = extractQuotedValueInParenthesis(it.substring(fileInclude.length))

                    listOf(
                        Include(value), createDeprecatedAnnotation(
                            scriptLocation, line, deprecatedAnnotation, text, "@file:Import(\"$value\")"
                        )
                    )
                }

                it.startsWith(include) -> {
                    val value = extractValue(it.substring(include.length))

                    listOf(
                        Include(value), createDeprecatedAnnotation(
                            scriptLocation, line, deprecatedAnnotation, text, "@file:Import(\"$value\")"
                        )
                    )
                }

                else -> emptyList()
            }
        }
    }

    private fun validateDependency(dependency: String): String {
        val regex = Regex("^([^:]*):([^:]*):([^:@]*)(:(.*))?(@(.*))?\$")
        regex.find(dependency) ?: throw ParseException(
            "Invalid dependency locator: '${dependency}'. Expected format is groupId:artifactId:version[:classifier][@type]"
        )
        return dependency
    }

    fun parseDependency(scriptLocation: ScriptLocation, line: Int, text: String): List<ScriptAnnotation> {
        val fileDependsOn = "@file:DependsOn"
        val fileDependsOnMaven = "@file:DependsOnMaven"
        val depends = "//DEPS "

        text.trim().let { s ->
            val deprecatedItems: MutableList<DeprecatedItem> = mutableListOf()

            val dependencies = when {
                s.startsWith(fileDependsOnMaven) -> {
                    extractQuotedValuesInParenthesis(s.substring(fileDependsOnMaven.length))
                }

                s.startsWith(fileDependsOn) -> {
                    extractQuotedValuesInParenthesis(s.substring(fileDependsOn.length))
                }

                s.startsWith(depends) -> {
                    val values = extractValues(s.substring(depends.length))
                    deprecatedItems.add(createDeprecatedAnnotation(scriptLocation,
                        line,
                        deprecatedAnnotation,
                        text,
                        "@file:DependsOn(" + values.joinToString(", ") { "\"${it.trim()}\"" } + ")"))

                    values
                }

                else -> emptyList()
            }

            val dependencyAnnotations = dependencies.map {
                val validated = validateDependency(it)
                Dependency(validated)
            }

            return dependencyAnnotations + deprecatedItems
        }
    }

    fun parseEntry(scriptLocation: ScriptLocation, line: Int, text: String): List<ScriptAnnotation> {
        val fileEntry = "@file:EntryPoint"
        val entry = "//ENTRY "

        text.trim().let {
            return when {
                it.startsWith(fileEntry) -> listOf(
                    Entry(extractQuotedValueInParenthesis(it.substring(fileEntry.length)))
                )

                it.startsWith(entry) -> {
                    val value = extractValue(it.substring(entry.length))
                    listOf(
                        Entry(value), createDeprecatedAnnotation(
                            scriptLocation, line, deprecatedAnnotation, text, "@file:EntryPoint(\"$value\")"
                        )
                    )
                }

                else -> emptyList()
            }
        }
    }

    fun parseRepository(scriptLocation: ScriptLocation, line: Int, text: String): List<ScriptAnnotation> {
        //Format:
        // @file:MavenRepository("imagej", "http://maven.imagej.net/content/repositories/releases/")
        // @file:Repository("http://maven.imagej.net/content/repositories/releases/", user="user", password="pass")

        val fileMavenRepository = "@file:MavenRepository"
        val fileRepository = "@file:Repository"

        text.trim().let {
            return when {
                it.startsWith(fileMavenRepository) -> {
                    val value = it.substring(fileMavenRepository.length).substringBeforeLast(")")

                    val repository = value.split(",").map { it.trim(' ', '"', '(') }.let { annotationParams ->
                        val keyValSep = "[ ]*=[ ]*\"".toRegex()

                        val namedArgs = annotationParams.filter { it.contains(keyValSep) }.map { keyVal ->
                            keyVal.split(keyValSep).map { it.trim(' ', '\"') }.let { it.first() to it.last() }
                        }.toMap()

                        if (annotationParams.size < 2) {
                            throw ParseException(
                                "Missing ${2 - annotationParams.size} of the required arguments for @file:MavenRepository(id, url)"
                            )
                        }

                        Repository(
                            namedArgs.getOrDefault("id", annotationParams[0]),
                            namedArgs.getOrDefault("url", annotationParams[1]),
                            namedArgs.getOrDefault("user", annotationParams.getOrNull(2) ?: ""),
                            namedArgs.getOrDefault("password", annotationParams.getOrNull(3) ?: "")
                        )
                    }

                    var str = """"${repository.url}""""

                    if (repository.user.isNotBlank()) {
                        str += """, user="${repository.user}""""
                    }

                    if (repository.password.isNotBlank()) {
                        str += """, password="${repository.password}""""
                    }

                    return listOf(
                        repository, createDeprecatedAnnotation(
                            scriptLocation, line, deprecatedAnnotation, text, "@file:Repository($str)"
                        )
                    )
                }

                it.startsWith(fileRepository) -> {
                    val value = it.substring(fileRepository.length).substringBeforeLast(")")

                    val repository = value.split(",").map { it.trim(' ', '"', '(') }.let { annotationParams ->
                        val keyValSep = "[ ]*=[ ]*\"".toRegex()

                        val namedArgs = annotationParams.filter { it.contains(keyValSep) }.map { keyVal ->
                            keyVal.split(keyValSep).map { it.trim(' ', '\"') }.let { it.first() to it.last() }
                        }.toMap()

                        if (annotationParams.isEmpty()) {
                            throw ParseException("Missing required argument of annotation @file:Repository(url)")
                        }

                        Repository(
                            namedArgs.getOrDefault("id", ""),
                            namedArgs.getOrDefault("url", annotationParams[0]),
                            namedArgs.getOrDefault("user", annotationParams.getOrNull(1) ?: ""),
                            namedArgs.getOrDefault("password", annotationParams.getOrNull(2) ?: "")
                        )
                    }
                    return listOf(repository)
                }

                else -> emptyList()
            }
        }
    }

    fun parseKotlinOpts(scriptLocation: ScriptLocation, line: Int, text: String): List<ScriptAnnotation> {
        val fileKotlinOptions = "@file:KotlinOptions"
        val fileKotlinOpts = "@file:KotlinOpts"
        val kotlinOpts = "//KOTLIN_OPTS "

        text.trim().let {
            return when {
                it.startsWith(fileKotlinOptions) -> extractQuotedValuesInParenthesis(it.substring(fileKotlinOptions.length)).map {
                    KotlinOpt(it)
                }

                it.startsWith(fileKotlinOpts) -> {
                    val values = extractQuotedValuesInParenthesis(it.substring(fileKotlinOpts.length))

                    values.map { KotlinOpt(it) } + createDeprecatedAnnotation(scriptLocation,
                        line,
                        deprecatedAnnotation,
                        text,
                        "@file:KotlinOptions(" + values.joinToString(
                            ", "
                        ) { "\"$it\"" } + ")")
                }

                it.startsWith(kotlinOpts) -> {
                    val values = extractValues(it.substring(kotlinOpts.length))
                    values.map { KotlinOpt(it) } + createDeprecatedAnnotation(scriptLocation,
                        line,
                        deprecatedAnnotation,
                        text,
                        "@file:KotlinOptions(" + values.joinToString(
                            ", "
                        ) { "\"$it\"" } + ")")
                }

                else -> emptyList()
            }
        }
    }

    fun parseCompilerOpts(scriptLocation: ScriptLocation, line: Int, text: String): List<ScriptAnnotation> {
        val fileCompilerOptions = "@file:CompilerOptions"
        val fileCompilerOpts = "@file:CompilerOpts"
        val compilerOpts = "//COMPILER_OPTS "

        text.trim().let {
            return when {
                it.startsWith(fileCompilerOptions) -> extractQuotedValuesInParenthesis(it.substring(fileCompilerOptions.length)).map {
                    CompilerOpt(it)
                }

                it.startsWith(fileCompilerOpts) -> {
                    val values = extractQuotedValuesInParenthesis(it.substring(fileCompilerOpts.length))

                    values.map { CompilerOpt(it) } + createDeprecatedAnnotation(scriptLocation,
                        line,
                        deprecatedAnnotation,
                        text,
                        "@file:CompilerOptions(" + values.joinToString(
                            ", "
                        ) { "\"$it\"" } + ")")
                }

                it.startsWith(compilerOpts) -> {
                    val values = extractValues(it.substring(compilerOpts.length))
                    values.map { CompilerOpt(it) } + createDeprecatedAnnotation(scriptLocation,
                        line,
                        deprecatedAnnotation,
                        text,
                        "@file:CompilerOptions(" + values.joinToString(
                            ", "
                        ) { "\"$it\"" } + ")")
                }

                else -> emptyList()
            }
        }
    }

    fun parsePackage(scriptLocation: ScriptLocation, line: Int, text: String): List<ScriptAnnotation> {
        val packagePrefix = "package "

        text.trim().let {
            if (it.startsWith(packagePrefix)) {
                return listOf(PackageName(it.substring(packagePrefix.length)))
            }
            return emptyList()
        }
    }

    fun parseImport(scriptLocation: ScriptLocation, line: Int, text: String): List<ScriptAnnotation> {
        val importPrefix = "import "

        text.trim().let {
            if (it.startsWith(importPrefix)) {
                return listOf(ImportName(it.substring(importPrefix.length)))
            }
            return emptyList()
        }
    }

    private fun extractQuotedValueInParenthesis(string: String): String {
        val result = extractQuotedValuesInParenthesis(string)

        if (result.size != 1) {
            throw ParseException("Expected single value, but get ${result.size}")
        }

        return result[0]
    }

    private fun extractQuotedValuesInParenthesis(string: String): List<String> {
        // https://stackoverflow.com/questions/171480/regex-grabbing-values-between-quotation-marks

        if (!string.startsWith("(")) {
            throw ParseException("Missing parenthesis")
        }

        val annotationArgs = """(["'])(\\?.*?)\1""".toRegex().findAll(string.drop(1)).toList().map {
            it.groupValues[2].trim()
        }

        // fail if any argument is a comma separated list of artifacts (see #101)
        annotationArgs.filter { it.contains(",[^)]".toRegex()) }.let {
            if (it.isNotEmpty()) {
                throw ParseException(
                    "Artifact locators must be provided as separate annotation arguments and not as comma-separated list: $it"
                )
            }
        }

        return annotationArgs
    }

    private fun extractValue(string: String): String {
        val result = extractValues(string)

        if (result.size != 1) {
            throw ParseException("Expected single value, but get ${result.size}")
        }

        return result[0]
    }

    fun extractValues(string: String): List<String> {
        string.trim().let {
            return it.split(",(?=(?:[^']*'[^']*')*[^']*\$)".toRegex()).map(String::trim).filter(String::isNotBlank)
        }
    }

    private fun createDeprecatedAnnotation(
        scriptLocation: ScriptLocation, line: Int, introText: String, existing: String, replacement: String
    ): DeprecatedItem =
        DeprecatedItem(scriptLocation, line, "$introText\n$existing\nshould be replaced with:\n$replacement")

    fun parseProjectCoordinates(scriptLocation: ScriptLocation, line: Int, text: String): List<ScriptAnnotation> {
       val trimmedText = text.trim()
        val matchResult = PROJECT_COORDINATES_ANNOTATION_REGEX.find(trimmedText)

        if (matchResult != null) {
            val attributesString = matchResult.groupValues[1]
            try {
                val projectCoordinates = extractProjectCoordinatesFromAttributes(attributesString)
                // It's possible to return an empty ProjectCoordinates if attributesString is empty or only whitespace
                // We might want to consider if an empty coordinate set is a valid annotation or should be an error/emptyList()
                return listOf(projectCoordinates)
            } catch (e: ParseException) {
                // Augment parse exception with location context
                throw ParseException("Invalid @file:ProjectCoordinates annotation at $scriptLocation line $line: ${e.message}")
            }
        }
        return emptyList()
    }

    /**
     * Parses markdown code blocks with language specification
     * Supports formats like:
     * - ```kotlin
     * - ```java
     * - ```bash
     * - ``` (generic code block)
     */
    fun parseMarkdownCodeBlock(scriptLocation: ScriptLocation, line: Int, text: String): List<ScriptAnnotation> {
        val trimmedText = text.trim()
        
        // Check for code block start: ```language or ```
        val startMatch = MARKDOWN_CODE_BLOCK_START_REGEX.find(trimmedText)
        if (startMatch != null) {
            val language = startMatch.groupValues[1].takeIf { it.isNotBlank() }
            return listOf(MarkdownCodeBlock(
                language = language,
                content = "",
                isStart = true,
                isEnd = false
            ))
        }
        
        // Check for code block end: ```
        val endMatch = MARKDOWN_CODE_BLOCK_END_REGEX.find(trimmedText)
        if (endMatch != null) {
            return listOf(MarkdownCodeBlock(
                language = null,
                content = "",
                isStart = false,
                isEnd = true
            ))
        }
        
        return emptyList()
    }

    private fun extractProjectCoordinatesFromAttributes(attributesString: String): ProjectCoordinates {
        var group: String? = null
        var artifact: String? = null
        var version: String? = null

        if (attributesString.isBlank()) {
            return ProjectCoordinates(null, null, null)
        }

        ATTRIBUTE_REGEX.findAll(attributesString).forEach { matchResult ->
            val key = matchResult.groupValues[1]
            val value = matchResult.groupValues[2].takeIf { it.isNotEmpty() }
                ?: matchResult.groupValues[3].takeIf { it.isNotEmpty() }
                ?: matchResult.groupValues[4]

            when (key) {
                "group" -> group = value
                "artifact" -> artifact = value
                "version" -> version = value
            }
        }
        return ProjectCoordinates(group, artifact, version)
    }
}

private val sheBang = listOf(SheBang)
private val PROJECT_COORDINATES_ANNOTATION_REGEX = Regex("""^@file:ProjectCoordinates\s*\((.*)\)""")
private val ATTRIBUTE_REGEX = Regex("""(group|artifact|version)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s,)]+))""")
private val BASE_CLASS_REGEX = """@file:BaseClass\("([^"]+)"\)""".toRegex()

// Markdown code block regex patterns
private val MARKDOWN_CODE_BLOCK_START_REGEX = Regex("""^```(\w*)$""")
private val MARKDOWN_CODE_BLOCK_END_REGEX = Regex("""^```$""")

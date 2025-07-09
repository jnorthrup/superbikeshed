package buildtools

import java.io.File

/**
 * Fixes missing type annotations in infix lambda expressions.
 * Handles common patterns in TrikeShed codebase with Join<A,B> and Indexed<T> types.
 */
object InfixLambdaTypeFixer {
    
    data class InfixLambdaMatch(
        val fullMatch: String,
        val infixKeyword: String,
        val functionName: String,
        val receiver: String,
        val parameter: String,
        val returnType: String?,
        val lambdaParams: List<String>,
        val lambdaBody: String
    )
    
    // Common type inference patterns for TrikeShed
    private val typeInferenceRules = mapOf(
        "join" to "Join<*, *>",
        "indexed" to "Indexed<*>",
        "twin" to "Twin<*>",
        "series" to "Series<*>",
        "cursor" to "Cursor",
        "channel" to "Channel<*>",
        "flow" to "Flow<*>",
        "attention" to "Attention",
        "context" to "Context"
    )
    
    // Pattern to match infix functions with lambda parameters
    private val infixLambdaPattern = Regex(
        """(infix\s+fun\s+)(<[^>]+>\s+)?([^.]+)\.([^(]+)\(([^)]+)\)(\s*:\s*[^{]+)?\s*=\s*\{([^}]*)\}""",
        RegexOption.DOT_MATCHES_ALL
    )
    
    // Pattern to match lambda parameters
    private val lambdaParamPattern = Regex("""([a-zA-Z_][a-zA-Z0-9_]*)\s*(,\s*([a-zA-Z_][a-zA-Z0-9_]*))*\s*->""")
    
    fun fixFile(file: File): Boolean {
        if (!file.exists() || !file.name.endsWith(".kt")) {
            return false
        }
        
        val content = file.readText()
        var modified = content
        var hasChanges = false
        
        // Find all infix lambda functions
        infixLambdaPattern.findAll(content).forEach { match ->
            val fixed = fixInfixLambda(match)
            if (fixed != match.value) {
                modified = modified.replace(match.value, fixed)
                hasChanges = true
            }
        }
        
        // Also fix inline lambda type annotations
        modified = fixInlineLambdas(modified)
        
        if (hasChanges || modified != content) {
            file.writeText(modified)
            println("Fixed infix lambdas in: ${file.path}")
            return true
        }
        
        return false
    }
    
    private fun fixInfixLambda(match: MatchResult): String {
        val groups = match.groupValues
        val infixKeyword = groups[1]
        val genericParams = groups[2] ?: ""
        val receiver = groups[3]
        val functionName = groups[4]
        val parameter = groups[5]
        val returnType = groups[6] ?: ""
        val lambdaContent = groups[7]
        
        // Extract lambda parameters if any
        val lambdaParams = extractLambdaParams(lambdaContent)
        
        // Fix parameter types
        val fixedParameter = fixParameterType(parameter)
        
        // Fix lambda parameter types
        val fixedLambdaContent = if (lambdaParams.isNotEmpty()) {
            fixLambdaParameterTypes(lambdaContent, functionName, receiver)
        } else {
            lambdaContent
        }
        
        // Infer return type if missing
        val inferredReturnType = if (returnType.isEmpty()) {
            inferReturnType(functionName, receiver, parameter)
        } else {
            returnType
        }
        
        return "$infixKeyword$genericParams$receiver.$functionName($fixedParameter)$inferredReturnType = { $fixedLambdaContent }"
    }
    
    private fun extractLambdaParams(lambdaContent: String): List<String> {
        val paramMatch = lambdaParamPattern.find(lambdaContent)
        return if (paramMatch != null) {
            lambdaContent.substringBefore("->")
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        } else {
            emptyList()
        }
    }
    
    private fun fixParameterType(parameter: String): String {
        // If already has type annotation, return as-is
        if (parameter.contains(":")) {
            return parameter
        }
        
        // Try to infer type from parameter name
        val paramName = parameter.trim()
        val inferredType = when {
            paramName.contains("index", ignoreCase = true) -> "Int"
            paramName.contains("size", ignoreCase = true) -> "Int"
            paramName.contains("count", ignoreCase = true) -> "Int"
            paramName.contains("name", ignoreCase = true) -> "String"
            paramName.contains("value", ignoreCase = true) -> "Any"
            paramName.contains("context", ignoreCase = true) -> "Context"
            else -> {
                // Check type inference rules
                typeInferenceRules.entries.find { (key, _) ->
                    paramName.contains(key, ignoreCase = true)
                }?.value ?: "Any"
            }
        }
        
        return "$paramName: $inferredType"
    }
    
    private fun fixLambdaParameterTypes(
        lambdaContent: String,
        functionName: String,
        receiver: String
    ): String {
        val params = extractLambdaParams(lambdaContent)
        if (params.isEmpty()) return lambdaContent
        
        val typedParams = params.map { param ->
            if (param.contains(":")) {
                param // Already typed
            } else {
                // Infer type based on context
                val inferredType = inferLambdaParamType(param, functionName, receiver, params)
                "$param: $inferredType"
            }
        }
        
        val restOfLambda = lambdaContent.substringAfter("->").trim()
        return "${typedParams.joinToString(", ")} -> $restOfLambda"
    }
    
    private fun inferLambdaParamType(
        param: String,
        functionName: String,
        receiver: String,
        allParams: List<String>
    ): String {
        // Context-based type inference
        return when {
            // Common patterns in map/filter/fold operations
            functionName.contains("map", ignoreCase = true) -> {
                when {
                    receiver.contains("Indexed") -> "T"
                    receiver.contains("Join") -> "Join<A, B>"
                    receiver.contains("Series") -> "T"
                    else -> "Any"
                }
            }
            functionName.contains("filter", ignoreCase = true) -> {
                when {
                    receiver.contains("Indexed") -> "T"
                    else -> "Any"
                }
            }
            functionName.contains("fold", ignoreCase = true) -> {
                if (param == allParams.firstOrNull()) "R" else "T"
            }
            // TrikeShed specific patterns
            param.contains("twin", ignoreCase = true) -> "Twin<*>"
            param.contains("join", ignoreCase = true) -> "Join<*, *>"
            param.contains("cursor", ignoreCase = true) -> "Cursor"
            else -> "Any"
        }
    }
    
    private fun inferReturnType(
        functionName: String,
        receiver: String,
        parameter: String
    ): String {
        // Infer return type based on function patterns
        val returnType = when {
            functionName.contains("map", ignoreCase = true) -> ": Indexed<R>"
            functionName.contains("filter", ignoreCase = true) -> ": Indexed<T>"
            functionName.contains("fold", ignoreCase = true) -> ": R"
            functionName.contains("join", ignoreCase = true) -> ": Join<*, *>"
            functionName.contains("to", ignoreCase = true) -> {
                when {
                    receiver.contains("Join") -> ": Join<*, *>"
                    receiver.contains("Twin") -> ": Twin<*>"
                    else -> ": Any"
                }
            }
            else -> ": Unit"
        }
        
        return " $returnType"
    }
    
    private fun fixInlineLambdas(content: String): String {
        var modified = content
        
        // Pattern for inline lambdas without type annotations
        val inlineLambdaPattern = Regex(
            """(\w+)\s*\{\s*([a-zA-Z_][a-zA-Z0-9_]*(?:\s*,\s*[a-zA-Z_][a-zA-Z0-9_]*)*)\s*->""",
            RegexOption.MULTILINE
        )
        
        inlineLambdaPattern.findAll(content).forEach { match ->
            val functionCall = match.groupValues[1]
            val params = match.groupValues[2].split(",").map { it.trim() }
            
            // Check if params already have types
            if (params.none { it.contains(":") }) {
                val typedParams = params.map { param ->
                    val type = inferInlineParamType(param, functionCall)
                    "$param: $type"
                }
                
                val replacement = "$functionCall { ${typedParams.joinToString(", ")} ->"
                modified = modified.replace(match.value, replacement)
            }
        }
        
        return modified
    }
    
    private fun inferInlineParamType(param: String, functionCall: String): String {
        return when {
            functionCall == "map" -> "T"
            functionCall == "filter" -> "T"
            functionCall == "forEach" -> "T"
            functionCall == "fold" -> if (param == "acc") "R" else "T"
            functionCall == "reduce" -> "T"
            functionCall.endsWith("With") -> "T"
            else -> "Any"
        }
    }
    
    fun processProject(rootDir: File) {
        val kotlinFiles = rootDir.walk()
            .filter { it.isFile && it.name.endsWith(".kt") }
            .filter { !it.path.contains("build/") }
            .toList()
        
        println("Processing ${kotlinFiles.size} Kotlin files for infix lambda fixes...")
        
        var fixedCount = 0
        kotlinFiles.forEach { file ->
            if (fixFile(file)) {
                fixedCount++
            }
        }
        
        println("Fixed infix lambdas in $fixedCount files")
    }
}
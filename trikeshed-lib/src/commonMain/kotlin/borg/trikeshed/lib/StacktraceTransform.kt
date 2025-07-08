package borg.trikeshed.lib

/**
 * TrikeShed Stacktrace Transform - Compilation Data Cube
 * 
 * Provides ranked stacktrace transformation functions for compilation analysis
 * using TrikeShed data structures and hermetic patterns.
 */

/**
 * Compilation Error Coordinate in N-dimensional space
 */
data class CompilationCoordinate(
    val file: String,
    val line: Int,
    val column: Int,
    val errorType: String,
    val severity: ErrorSeverity
)

enum class ErrorSeverity(val rank: Int) {
    WARNING(1),
    ERROR(2), 
    FATAL(3),
    CIRCULAR_DEPENDENCY(4)
}

/**
 * Compilation Data Cube - N-dimensional error analysis
 */
class CompilationDataCube(
    val dimensions: Indexed<String>,
    val coordinates: Indexed<CompilationCoordinate>,
    val transforms: Indexed<StacktraceTransform>
) {
    
    /**
     * Apply ranked transformation to stacktrace data
     */
    fun transform(rank: Int): CompilationDataCube {
        val rankedTransform = transforms[rank % transforms.a]
        val transformedCoordinates = coordinates.a j { i ->
            rankedTransform.apply(coordinates[i])
        }
        
        return CompilationDataCube(
            dimensions = dimensions,
            coordinates = transformedCoordinates,
            transforms = transforms
        )
    }
    
    /**
     * Bisect compilation cube by error severity
     */
    fun bisectBySeverity(severity: ErrorSeverity): CompilationDataCube {
        val filtered = coordinates.filter { it.severity == severity }
        val filteredIndexed = filtered.size j { i -> filtered[i] }
        
        return CompilationDataCube(
            dimensions = dimensions,
            coordinates = filteredIndexed,
            transforms = transforms
        )
    }
    
    /**
     * Project cube onto file dimension
     */
    fun projectByFile(): Join<String, Indexed<CompilationCoordinate>> {
        val fileGroups = mutableMapOf<String, MutableList<CompilationCoordinate>>()
        
        for (i in 0 until coordinates.a) {
            val coord = coordinates[i]
            fileGroups.getOrPut(coord.file) { mutableListOf() }.add(coord)
        }
        
        return fileGroups.entries.first().key j 
               (fileGroups.entries.first().value.size j { i -> fileGroups.entries.first().value[i] })
    }
    
    /**
     * Rank errors by complexity (circular deps highest)
     */
    fun rankByComplexity(): Indexed<CompilationCoordinate> {
        val sorted = coordinates.toList().sortedByDescending { it.severity.rank }
        return sorted.size j { i -> sorted[i] }
    }
}

/**
 * Stacktrace Transform Function Interface
 */
interface StacktraceTransform {
    val rank: Int
    fun apply(coordinate: CompilationCoordinate): CompilationCoordinate
}

/**
 * Circular Dependency Transform (Highest Rank)
 */
class CircularDependencyTransform : StacktraceTransform {
    override val rank: Int = 4
    
    override fun apply(coordinate: CompilationCoordinate): CompilationCoordinate {
        return if (coordinate.errorType.contains("Circular dependency")) {
            coordinate.copy(severity = ErrorSeverity.CIRCULAR_DEPENDENCY)
        } else {
            coordinate
        }
    }
}

/**
 * Unresolved Reference Transform
 */
class UnresolvedReferenceTransform : StacktraceTransform {
    override val rank: Int = 2
    
    override fun apply(coordinate: CompilationCoordinate): CompilationCoordinate {
        return if (coordinate.errorType.contains("Unresolved reference")) {
            coordinate.copy(
                severity = ErrorSeverity.ERROR,
                errorType = "MISSING_DEPENDENCY: ${coordinate.errorType}"
            )
        } else {
            coordinate
        }
    }
}

/**
 * Type Mismatch Transform  
 */
class TypeMismatchTransform : StacktraceTransform {
    override val rank: Int = 1
    
    override fun apply(coordinate: CompilationCoordinate): CompilationCoordinate {
        return if (coordinate.errorType.contains("Type mismatch")) {
            coordinate.copy(
                severity = ErrorSeverity.ERROR,
                errorType = "TYPE_SYSTEM: ${coordinate.errorType}"
            )
        } else {
            coordinate
        }
    }
}

/**
 * Stacktrace Parser - Converts gradle output to compilation coordinates
 */
object StacktraceParser {
    
    /**
     * Parse gradle stacktrace into compilation data cube
     */
    fun parseStacktrace(stacktraceText: String): CompilationDataCube {
        val lines = stacktraceText.lines()
        val coordinates = mutableListOf<CompilationCoordinate>()
        
        for (line in lines) {
            if (line.startsWith("e: file://")) {
                parseErrorLine(line)?.let { coordinates.add(it) }
            } else if (line.contains("Circular dependency")) {
                parseCircularDependency(line)?.let { coordinates.add(it) }
            }
        }
        
        val indexed = coordinates.size j { i -> coordinates[i] }
        val transforms = 3 j { i ->
            when (i) {
                0 -> TypeMismatchTransform()
                1 -> UnresolvedReferenceTransform()
                2 -> CircularDependencyTransform()
                else -> TypeMismatchTransform()
            }
        }
        
        val dimensions = 4 j { i ->
            when (i) {
                0 -> "file"
                1 -> "line" 
                2 -> "errorType"
                3 -> "severity"
                else -> "unknown"
            }
        }
        
        return CompilationDataCube(dimensions, indexed, transforms)
    }
    
    private fun parseErrorLine(line: String): CompilationCoordinate? {
        // Parse: e: file:///path/file.kt:123:45 Error message
        val regex = """e: file://([^:]+):(\d+):(\d+) (.+)""".toRegex()
        val match = regex.find(line) ?: return null
        
        val (file, lineNum, column, error) = match.destructured
        
        return CompilationCoordinate(
            file = file.substringAfterLast("/"),
            line = lineNum.toIntOrNull() ?: 0,
            column = column.toIntOrNull() ?: 0,
            errorType = error,
            severity = ErrorSeverity.ERROR
        )
    }
    
    private fun parseCircularDependency(line: String): CompilationCoordinate? {
        if (!line.contains("Circular dependency")) return null
        
        return CompilationCoordinate(
            file = "build.gradle.kts",
            line = 0,
            column = 0,
            errorType = "Circular dependency between tasks",
            severity = ErrorSeverity.CIRCULAR_DEPENDENCY
        )
    }
}

/**
 * Compilation Cube Analytics - TrikeShed error analysis
 */
object CompilationAnalytics {
    
    /**
     * Generate zero error achievement report
     */
    fun generateZeroErrorReport(
        beforeCube: CompilationDataCube,
        afterCube: CompilationDataCube
    ): ZeroErrorReport {
        val beforeCount = beforeCube.coordinates.a
        val afterCount = afterCube.coordinates.a
        val reduction = beforeCount - afterCount
        
        val suppressions = beforeCube.coordinates.a j { i ->
            val before = beforeCube.coordinates[i]
            SuppressionAction(
                target = before.file,
                action = "GSED_TRANSFORM",
                errorType = before.errorType,
                rank = before.severity.rank
            )
        }
        
        return ZeroErrorReport(
            beforeErrors = beforeCount,
            afterErrors = afterCount,
            reductionCount = reduction,
            suppressionActions = suppressions,
            achievement = if (afterCount == 0) "ZERO_ERRORS_ACHIEVED" else "PARTIAL_REDUCTION"
        )
    }
    
    /**
     * Rank errors by fix complexity
     */
    fun rankByFixComplexity(cube: CompilationDataCube): Indexed<Join<Int, CompilationCoordinate>> {
        val ranked = cube.coordinates.toList().map { coord ->
            val complexity = when {
                coord.errorType.contains("Circular dependency") -> 10
                coord.errorType.contains("Unresolved reference") -> 5
                coord.errorType.contains("Type mismatch") -> 3
                else -> 1
            }
            complexity j coord
        }.sortedByDescending { it.a }
        
        return ranked.size j { i -> ranked[i] }
    }
}

/**
 * Suppression Action Record
 */
data class SuppressionAction(
    val target: String,
    val action: String,
    val errorType: String,
    val rank: Int
)

/**
 * Zero Error Achievement Report
 */
data class ZeroErrorReport(
    val beforeErrors: Int,
    val afterErrors: Int,
    val reductionCount: Int,
    val suppressionActions: Indexed<SuppressionAction>,
    val achievement: String
) {
    
    /**
     * Generate achievement summary
     */
    fun summary(): String = buildString {
        appendLine("🎯 TRIKESHED ZERO ERROR ACHIEVEMENT")
        appendLine("================================")
        appendLine("Before: $beforeErrors compilation errors")
        appendLine("After:  $afterErrors compilation errors") 
        appendLine("Reduction: $reductionCount errors eliminated")
        appendLine("Status: $achievement")
        appendLine()
        appendLine("📋 Suppression Actions Applied:")
        for (i in 0 until suppressionActions.a) {
            val action = suppressionActions[i]
            appendLine("  • ${action.action} on ${action.target} (rank=${action.rank})")
        }
    }
}

/**
 * Extension functions for TrikeShed integration
 */
fun Indexed<CompilationCoordinate>.filter(predicate: (CompilationCoordinate) -> Boolean): List<CompilationCoordinate> {
    val result = mutableListOf<CompilationCoordinate>()
    for (i in 0 until a) {
        if (predicate(b(i))) {
            result.add(b(i))
        }
    }
    return result
}

fun Indexed<CompilationCoordinate>.toList(): List<CompilationCoordinate> {
    val result = mutableListOf<CompilationCoordinate>()
    for (i in 0 until a) {
        result.add(b(i))
    }
    return result
}

/**
 * Example Usage:
 * 
 * ```kotlin
 * val stacktrace = """
 * e: file:///path/Reactor.kt:123:45 Unresolved reference 'ByteBuffer'
 * e: file:///path/Channel.kt:67:12 Circular dependency between tasks
 * """
 * 
 * val cube = StacktraceParser.parseStacktrace(stacktrace)
 * val transformed = cube.transform(rank = 2) // Apply unresolved reference transform
 * val report = CompilationAnalytics.generateZeroErrorReport(cube, transformed)
 * 
 * println(report.summary())
 * ```
 */
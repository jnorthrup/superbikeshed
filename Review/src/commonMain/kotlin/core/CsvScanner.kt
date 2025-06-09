@file:Suppress("NOTHING_TO_INLINE")

package core

import kotlin.jvm.JvmInline

/**
 * CSV Scanner with Finite Pairwise Complexity
 * 
 * Implements TrikeShed's DelimitRange pattern for tensor-first CSV processing.
 * Minimal implementation focused on core functionality.
 */

/**
 * Lexer state for CSV parsing with escape handling
 */
sealed class CsvLexState {
    object Field : CsvLexState()
    object QuotedField : CsvLexState()
    object Escaped : CsvLexState()
    object QuotedEscaped : CsvLexState()
    data class DelimiterFound(val pos: Int) : CsvLexState()
    data class RecordEnd(val pos: Int) : CsvLexState()
}

/**
 * DelimitRange for field boundaries (start j length pattern)
 */
data class DelimitRange(
    val start: UShort,
    val length: UShort
) {
    companion object {
        infix fun UShort.delimRange(length: UShort): DelimitRange = DelimitRange(this, length)
    }
}

/**
 * CSV record as Series of DelimitRanges (TrikeShed pattern)
 */
data class CsvRecord(
    val lineStart: Int,
    val ranges: List<DelimitRange>
)

/**
 * CSV structural fingerprint for O(log n) comparison
 */
data class CsvStructure(
    val recordCount: Int,
    val fieldCount: Int,
    val maxFieldsPerRecord: Int,
    val minFieldsPerRecord: Int,
    val hasQuotedFields: Boolean,
    val hasEscapedFields: Boolean,
    val delimiter: Char = ','
) {
    val structuralHash: Int = 
        (recordCount * 31 + fieldCount) * 31 + maxFieldsPerRecord * 31 + 
        (if (hasQuotedFields) 1 else 0) * 31 + (if (hasEscapedFields) 1 else 0)
    
    infix fun isIsomorphicTo(other: CsvStructure): Boolean =
        structuralHash == other.structuralHash &&
        maxFieldsPerRecord == other.maxFieldsPerRecord &&
        minFieldsPerRecord == other.minFieldsPerRecord &&
        hasQuotedFields == other.hasQuotedFields
}

/**
 * CSV Scanner with tensor-first design
 */
@JvmInline
value class CsvScanner(val input: String) {
    
    /**
     * Parse CSV with DelimitRange indexing (TrikeShed pattern)
     */
    fun parse(): CsvParseResult {
        val records = mutableListOf<CsvRecord>()
        val structure = extractStructure()
        
        var currentState: CsvLexState = CsvLexState.Field
        var rangeStart = 0
        var lineStart = 0
        val currentRanges = mutableListOf<DelimitRange>()
        val openQuotes = mutableListOf<Char>()
        
        var i = 0
        while (i < input.length) {
            val char = input[i]
            
            currentState = when (currentState) {
                is CsvLexState.Field -> when (char) {
                    '\\' -> CsvLexState.Escaped
                    '"', '\'' -> {
                        openQuotes.add(char)
                        CsvLexState.QuotedField
                    }
                    ',' -> {
                        // Add field range
                        val length = (i - rangeStart).coerceAtMost(UShort.MAX_VALUE.toInt())
                        currentRanges.add(DelimitRange((rangeStart - lineStart).toUShort(), length.toUShort()))
                        rangeStart = i + 1
                        CsvLexState.DelimiterFound(i)
                    }
                    '\r' -> {
                        if (i + 1 < input.length && input[i + 1] == '\n') {
                            i++ // Skip \n
                        }
                        CsvLexState.RecordEnd(i)
                    }
                    '\n' -> CsvLexState.RecordEnd(i)
                    else -> CsvLexState.Field
                }
                
                is CsvLexState.Escaped -> CsvLexState.Field
                
                is CsvLexState.QuotedField -> when (char) {
                    '\\' -> CsvLexState.QuotedEscaped
                    '"', '\'' -> {
                        if (openQuotes.isNotEmpty() && openQuotes.last() == char) {
                            openQuotes.removeLastOrNull()
                            if (openQuotes.isEmpty()) CsvLexState.Field else CsvLexState.QuotedField
                        } else {
                            openQuotes.add(char)
                            CsvLexState.QuotedField
                        }
                    }
                    else -> CsvLexState.QuotedField
                }
                
                is CsvLexState.QuotedEscaped -> CsvLexState.QuotedField
                
                is CsvLexState.DelimiterFound -> {
                    // Skip whitespace after delimiter
                    if (char.isWhitespace() && char != '\r' && char != '\n') {
                        rangeStart = i + 1
                        CsvLexState.Field
                    } else {
                        CsvLexState.Field
                    }
                }
                
                is CsvLexState.RecordEnd -> {
                    // Add final field of record
                    val length = (i - rangeStart).coerceAtMost(UShort.MAX_VALUE.toInt())
                    if (length > 0) {
                        currentRanges.add(DelimitRange((rangeStart - lineStart).toUShort(), length.toUShort()))
                    }
                    
                    // Add record
                    records.add(CsvRecord(lineStart, currentRanges.toList()))
                    
                    // Reset for next record
                    currentRanges.clear()
                    openQuotes.clear()
                    lineStart = i + 1
                    rangeStart = lineStart
                    
                    CsvLexState.Field
                }
            }
            i++
        }
        
        // Handle final record
        if (rangeStart < input.length || currentRanges.isNotEmpty()) {
            val length = (input.length - rangeStart).coerceAtMost(UShort.MAX_VALUE.toInt())
            if (length > 0) {
                currentRanges.add(DelimitRange((rangeStart - lineStart).toUShort(), length.toUShort()))
            }
            records.add(CsvRecord(lineStart, currentRanges.toList()))
        }
        
        return CsvParseResult(records, structure)
    }
    
    /**
     * Extract structural fingerprint (O(n) scan)
     */
    fun extractStructure(): CsvStructure {
        var recordCount = 0
        var totalFields = 0
        var maxFields = 0
        var minFields = Int.MAX_VALUE
        var currentFields = 0
        var hasQuoted = false
        var hasEscaped = false
        
        var inString = false
        var escaped = false
        var i = 0
        
        while (i < input.length) {
            val char = input[i]
            
            when {
                escaped -> escaped = false
                inString -> when (char) {
                    '\\' -> {
                        escaped = true
                        hasEscaped = true
                    }
                    '"', '\'' -> inString = false
                }
                else -> when (char) {
                    '"', '\'' -> {
                        inString = true
                        hasQuoted = true
                    }
                    ',' -> currentFields++
                    '\r' -> {
                        if (i + 1 < input.length && input[i + 1] == '\n') i++
                        recordCount++
                        currentFields++ // Count final field
                        totalFields += currentFields
                        maxFields = maxOf(maxFields, currentFields)
                        minFields = minOf(minFields, currentFields)
                        currentFields = 0
                    }
                    '\n' -> {
                        recordCount++
                        currentFields++ // Count final field
                        totalFields += currentFields
                        maxFields = maxOf(maxFields, currentFields)
                        minFields = minOf(minFields, currentFields)
                        currentFields = 0
                    }
                }
            }
            i++
        }
        
        // Handle final record
        if (currentFields > 0 || input.isNotEmpty()) {
            recordCount++
            currentFields++
            totalFields += currentFields
            maxFields = maxOf(maxFields, currentFields)
            minFields = minOf(minFields, currentFields)
        }
        
        if (minFields == Int.MAX_VALUE) minFields = 0
        
        return CsvStructure(recordCount, totalFields, maxFields, minFields, hasQuoted, hasEscaped)
    }
    
    /**
     * Extract field value by record and field index
     */
    fun extractField(record: CsvRecord, fieldIndex: Int): String? {
        if (fieldIndex >= record.ranges.size) return null
        
        val range = record.ranges[fieldIndex]
        val start = record.lineStart + range.start.toInt()
        val end = start + range.length.toInt()
        
        if (end > input.length) return null
        
        val field = input.substring(start, end).trim()
        
        // Handle quoted fields
        return if (field.length >= 2 && 
            ((field.startsWith('"') && field.endsWith('"')) ||
             (field.startsWith('\'') && field.endsWith('\'')))) {
            field.substring(1, field.length - 1)
        } else {
            field
        }
    }
}

/**
 * Supporting data types
 */
data class CsvParseResult(
    val records: List<CsvRecord>,
    val structure: CsvStructure
)
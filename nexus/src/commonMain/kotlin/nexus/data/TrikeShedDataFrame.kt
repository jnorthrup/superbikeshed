package nexus.data

import borg.trikeshed.lib.*

/**
 * TrikeShed DataFrame - A cursor-based, immutable data processing framework
 * 
 * Combines TrikeShed's compositional patterns with DataFrame operations,
 * providing integration points for Pandas (Python), Spark, and Hadoop.
 * 
 * Core concept: DatabaseCursor = Indexed<RowVec>
 * where RowVec = Indexed<Join<Any?, () -> String>>
 */

// Type aliases for clarity
typealias ColumnName = String
typealias ColumnType = String  
typealias CellValue = Any?
typealias CellFormatter = () -> String
typealias Cell = Join<CellValue, CellFormatter>
typealias Row = Indexed<Cell>
typealias DataFrame = Indexed<Row>
typealias Schema = Indexed<Join<ColumnName, ColumnType>>

/**
 * TrikeShed DataFrame with cursor-based operations
 */
class TrikeShedDataFrame(
    private val data: DataFrame,
    private val schema: Schema,
    private val cursor: DataCursor = DataCursor(0, data.size)
) {
    
    /**
     * Data cursor for efficient navigation and windowing
     */
    data class DataCursor(
        val position: Int,
        val limit: Int,
        val windowSize: Int = 100,
        val filters: Indexed<(Row) -> Boolean> = Indexed.empty()
    )
    
    companion object {
        /**
         * Create DataFrame from column-oriented data
         */
        fun fromColumns(vararg columns: Pair<String, Indexed<Any?>>): TrikeShedDataFrame {
            require(columns.isNotEmpty()) { "At least one column required" }
            
            val numRows = columns.first().second.size
            require(columns.all { it.second.size == numRows }) { "All columns must have same size" }
            
            val schema: Schema = columns.size j { i ->
                columns[i].first j inferType(columns[i].second)
            }
            
            val data: DataFrame = numRows j { rowIdx ->
                columns.size j { colIdx ->
                    val value = columns[colIdx].second[rowIdx]
                    value j { value?.toString() ?: "null" }
                }
            }
            
            return TrikeShedDataFrame(data, schema)
        }
        
        /**
         * Create from row-oriented data
         */
        fun fromRows(schema: Schema, rows: Indexed<Indexed<Any?>>): TrikeShedDataFrame {
            val data: DataFrame = rows.size j { rowIdx ->
                schema.size j { colIdx ->
                    val value = rows[rowIdx][colIdx]
                    value j { value?.toString() ?: "null" }
                }
            }
            return TrikeShedDataFrame(data, schema)
        }
        
        private fun inferType(column: Indexed<Any?>): String {
            // Simple type inference
            val sample = column.toList().firstOrNull { it != null } ?: return "null"
            return when (sample) {
                is Int -> "int"
                is Long -> "long"
                is Double -> "double"
                is Float -> "float"
                is Boolean -> "boolean"
                is String -> "string"
                else -> "object"
            }
        }
    }
    
    /**
     * Cursor operations
     */
    fun seek(position: Int): TrikeShedDataFrame {
        return copy(cursor = cursor.copy(position = position.coerceIn(0, data.size)))
    }
    
    fun next(count: Int = 1): TrikeShedDataFrame {
        return seek(cursor.position + count)
    }
    
    fun previous(count: Int = 1): TrikeShedDataFrame {
        return seek(cursor.position - count)
    }
    
    fun window(size: Int): TrikeShedDataFrame {
        return copy(cursor = cursor.copy(windowSize = size))
    }
    
    /**
     * Get current window of data
     */
    fun currentWindow(): DataFrame {
        val start = cursor.position
        val end = (start + cursor.windowSize).coerceAtMost(data.size)
        
        return (end - start) j { i ->
            data[start + i]
        }
    }
    
    /**
     * DataFrame operations
     */
    fun select(vararg columns: String): TrikeShedDataFrame {
        val columnIndices = columns.map { colName ->
            schema.toList().indexOfFirst { it.a == colName }
        }.filter { it >= 0 }
        
        val newSchema: Schema = columnIndices.size j { i ->
            schema[columnIndices[i]]
        }
        
        val newData: DataFrame = data.size j { rowIdx ->
            columnIndices.size j { colIdx ->
                data[rowIdx][columnIndices[colIdx]]
            }
        }
        
        return TrikeShedDataFrame(newData, newSchema)
    }
    
    fun filter(predicate: (Row) -> Boolean): TrikeShedDataFrame {
        val newFilters = cursor.filters.append(predicate)
        return copy(cursor = cursor.copy(filters = newFilters))
    }
    
    fun map(transform: (Row) -> Row): TrikeShedDataFrame {
        val newData: DataFrame = data.size j { i ->
            transform(data[i])
        }
        return TrikeShedDataFrame(newData, schema, cursor)
    }
    
    fun groupBy(column: String): GroupedDataFrame {
        val colIdx = schema.toList().indexOfFirst { it.a == column }
        require(colIdx >= 0) { "Column $column not found" }
        
        // Group rows by column value
        val groups = mutableMapOf<Any?, MutableList<Int>>()
        for (i in 0 until data.size) {
            val key = data[i][colIdx].a
            groups.getOrPut(key) { mutableListOf() }.add(i)
        }
        
        return GroupedDataFrame(this, groups, column)
    }
    
    /**
     * Integration with external systems
     */
    fun toPandasScript(): String {
        // Generate Python script for Pandas
        val columnNames = schema.toList().map { it.a }
        val rows = data.toList().map { row ->
            row.toList().map { it.a }
        }
        
        return """
import pandas as pd

# Generated from TrikeShed DataFrame
data = {
${columnNames.mapIndexed { idx, name ->
    "    '$name': [${rows.map { it[idx] }.joinToString(", ") { 
        when (it) {
            is String -> "'$it'"
            null -> "None"
            else -> it.toString()
        }
    }}]"
}.joinToString(",\n")}
}

df = pd.DataFrame(data)
        """.trimIndent()
    }
    
    fun toSparkRDD(): String {
        // Generate Spark RDD creation code
        val rows = data.toList().map { row ->
            "(${row.toList().map { it.a }.joinToString(", ") { 
                when (it) {
                    is String -> "\"$it\""
                    null -> "null"
                    else -> it.toString()
                }
            }})"
        }
        
        return """
// Spark RDD from TrikeShed DataFrame
val rdd = spark.sparkContext.parallelize(Seq(
${rows.joinToString(",\n    ")}
))

val df = rdd.toDF(${schema.toList().map { "\"${it.a}\"" }.joinToString(", ")})
        """.trimIndent()
    }
    
    fun toHadoopWritable(): String {
        // Generate Hadoop Writable format
        return """
// Hadoop Writable format
${data.toList().mapIndexed { idx, row ->
    val values = row.toList().map { it.a }
    "new Text(\"${values.joinToString("\t")}\")"
}.joinToString("\n")}
        """.trimIndent()
    }
    
    /**
     * Aggregation operations
     */
    fun sum(column: String): Double? {
        val colIdx = schema.toList().indexOfFirst { it.a == column }
        if (colIdx < 0) return null
        
        return data.toList().mapNotNull { row ->
            (row[colIdx].a as? Number)?.toDouble()
        }.sum()
    }
    
    fun count(): Int = data.size
    
    fun mean(column: String): Double? {
        val colIdx = schema.toList().indexOfFirst { it.a == column }
        if (colIdx < 0) return null
        
        val values = data.toList().mapNotNull { row ->
            (row[colIdx].a as? Number)?.toDouble()
        }
        
        return if (values.isNotEmpty()) values.average() else null
    }
    
    /**
     * Display operations
     */
    fun show(limit: Int = 10) {
        println("TrikeShed DataFrame [${data.size} rows x ${schema.size} columns]")
        println("=" * 80)
        
        // Header
        println(schema.toList().map { it.a }.joinToString(" | "))
        println("-" * 80)
        
        // Data
        val displayLimit = limit.coerceAtMost(data.size)
        for (i in 0 until displayLimit) {
            println(data[i].toList().map { it.b() }.joinToString(" | "))
        }
        
        if (displayLimit < data.size) {
            println("... ${data.size - displayLimit} more rows")
        }
    }
    
    private fun copy(
        data: DataFrame = this.data,
        schema: Schema = this.schema,
        cursor: DataCursor = this.cursor
    ) = TrikeShedDataFrame(data, schema, cursor)
}

/**
 * Grouped DataFrame for aggregation operations
 */
class GroupedDataFrame(
    private val source: TrikeShedDataFrame,
    private val groups: Map<Any?, List<Int>>,
    private val groupColumn: String
) {
    fun agg(vararg operations: Pair<String, (Indexed<Any?>) -> Any?>): TrikeShedDataFrame {
        // Perform aggregations
        val results = mutableListOf<Indexed<Any?>>()
        
        // Add group column
        results.add(groups.keys.toList().toIdx())
        
        // Perform each aggregation
        // ... aggregation logic ...
        
        return TrikeShedDataFrame.fromColumns(
            groupColumn to results[0],
            // ... other columns ...
        )
    }
}

// Extension to convert lists to Indexed
private fun <T> List<T>.toIdx(): Indexed<T> = this.size j { this[it] }

private operator fun String.times(n: Int) = this.repeat(n)
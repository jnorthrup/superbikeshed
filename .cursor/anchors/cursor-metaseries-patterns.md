# Cursor Metaseries Patterns - Coordinate-Based Wireproto

## The Insight: Cursor as Coordinate-Based Metaseries

### The Problem
- **Wireproto expression of columns** is expensive and verbose
- **LJSON** provides lightweight JSON but still needs column expression
- **Traditional approaches** fight each column individually
- **Metaseries patterns** need lightweight coordinate representation

### The Solution: Cursor Coordinates as Metaseries
- **Cursor coordinates** provide lightweight column addressing
- **Coordinate-based metaseries** eliminates wireproto overhead
- **LJSON + cursor coordinates** = lightweight column expression
- **Series operations** work over coordinate space, not column names

## Cursor Coordinate Metaseries Patterns

### 1. Coordinate-Based Column Access
```kotlin
// ❌ WRONG - Expensive wireproto column expression
data class ColumnExpression(
    val name: String,
    val type: String,
    val path: List<String>,
    val wireproto: String // Expensive!
)

// ✅ RIGHT - Lightweight coordinate-based metaseries
data class CursorCoordinate(
    val row: Int,
    val col: Int,
    val offset: Long
)

// LJSON with cursor coordinates
data class LJsonCursor(
    val data: ByteArray,
    val coordinates: Indexed<CursorCoordinate>
)
```

### 2. Coordinate Series Operations
```kotlin
// Metaseries over coordinate space
fun processCoordinateSeries(
    data: LJsonCursor,
    transform: (ByteArray, CursorCoordinate) -> ByteArray
): LJsonCursor {
    return LJsonCursor(
        data = data.data,
        coordinates = Indexed(data.coordinates.a) { i ->
            val coord = data.coordinates.b(i)
            val transformed = transform(data.data, coord)
            coord.copy() // Coordinate remains, data transformed
        }
    )
}

// Usage: Transform entire series at coordinate level
val processed = processCoordinateSeries(ljsonCursor) { data, coord ->
    // Transform data at specific coordinate
    // No wireproto overhead, just coordinate math
}
```

### 3. LJSON + Cursor Coordinate Integration
```kotlin
// Lightweight column expression through coordinates
sealed class LJsonColumn {
    data class ByCoordinate(val coord: CursorCoordinate) : LJsonColumn()
    data class ByRange(val start: CursorCoordinate, val end: CursorCoordinate) : LJsonColumn()
    data class ByPattern(val pattern: (CursorCoordinate) -> Boolean) : LJsonColumn()
}

// Metaseries over LJSON columns
fun extractLJsonSeries(
    data: LJsonCursor,
    columns: Indexed<LJsonColumn>
): Indexed<ByteArray> {
    return Indexed(columns.a) { i ->
        val column = columns.b(i)
        when (column) {
            is LJsonColumn.ByCoordinate -> extractAtCoordinate(data, column.coord)
            is LJsonColumn.ByRange -> extractRange(data, column.start, column.end)
            is LJsonColumn.ByPattern -> extractPattern(data, column.pattern)
        }
    }
}
```

## Coordinate-Based Metaseries Benefits

### 1. Wireproto Cost Reduction
- **No expensive column name serialization**
- **Coordinate math instead of string matching**
- **LJSON provides lightweight data format**
- **Series operations work over coordinate space**

### 2. Performance Advantages
- **Coordinate-based addressing** is O(1) vs O(n) string matching
- **Batch coordinate operations** enable SIMD optimization
- **Memory locality** through coordinate clustering
- **No string allocations** in coordinate space

### 3. Metaseries Composition
```kotlin
// Compose coordinate transformations
fun composeCoordinateSeries(
    transforms: Indexed<(CursorCoordinate) -> CursorCoordinate>
): (CursorCoordinate) -> CursorCoordinate {
    return { coord ->
        transforms.fold(coord) { acc, transform -> transform(acc) }
    }
}

// Usage: Complex coordinate metaseries
val complexTransform = composeCoordinateSeries(Indexed(3) { i ->
    when (i) {
        0 -> { coord -> coord.copy(row = coord.row + 1) }
        1 -> { coord -> coord.copy(col = coord.col * 2) }
        2 -> { coord -> coord.copy(offset = coord.offset + 100) }
        else -> { coord -> coord }
    }
})
```

## LJSON + Cursor Coordinate Integration

### 1. Lightweight Column Expression
```kotlin
// Traditional wireproto approach
val wireprotoColumns = """
{
  "columns": [
    {"name": "user_id", "type": "int", "path": ["data", "user", "id"]},
    {"name": "email", "type": "string", "path": ["data", "user", "email"]}
  ]
}
"""

// LJSON + cursor coordinate approach
val ljsonCoordinates = LJsonCursor(
    data = ljsonBytes,
    coordinates = Indexed(2) { i ->
        when (i) {
            0 -> CursorCoordinate(row = 0, col = 0, offset = 0x100)
            1 -> CursorCoordinate(row = 0, col = 1, offset = 0x200)
            else -> CursorCoordinate(0, 0, 0)
        }
    }
)
```

### 2. Series Operations Over Coordinates
```kotlin
// Metaseries over coordinate space
fun transformLJsonSeries(
    cursor: LJsonCursor,
    coordinateTransform: (CursorCoordinate) -> CursorCoordinate
): LJsonCursor {
    return cursor.copy(
        coordinates = Indexed(cursor.coordinates.a) { i ->
            coordinateTransform(cursor.coordinates.b(i))
        }
    )
}

// Usage: Transform entire LJSON series
val transformed = transformLJsonSeries(ljsonCursor) { coord ->
    coord.copy(row = coord.row + 1, col = coord.col * 2)
}
```

## The Coordinate Metaseries Mantra
**"Coordinates are the lightweight metaseries. LJSON + coordinates = wireproto at a discount."** 
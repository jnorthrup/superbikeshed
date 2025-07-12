# ADR-004: Cursor Coordinate-Based Metaseries

## Status
Accepted

## Context
Wireproto expression of columns is expensive and verbose. LJSON provides lightweight JSON but still needs column expression. Traditional approaches fight each column individually, creating overhead and complexity.

## Decision
Use cursor coordinates as a lightweight coordinate-based metaseries for column expression. LJSON + cursor coordinates provides wireproto expression at a fraction of the cost.

## Consequences

### Positive
- **Eliminates expensive wireproto column serialization**
- **Coordinate-based addressing** is O(1) vs O(n) string matching
- **LJSON provides lightweight data format** with coordinate addressing
- **Series operations work over coordinate space** instead of column names
- **Enables SIMD optimization** for batch coordinate operations
- **No string allocations** in coordinate space

### Negative
- Requires coordinate management and mapping
- May be less intuitive than named columns
- Need to maintain coordinate-to-column mapping

## Implementation
- **CursorCoordinate**: Lightweight coordinate representation (row, col, offset)
- **LJsonCursor**: LJSON data with coordinate-based addressing
- **Coordinate Series Operations**: Transform operations over coordinate space
- **LJSON + Coordinates**: Lightweight column expression without wireproto overhead

## Examples
- **Coordinate-Based Access**: `CursorCoordinate(row: Int, col: Int, offset: Long)`
- **LJSON Integration**: `LJsonCursor(data: ByteArray, coordinates: Indexed<CursorCoordinate>)`
- **Series Operations**: `processCoordinateSeries(data: LJsonCursor, transform: (ByteArray, CursorCoordinate) -> ByteArray)`

## Related
- Cursor metaseries patterns: `.cursor/anchors/cursor-metaseries-patterns.md`
- Metaseries patterns: ADR-003
- LJSON integration: trikeshed-ljson module 
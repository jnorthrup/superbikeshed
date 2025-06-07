import { CoreTensorCursorWithMeta, IntRange, TypeMemento, IOMementoType } from './types';
import { cursorRows, cursorCols, cursorMetaNames } from './cursor';
import { getTensorValue } from './tensor';

export function toDisplayString(value: any, type: TypeMemento): string {
    if (value === null || value === undefined) {
        return "null";
    }
    
    switch (type.typeName) {
        case IOMementoType.IoString:
        case IOMementoType.IoChar:
            return String(value);
        case IOMementoType.IoInt:
        case IOMementoType.IoLong:
        case IOMementoType.IoFloat:
        case IOMementoType.IoDouble:
            return value.toString();
        case IOMementoType.IoBoolean:
            return value ? "true" : "false";
        case IOMementoType.IoCharSeries:
            return value;
        case IOMementoType.IoBigDecimal:
        case IOMementoType.IoBigInt:
            return value.toString();
        case IOMementoType.IoDateTime:
            return value instanceof Date ? value.toISOString() : String(value);
        case IOMementoType.IoDuration:
            return String(value);
        case IOMementoType.IoUUID:
            return String(value);
        case IOMementoType.IoBinary:
            return value instanceof Uint8Array ? value.toString() : String(value);
        default:
            return String(value);
    }
}

export function head<T>(ctcm: CoreTensorCursorWithMeta<T>, count: number = 5): void {
    const range: IntRange = { first: 0, last: Math.min(count - 1, cursorRows(ctcm.a) - 1) };
    show(ctcm, range);
}

export function showRandom<T>(ctcm: CoreTensorCursorWithMeta<T>, n: number = 5): void {
    const rows = cursorRows(ctcm.a);
    if (rows === 0) {
        console.log("Empty cursor");
        return;
    }
    
    const indices = new Set<number>();
    while (indices.size < Math.min(n, rows)) {
        indices.add(Math.floor(Math.random() * rows));
    }
    
    const sortedIndices = Array.from(indices).sort((a, b) => a - b);
    const range: IntRange = { first: sortedIndices[0], last: sortedIndices[sortedIndices.length - 1] };
    show(ctcm, range);
}

export function show<T>(ctcm: CoreTensorCursorWithMeta<T>, range?: IntRange): void {
    const cursor = ctcm.a;
    const meta = ctcm.b;
    const rows = cursorRows(cursor);
    
    if (rows === 0) {
        console.log("Empty cursor");
        return;
    }
    
    const displayRange = range || { first: 0, last: Math.min(4, rows - 1) };
    if (displayRange.first < 0 || displayRange.last >= rows) {
        throw new RangeError("Range out of bounds");
    }
    
    showValues(ctcm, displayRange);
}

export function showValues<T>(ctcm: CoreTensorCursorWithMeta<T>, range: IntRange): void {
    const cursor = ctcm.a;
    const meta = ctcm.b;
    const cols = cursorCols(cursor);
    const names = cursorMetaNames(meta);
    
    // Calculate column widths
    const colWidths = new Array(cols).fill(0);
    for (let i = 0; i < cols; i++) {
        colWidths[i] = Math.max(names[i].length, 4);
    }
    
    // Print header
    let header = "| ";
    for (let i = 0; i < cols; i++) {
        header += names[i].padEnd(colWidths[i]) + " | ";
    }
    console.log(header);
    
    // Print separator
    let separator = "+-";
    for (let i = 0; i < cols; i++) {
        separator += "-".repeat(colWidths[i]) + "-+-";
    }
    separator = separator.slice(0, -1);
    console.log(separator);
    
    // Print rows
    for (let i = range.first; i <= range.last; i++) {
        let row = "| ";
        for (let j = 0; j < cols; j++) {
            const value = getTensorValue(cursor, [i, j]);
            const type = getTensorValue(meta, [0, j]);
            const displayValue = toDisplayString(value, type);
            row += displayValue.padEnd(colWidths[j]) + " | ";
        }
        console.log(row);
    }
}

export function isNumerical<T>(ctcm: CoreTensorCursorWithMeta<T>): boolean {
    const meta = ctcm.b;
    const cols = cursorCols(ctcm.a);
    
    for (let i = 0; i < cols; i++) {
        const type = getTensorValue(meta, [0, i]);
        switch (type.typeName) {
            case IOMementoType.IoInt:
            case IOMementoType.IoLong:
            case IOMementoType.IoFloat:
            case IOMementoType.IoDouble:
            case IOMementoType.IoBigDecimal:
            case IOMementoType.IoBigInt:
                continue;
            default:
                return false;
        }
    }
    
    return true;
}

export function isHomomorphic<T>(ctcm: CoreTensorCursorWithMeta<T>): boolean {
    const meta = ctcm.b;
    const cols = cursorCols(ctcm.a);
    if (cols === 0) return true;
    
    const firstType = getTensorValue(meta, [0, 0]).typeName;
    for (let i = 1; i < cols; i++) {
        if (getTensorValue(meta, [0, i]).typeName !== firstType) {
            return false;
        }
    }
    
    return true;
} 
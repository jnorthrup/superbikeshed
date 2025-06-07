import { CoreTensorCursor, CoreTensorCursorWithMeta, CoreTensorRowVec, CoreTensorColumnVec, CursorMeta, ColumnMeta, IntRange, Series } from './types';
import { j } from './join';
import { getSeriesValue } from './series';
import { getTensorValue, tensorRank } from './tensor';

export function cursorRows<T>(cursor: CoreTensorCursor<T>): number {
    if (tensorRank(cursor) !== 2) {
        throw new Error("Cursor must be rank 2");
    }
    return cursor.shape[0];
}

export function cursorCols<T>(cursor: CoreTensorCursor<T>): number {
    if (tensorRank(cursor) !== 2) {
        throw new Error("Cursor must be rank 2");
    }
    return cursor.shape[1];
}

export function getRow<T>(cursor: CoreTensorCursor<T>, rowIndex: number): CoreTensorRowVec<T> {
    if (rowIndex < 0 || rowIndex >= cursorRows(cursor)) {
        throw new RangeError(`Row index ${rowIndex} out of bounds`);
    }
    
    return {
        shape: [cursorCols(cursor)],
        stride: [1],
        data: cursor.data.slice(rowIndex * cursorCols(cursor), (rowIndex + 1) * cursorCols(cursor))
    };
}

export function getCol<T>(cursor: CoreTensorCursor<T>, colIndex: number): CoreTensorColumnVec<T> {
    if (colIndex < 0 || colIndex >= cursorCols(cursor)) {
        throw new RangeError(`Column index ${colIndex} out of bounds`);
    }
    
    const result: T[] = [];
    for (let i = 0; i < cursorRows(cursor); i++) {
        result.push(getTensorValue(cursor, [i, colIndex]));
    }
    
    return {
        shape: [cursorRows(cursor)],
        stride: [1],
        data: result
    };
}

export function sliceCursorByRowRange<T>(cursor: CoreTensorCursor<T>, rowRange: IntRange): CoreTensorCursor<T> {
    if (rowRange.first < 0 || rowRange.last >= cursorRows(cursor)) {
        throw new RangeError("Row range out of bounds");
    }
    
    const rows = rowRange.last - rowRange.first + 1;
    const result: T[] = [];
    
    for (let i = rowRange.first; i <= rowRange.last; i++) {
        for (let j = 0; j < cursorCols(cursor); j++) {
            result.push(getTensorValue(cursor, [i, j]));
        }
    }
    
    return {
        shape: [rows, cursorCols(cursor)],
        stride: [cursorCols(cursor), 1],
        data: result
    };
}

export function sliceCursorByColumnIndices<T>(cursor: CoreTensorCursor<T>, ...colIndices: number[]): CoreTensorCursor<T> {
    const result: T[] = [];
    
    for (let i = 0; i < cursorRows(cursor); i++) {
        for (const colIndex of colIndices) {
            if (colIndex < 0 || colIndex >= cursorCols(cursor)) {
                throw new RangeError(`Column index ${colIndex} out of bounds`);
            }
            result.push(getTensorValue(cursor, [i, colIndex]));
        }
    }
    
    return {
        shape: [cursorRows(cursor), colIndices.length],
        stride: [colIndices.length, 1],
        data: result
    };
}

export function sliceCursorByColumnSeries<T>(cursor: CoreTensorCursor<T>, colIndicesSeries: Series<number>): CoreTensorCursor<T> {
    const result: T[] = [];
    const numCols = colIndicesSeries.a;
    
    for (let i = 0; i < cursorRows(cursor); i++) {
        for (let j = 0; j < numCols; j++) {
            const colIndex = getSeriesValue(colIndicesSeries, j);
            if (colIndex < 0 || colIndex >= cursorCols(cursor)) {
                throw new RangeError(`Column index ${colIndex} out of bounds`);
            }
            result.push(getTensorValue(cursor, [i, colIndex]));
        }
    }
    
    return {
        shape: [cursorRows(cursor), numCols],
        stride: [numCols, 1],
        data: result
    };
}

export function columnName(colMeta: ColumnMeta): string {
    return colMeta.a;
}

export function columnType(colMeta: ColumnMeta): TypeMemento {
    return colMeta.b;
}

export function coreTensorMeta<T>(ctcm: CoreTensorCursorWithMeta<T>): CursorMeta {
    return ctcm.b;
}

export function cursorMetaNames(cm: CursorMeta): string[] {
    const result: string[] = [];
    for (let i = 0; i < cm.shape[1]; i++) {
        result.push(columnName(getTensorValue(cm, [0, i])));
    }
    return result;
}

export class ColumnExclusion {
    constructor(public readonly name: string) {}
    toString(): string { return `ColumnExclusion(${this.name})`; }
}

export function excludeByName(name: string): ColumnExclusion {
    return new ColumnExclusion(name);
}

export function excludeColumnsByIndices<T>(
    ctcm: CoreTensorCursorWithMeta<T>,
    indicesToExcludeSeries: Series<number>
): CoreTensorCursorWithMeta<T> {
    const meta = coreTensorMeta(ctcm);
    const cursor = ctcm.a;
    const numCols = cursorCols(cursor);
    const excludeIndices = new Set<number>();
    
    for (let i = 0; i < indicesToExcludeSeries.a; i++) {
        const index = getSeriesValue(indicesToExcludeSeries, i);
        if (index < 0 || index >= numCols) {
            throw new RangeError(`Column index ${index} out of bounds`);
        }
        excludeIndices.add(index);
    }
    
    const keepIndices: number[] = [];
    for (let i = 0; i < numCols; i++) {
        if (!excludeIndices.has(i)) {
            keepIndices.push(i);
        }
    }
    
    return j(
        sliceCursorByColumnIndices(cursor, ...keepIndices),
        sliceCursorByColumnIndices(meta, ...keepIndices)
    );
}

export function excludeColumnsByName<T>(
    ctcm: CoreTensorCursorWithMeta<T>,
    exclusionsSeries: Series<ColumnExclusion>
): CoreTensorCursorWithMeta<T> {
    const meta = coreTensorMeta(ctcm);
    const cursor = ctcm.a;
    const numCols = cursorCols(cursor);
    const excludeNames = new Set<string>();
    
    for (let i = 0; i < exclusionsSeries.a; i++) {
        excludeNames.add(getSeriesValue(exclusionsSeries, i).name);
    }
    
    const keepIndices: number[] = [];
    for (let i = 0; i < numCols; i++) {
        if (!excludeNames.has(columnName(getTensorValue(meta, [0, i])))) {
            keepIndices.push(i);
        }
    }
    
    return j(
        sliceCursorByColumnIndices(cursor, ...keepIndices),
        sliceCursorByColumnIndices(meta, ...keepIndices)
    );
}

export function selectColumnsByName<T>(
    ctcm: CoreTensorCursorWithMeta<T>,
    ...columnNames: string[]
): CoreTensorCursorWithMeta<T> {
    const meta = coreTensorMeta(ctcm);
    const cursor = ctcm.a;
    const numCols = cursorCols(cursor);
    const nameToIndex = new Map<string, number>();
    
    for (let i = 0; i < numCols; i++) {
        nameToIndex.set(columnName(getTensorValue(meta, [0, i])), i);
    }
    
    const keepIndices: number[] = [];
    for (const name of columnNames) {
        const index = nameToIndex.get(name);
        if (index === undefined) {
            throw new Error(`Column ${name} not found`);
        }
        keepIndices.push(index);
    }
    
    return j(
        sliceCursorByColumnIndices(cursor, ...keepIndices),
        sliceCursorByColumnIndices(meta, ...keepIndices)
    );
} 
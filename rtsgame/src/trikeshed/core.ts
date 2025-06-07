// src/trikeshed/core.ts

// Suppressing TSLint/ESLint rules similar to Kotlin's @file:Suppress
// For TSLint (deprecated but common in older TS projects):
// tslint:disable:interface-name function-name object-literal-sort-keys no-any non-literal-fs-path prefer-for-of no-unnecessary-type-assertion no-shadowed-variable

// For ESLint (modern standard):
/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/interface-name-prefix */ // For IJoin if we used I prefix
/* eslint-disable @typescript-eslint/class-name-casing */ // For _Join if we used class _Join
/* eslint-disable no-shadow */ // For potential name shadowing if simple names are used
/* eslint-disable @typescript-eslint/no-unused-vars */ // For generic types that might not be used in all base definitions

// I. Trikeshed Essentials: Join and Series Primitives

/**
 * Interface representing a fundamental Join operation, similar to a Pair but with named `a` and `b` components.
 */
export interface Join<A, B> {
    readonly a: A;
    readonly b: B;
    // component1 and component2 are idiomatic in Kotlin for destructuring
    // In TypeScript, direct property access or object destructuring is common: const { a, b } = myJoin;
}

/**
 * Creates a Join instance. This is the primary construction mechanism.
 * Kotlin's infix fun <A, B> A.j(b: B): Join<A, B> translates to a function.
 */
export function j<A, B>(firstParam: A, secondParam: B): Join<A, B> {
    return { a: firstParam, b: secondParam };
}

/** Accessor for the first element of a Join. */
export function first<A, B>(join: Join<A, B>): A {
    return join.a;
}

/** Accessor for the second element of a Join. */
export function second<A, B>(join: Join<A, B>): B {
    return join.b;
}

/**
 * Type alias for a Join where both elements are of the same type.
 */
export type Twin<T> = Join<T, T>;

/**
 * Factory function to create a Twin from a single value.
 */
export function twin<T>(value: T): Twin<T> {
    return j(value, value);
}

/**
 * Type alias for a Series, which is a Join of a size (number) and an accessor function.
 * This represents a lazily-evaluated sequence of elements indexed by an integer.
 */
export type Series<T> = Join<number, (index: number) => T>;

/** Returns the size of the Series. */
export function size<T>(series: Series<T>): number {
    return series.a;
}

/**
 * Operator to access an element of the Series by its index.
 * Kotlin's operator fun <T> Series<T>.get(i: Int): T translates to a function.
 */
export function getSeriesValue<T>(series: Series<T>, i: number): T {
    if (i < 0 || i >= size(series)) {
        throw new RangeError(`Index ${i} out of bounds for Series of size ${size(series)}`);
    }
    return series.b(i);
}

/**
 * An empty Series instance.
 * Note: Typing `EmptySeries` as `Series<any>` or `Series<unknown>` is safer.
 * Casting to `Series<T>` should be done carefully by the caller.
 */
export const EmptySeries: Series<any> = j(0, (_index: number) => {
    throw new Error("Cannot access elements of an empty series.");
});

/**
 * Factory function to create an empty Series.
 */
export function emptySeries<T>(): Series<T> {
    return EmptySeries as Series<T>;
}

/**
 * Creates a lazy supplier (a lambda with no arguments) that returns `this` value.
 * Kotlin's inline val <T> T.leftIdentity: () -> T translates to a function.
 */
export function leftIdentity<T>(value: T): () => T {
    return () => value;
}

/**
 * Syntactic sugar for leftIdentity.
 * Kotlin's `↺` is not a valid identifier in JS/TS.
 */
export function circularRef<T>(value: T): () => T {
    return leftIdentity(value);
}

/**
 * A class wrapper around Series that makes it Iterable.
 * Kotlin's value class IterableSeries<A>(val s: Series<A>)
 */
export class IterableSeries<A> implements Iterable<A>, Series<A> {
    readonly a: number; // size
    readonly b: (index: number) => A; // accessor

    constructor(public readonly series: Series<A>) {
        this.a = size(series);
        this.b = series.b;
    }

    [Symbol.iterator](): Iterator<A> {
        let index = 0;
        const currentSeries = this.series; // Capture series instance for the iterator
        return {
            next: (): IteratorResult<A> => {
                if (index < size(currentSeries)) {
                    return { value: getSeriesValue(currentSeries, index++), done: false };
                } else {
                    return { value: undefined as any, done: true }; // `value: undefined` is fine for done:true
                }
            },
        };
    }

    // Series interface implementation
    get size(): number { return this.a; }
    get(i: number): A { return getSeriesValue(this.series, i); }
}

/**
 * Provides an Iterable view of the Series.
 * Kotlin's `▶` (materialize) becomes a function.
 */
export function materializeSeries<T>(series: Series<T>): IterableSeries<T> {
    return new IterableSeries(series);
}

/**
 * Extension function to convert a Series of Char to a String.
 * In TypeScript, this would be a standalone function.
 */
export function seriesCharToString(series: Series<string>): string {
    // Assuming Series<string> where each string is a single character
    let result = "";
    for (const char of materializeSeries(series)) {
        result += char;
    }
    return result;
}


// III. core.Tensor Implementation

/**
 * Type alias for a Tensor, which is a Join of its shape (number[]) and an accessor function
 * that takes coordinates (number[]) and returns an element.
 */
export type Tensor<T> = {
    data: T[];
    shape: number[];
    stride: number[];
};

/** Returns the shape of the Tensor. */
export function tensorShape<T>(tensor: Tensor<T>): number[] {
    return tensor.shape;
}
/** Returns the accessor function of the Tensor. */
export function tensorAccessor<T>(tensor: Tensor<T>): (coords: number[]) => T {
    return (coords: number[]) => TensorOps.get(tensor, coords);
}

// Syntactic sugar often directly uses the more verbose names in TS if they are clear enough,
// or short forms are provided as simple const/functions.
export const shape = tensorShape;
export const accessor = tensorAccessor;


/** Returns the rank (number of dimensions) of the Tensor. */
export function tensorRank<T>(tensor: Tensor<T>): number {
    return tensorShape(tensor).length;
}
export const rank = tensorRank;

/** Returns the total number of elements in the Tensor. */
export function tensorTotalSize<T>(tensor: Tensor<T>): number {
    const s = tensorShape(tensor);
    if (s.length === 0) return 0; // Or 1 for a scalar if shape is like [] meaning one value
                                   // Kotlin code implies 0 for empty shape.
    return s.reduce((acc, i) => acc * i, 1); // Start with 1 for multiplication identity
}
export const totalSize = tensorTotalSize;

/**
 * Constructs a Tensor from a given shape and accessor function.
 */
export function TensorConstruct<T>(shape: number[], accessor: (coords: number[]) => T): Tensor<T> {
    const size = shape.reduce((a, b) => a * b, 1);
    const stride = shape.slice(1).reduceRight(
        (acc, dim) => [dim * acc[0], ...acc],
        [1]
    );
    
    return {
        data: new Array(size).fill(null),
        shape,
        stride
    };
}

/**
 * Constructs a 1-dimensional Tensor (a "Series" in this context) from a size and an accessor function.
 */
export function TensorSeries<T>(size: number, accessor: (index: number) => T): Tensor<T> {
    return TensorConstruct([size], (coords: number[]) => accessor(coords[0]));
}

/**
 * Constructs a 2-dimensional Tensor (a "Cursor") from rows, columns, and an accessor function.
 */
export function TensorCursor<T>(rows: number, cols: number, accessor: (row: number, col: number) => T): Tensor<T> {
    return TensorConstruct([rows, cols], (coords: number[]) => accessor(coords[0], coords[1]));
}

/**
 * Invokes the Tensor's accessor with the given coordinates array.
 */
export function getTensorValue<T>(tensor: Tensor<T>, coords: number[]): T {
    // Basic validation, more can be added (e.g., rank check, coord bounds)
    if (coords.length !== rank(tensor)) {
        throw new Error(`Coordinate rank mismatch: Tensor rank is ${rank(tensor)}, got ${coords.length} coordinates.`);
    }
    return TensorOps.get(tensor, coords);
}

/**
 * Invokes the Tensor's accessor with the given variable arguments for coordinates.
 * In TS, this is achieved using rest parameters.
 */
export function getTensorValueVarArgs<T>(tensor: Tensor<T>, ...coords: number[]): T {
    return getTensorValue(tensor, coords);
}

// For specific ranks, we can provide convenience functions like Kotlin's invoke operators.
// These are not direct operator overloads in TS.

/**
 * Gets a value from a 1-dimensional Tensor with a single coordinate.
 */
export function getTensorValueRank1<T>(tensor: Tensor<T>, i: number): T {
    if (rank(tensor) !== 1) {
        throw new Error(`Tensor is not rank 1. Current rank: ${rank(tensor)}`);
    }
    return TensorOps.get(tensor, [i]);
}

/**
 * Gets a value from a 2-dimensional Tensor with row and column coordinates.
 */
export function getTensorValueRank2<T>(tensor: Tensor<T>, i: number, j: number): T {
    if (rank(tensor) !== 2) {
        throw new Error(`Tensor is not rank 2. Current rank: ${rank(tensor)}`);
    }
    return TensorOps.get(tensor, [i, j]);
}

// End of Part 1 of translation

// File: src/trikeshed/core.ts (appending)

// IV. Core Tensor Operations

/**
 * Applies a transformation function element-wise to a Tensor, producing a new Tensor
 * with the same shape. This is an "alpha-conversion" operation.
 * Kotlin: infix fun <X, C> Tensor<X>.α(crossinline transform: (X) -> C): Tensor<C>
 */
export function alphaConvert<X, C>(tensor: Tensor<X>, transform: (value: X) => C): Tensor<C> {
    return TensorConstruct(tensorShape(tensor), (coords: number[]) => transform(getTensorValue(tensor, coords)));
}

/**
 * Applies a transformation function element-wise to a Series, producing a new Series.
 * Kotlin: inline infix fun <X, C> Series<X>.α(crossinline transform: (X) -> C): Series<C>
 */
export function alphaConvertSeries<X, C>(series: Series<X>, transform: (value: X) => C): Series<C> {
    return j(size(series), (i: number) => transform(getSeriesValue(series, i)));
}


/**
 * Converts a linear index into multi-dimensional coordinates based on the Tensor's shape.
 */
export function linearToCoords(tensor: Tensor<any>, linearIndex: number): number[] {
    const s = shape(tensor);
    const r = rank(tensor);
    const coords = new Array(r).fill(0);
    let remaining = linearIndex;

    if (r === 0 && linearIndex === 0) return []; // Scalar case, empty coords
    if (r === 0 && linearIndex !== 0) throw new RangeError("Linear index out of bounds for scalar tensor.");


    for (let i = r - 1; i >= 0; i--) {
        const dimSize = s[i];
        if (dimSize === 0 && remaining !== 0) throw new Error("Cannot map linear index in zero-sized dimension");
        if (dimSize === 0) { // If dimension size is 0, coordinate must be 0
            coords[i] = 0;
            continue;
        }
        coords[i] = remaining % dimSize;
        remaining = Math.floor(remaining / dimSize);
    }

    if (remaining !== 0) {
        // This indicates the linearIndex was too large for the tensor's total size.
        throw new RangeError(`Linear index ${linearIndex} is out of bounds for tensor with shape ${s.join(',')}. Remaining: ${remaining}`);
    }
    return coords;
}

/**
 * Converts multi-dimensional coordinates into a linear index based on the Tensor's shape.
 */
export function coordsToLinear(tensor: Tensor<any>, coords: number[]): number {
    const s = shape(tensor);
    const r = rank(tensor);

    if (r === 0 && coords.length === 0) return 0; // Scalar case
    if (r === 0 && coords.length !== 0) throw new Error("Coordinates provided for scalar tensor.");


    if (coords.length !== r) {
        throw new Error(`Coordinate rank mismatch: expected ${r}, got ${coords.length}`);
    }

    let linearIndex = 0;
    let multiplier = 1;
    for (let i = r - 1; i >= 0; i--) {
        const coordVal = coords[i];
        const dimSize = s[i];
        if (coordVal < 0 || coordVal >= dimSize) {
            throw new RangeError(`Coordinate out of bounds: coords[${i}]=${coordVal} for dimension ${i} with size ${dimSize}`);
        }
        linearIndex += coordVal * multiplier;
        multiplier *= dimSize;
    }
    return linearIndex;
}

/**
 * Materializes the entire content of a Tensor into a flat Array.
 * Note: This can be memory-intensive for large tensors.
 * The order of elements in the array is row-major (C-style).
 */
export function materialize<T>(tensor: Tensor<T>): T[] {
    const tSize = totalSize(tensor);
    if (tSize === 0 && shape(tensor).length > 0 && shape(tensor).some(d => d === 0) ) return []; // Tensor with a zero dimension
    if (tSize === 0 && shape(tensor).length === 0) return []; // True scalar but no elements (empty shape) - though totalSize might be 1 for scalar
                                                             // Kotlin code returns totalSize=0 for empty shape. So this should be okay.

    const arr: T[] = new Array(tSize);
    for (let i = 0; i < tSize; i++) {
        arr[i] = getTensorValue(tensor, linearToCoords(tensor, i));
    }
    return arr;
}


/**
 * Determines the broadcasted shape for two input shapes.
 * Dimensions are aligned from the right. A dimension can broadcast if it's equal or one of them is 1.
 */
export function broadcastShapes(shape1: number[], shape2: number[]): number[] {
    const maxRank = Math.max(shape1.length, shape2.length);
    const result = new Array(maxRank).fill(0);

    for (let i = 0; i < maxRank; i++) {
        const dim1 = (i < shape1.length) ? shape1[shape1.length - 1 - i] : 1;
        const dim2 = (i < shape2.length) ? shape2[shape2.length - 1 - i] : 1;

        if (dim1 !== dim2 && dim1 !== 1 && dim2 !== 1) {
            throw new Error(`Shapes are not broadcastable: [${shape1}] vs [${shape2}] at aligned index ${i}`);
        }
        result[maxRank - 1 - i] = Math.max(dim1, dim2);
    }
    return result;
}

/**
 * A helper function for broadcasting, as provided in the Kotlin summary.
 * Translates the logic from:
 * fun IntArray.broadcastTo(targetShape: IntArray): IntArray
 * This function calculates how a source shape (`thisShape`) needs to be adjusted or interpreted
 * to fit a `targetShape` during broadcasting operations.
 * The Kotlin version's logic was: "if sourceIndex < this.size && this[sourceIndex] < targetShape[i] then this[sourceIndex] else 0"
 * This seems to imply it's creating a coordinate mapping or mask.
 * A more common interpretation of `broadcastTo` for coordinates might be to adjust input coordinates
 * for a smaller shape to be valid for a larger broadcasted shape (e.g., if a dimension was 1, use 0, else use original coord).
 * Let's stick to the literal translation of the provided Kotlin logic.
 */
export function calculateBroadcastCoordinateMap(sourceShape: number[], targetShape: number[]): number[] {
    const result = new Array(targetShape.length).fill(0);
    const offset = targetShape.length - sourceShape.length;

    for (let i = 0; i < targetShape.length; i++) {
        if (i < offset) { // Target dimension is beyond source shape's rank (left-padded)
            result[i] = 0; // Or perhaps this should throw error or handle based on broadcasting rules if sourceShape[0] is not 1
        } else {
            const sourceIndex = i - offset;
            // Original Kotlin logic:
            // if (sourceIndex < sourceShape.length && sourceShape[sourceIndex] < targetShape[i]) { result[i] = sourceShape[sourceIndex]; } else { result[i] = 0; }
            // This logic seems unusual for typical broadcasting coordinate calculation.
            // A typical broadcasting coordinate adjustment would be:
            // if (sourceShape[sourceIndex] === 1) result[i] = 0; else result[i] = targetCoords[i];
            // Given the ambiguity, I will translate the provided Kotlin logic directly.
            if (sourceIndex < sourceShape.length && sourceShape[sourceIndex] < targetShape[i]) {
                result[i] = sourceShape[sourceIndex];
            } else {
                result[i] = 0;
            }
        }
    }
    return result;
    // IMPORTANT: The utility of this specific `broadcastTo` logic needs review.
    // The typical use of broadcasting in zip/combine is to make coordinates for the smaller tensor
    // effectively 0 for dimensions that were 1, or use the coordinate directly if the dimension matched.
}


/**
 * Adjusts coordinates for a tensor that has been broadcast to a target shape.
 * If a dimension in the original tensor was 1, the coordinate for that dimension becomes 0.
 * Otherwise, the coordinate from the target shape is used.
 * @param originalShape The shape of the tensor before broadcasting.
 * @param broadcastedCoords The coordinates in the context of the broadcasted shape.
 * @returns Coordinates suitable for accessing the original tensor.
 */
function getCoordsForBroadcastedTensor(originalShape: number[], broadcastedCoords: number[]): number[] {
    const resultCoords = new Array(originalShape.length).fill(0);
    const rankDiff = broadcastedCoords.length - originalShape.length;

    for (let i = 0; i < originalShape.length; i++) {
        const originalDimSize = originalShape[i];
        const broadcastedCoordValue = broadcastedCoords[i + rankDiff];
        if (originalDimSize === 1) {
            resultCoords[i] = 0;
        } else {
            resultCoords[i] = broadcastedCoordValue;
        }
    }
    return resultCoords;
}


/**
 * Zips two Tensors element-wise, creating a new Tensor of Join pairs.
 * The shapes are broadcasted if compatible.
 */
export function zip<A, B>(tensorA: Tensor<A>, tensorB: Tensor<B>): Tensor<Join<A, B>> {
    const broadcastedShapeResult = broadcastShapes(shape(tensorA), shape(tensorB));

    return TensorConstruct(broadcastedShapeResult, (coords: number[]) => {
        const valA = getTensorValue(tensorA, getCoordsForBroadcastedTensor(shape(tensorA), coords));
        const valB = getTensorValue(tensorB, getCoordsForBroadcastedTensor(shape(tensorB), coords));
        return j(valA, valB);
    });
}

/**
 * Combines two Tensors element-wise using a transformation function,
 * producing a new Tensor. Shapes are broadcasted if compatible.
 */
export function combine<A, B, C>(
    tensorA: Tensor<A>,
    tensorB: Tensor<B>,
    transform: (valA: A, valB: B) => C
): Tensor<C> {
    const broadcastedShapeResult = broadcastShapes(shape(tensorA), shape(tensorB));

    return TensorConstruct(broadcastedShapeResult, (coords: number[]) => {
        const valA = getTensorValue(tensorA, getCoordsForBroadcastedTensor(shape(tensorA), coords));
        const valB = getTensorValue(tensorB, getCoordsForBroadcastedTensor(shape(tensorB), coords));
        return transform(valA, valB);
    });
}

// End of Part 2 of translation (Core Tensor Operations)

// File: src/trikeshed/core.ts (appending)

// V. CoreTensorCursor Layer

/**
 * Type alias for a Tensor specialized to represent a Cursor (a 2D structure like a table).
 * It's expected to be a Tensor of rank 2.
 */
export type CoreTensorCursor<T> = Tensor<T>;

/**
 * Type alias for a 1-dimensional Tensor representing a row vector within a cursor.
 * It's expected to be a Tensor of rank 1.
 */
export type CoreTensorRowVec<T> = Tensor<T>;

/**
 * Type alias for a 1-dimensional Tensor representing a column vector within a cursor.
 * It's expected to be a Tensor of rank 1.
 */
export type CoreTensorColumnVec<T> = Tensor<T>;

// Forward declaration for ColumnMeta if not fully defined yet, or ensure it's imported/available
// Assuming ColumnMeta will be defined in section VI. For now, a placeholder if needed.
// export type ColumnMeta = Join<string, TypeMemento>; // Will be defined later

/**
 * Type alias for a Tensor holding ColumnMeta objects, representing the metadata for cursor columns.
 * This is a 1D Tensor where each element is a ColumnMeta.
 */
export type CursorMeta = Tensor<ColumnMeta>; // ColumnMeta to be defined in next section

/**
 * Type alias for a Join that combines a CoreTensorCursor (the data) with its CursorMeta (the schema).
 */
export type CoreTensorCursorWithMeta<T> = Join<CoreTensorCursor<T>, CursorMeta>;

/** Returns the number of rows in a CoreTensorCursor. */
export function cursorRows<T>(cursor: CoreTensorCursor<T>): number {
    const s = shape(cursor);
    if (s.length !== 2) throw new Error("Cursor must be rank 2 to have rows.");
    return s[0];
}

/** Returns the number of columns in a CoreTensorCursor. */
export function cursorCols<T>(cursor: CoreTensorCursor<T>): number {
    const s = shape(cursor);
    if (s.length !== 2) throw new Error("Cursor must be rank 2 to have columns.");
    return s[1];
}

/**
 * Extracts a row as a CoreTensorRowVec (rank-1 Tensor) from a 2-dimensional CoreTensorCursor.
 */
export function getRow<T>(cursor: CoreTensorCursor<T>, rowIndex: number): CoreTensorRowVec<T> {
    if (rank(cursor) !== 2) {
        throw new Error("Cursor must be rank 2 for row access.");
    }
    const numRows = cursorRows(cursor);
    if (rowIndex < 0 || rowIndex >= numRows) {
        throw new RangeError(`Row index ${rowIndex} out of bounds for cursor with ${numRows} rows.`);
    }
    const numCols = cursorCols(cursor);
    return TensorSeries(numCols, (colIdx: number) => getTensorValueRank2(cursor, rowIndex, colIdx));
}

/**
 * Extracts a column as a CoreTensorColumnVec (rank-1 Tensor) from a 2-dimensional CoreTensorCursor.
 */
export function getCol<T>(cursor: CoreTensorCursor<T>, colIndex: number): CoreTensorColumnVec<T> {
    if (rank(cursor) !== 2) {
        throw new Error("Cursor must be rank 2 for column access.");
    }
    const numCols = cursorCols(cursor);
    if (colIndex < 0 || colIndex >= numCols) {
        throw new RangeError(`Column index ${colIndex} out of bounds for cursor with ${numCols} columns.`);
    }
    const numRows = cursorRows(cursor);
    return TensorSeries(numRows, (rowIdx: number) => getTensorValueRank2(cursor, rowIdx, colIndex));
}

/**
 * Represents a simple integer range (inclusive start, exclusive end, like slice).
 * Or (inclusive start, inclusive end) if that matches Kotlin's IntRange more closely for this context.
 * Kotlin's `IntRange.last` is inclusive. So, `first` to `last` inclusive.
 */
export interface IntRange {
    first: number;
    last: number; // Inclusive
}

/**
 * Slices a CoreTensorCursor by a range of rows, returning a new CoreTensorCursor.
 * Kotlin: operator fun <T> CoreTensorCursor<T>.get(rowRange: IntRange): CoreTensorCursor<T>
 */
export function sliceCursorByRowRange<T>(cursor: CoreTensorCursor<T>, rowRange: IntRange): CoreTensorCursor<T> {
    if (rank(cursor) !== 2) {
        throw new Error("Cursor must be rank 2 for row range slicing.");
    }
    const numRows = cursorRows(cursor);
    if (rowRange.first < 0 || rowRange.last >= numRows || rowRange.first > rowRange.last) {
        throw new RangeError(`Row range {first: ${rowRange.first}, last: ${rowRange.last}} out of bounds for cursor with ${numRows} rows.`);
    }

    const newNumRows = rowRange.last - rowRange.first + 1;
    const numCols = cursorCols(cursor);

    return TensorCursor(newNumRows, numCols, (r: number, c: number) => {
        return getTensorValueRank2(cursor, rowRange.first + r, c);
    });
}

/**
 * Slices a CoreTensorCursor by specific column indices (varargs), returning a new CoreTensorCursor.
 * Kotlin: operator fun <T> CoreTensorCursor<T>.get(vararg colIndices: Int): CoreTensorCursor<T>
 */
export function sliceCursorByColumnIndices<T>(cursor: CoreTensorCursor<T>, ...colIndices: number[]): CoreTensorCursor<T> {
    if (rank(cursor) !== 2) {
        throw new Error("Cursor must be rank 2 for column indexing.");
    }
    const numCols = cursorCols(cursor);
    colIndices.forEach(ci => {
        if (ci < 0 || ci >= numCols) {
            throw new RangeError(`Column index ${ci} out of bounds for cursor with ${numCols} columns.`);
        }
    });

    const numRows = cursorRows(cursor);
    const newNumCols = colIndices.length;

    return TensorCursor(numRows, newNumCols, (r: number, c: number) => {
        return getTensorValueRank2(cursor, r, colIndices[c]);
    });
}

/**
 * Slices a CoreTensorCursor by specific column indices provided as a Series<number>.
 * Kotlin: operator fun <T> CoreTensorCursor<T>.get(colIndices: Series<Int>): CoreTensorCursor<T>
 */
export function sliceCursorByColumnSeries<T>(cursor: CoreTensorCursor<T>, colIndicesSeries: Series<number>): CoreTensorCursor<T> {
    if (rank(cursor) !== 2) {
        throw new Error("Cursor must be rank 2 for column indexing.");
    }
    const numCols = cursorCols(cursor);
    const indicesArray: number[] = [];
    // Materialize Series to array for easier processing and validation
    for (const index of materializeSeries(colIndicesSeries)) {
        if (index < 0 || index >= numCols) {
            throw new RangeError(`Column index ${index} from series out of bounds for cursor with ${numCols} columns.`);
        }
        indicesArray.push(index);
    }

    const numRows = cursorRows(cursor);
    const newNumCols = indicesArray.length;

    if (newNumCols === 0 && numRows > 0) { // Handle case of slicing to zero columns
        return TensorCursor(numRows, 0, (_r, _c) => {
            throw new Error("Cannot access data in a zero-column cursor");
        });
    }
    if (numRows === 0 && newNumCols > 0 ) { // Handle case of slicing a zero-row cursor
         return TensorCursor(0, newNumCols, (_r, _c) => {
            throw new Error("Cannot access data in a zero-row cursor");
        });
    }
     if (numRows === 0 && newNumCols === 0 ) { // Handle case of slicing a zero-row cursor to zero columns
         return TensorCursor(0, 0, (_r, _c) => {
            throw new Error("Cannot access data in a zero-row zero-column cursor");
        });
    }


    return TensorCursor(numRows, newNumCols, (r: number, c: number) => {
        return getTensorValueRank2(cursor, r, indicesArray[c]);
    });
}

// End of Part 3 of translation (CoreTensorCursor Layer)

// File: src/trikeshed/core.ts (appending)

// VI. Metadata Types

/**
 * Interface for type metadata, used within ColumnMeta.
 */
export interface TypeMemento {
    // In Kotlin, networkSize was nullable. In TS, optional property.
    readonly networkSize?: number;
    // Add a general type identifier, useful for TS.
    readonly typeName: string;
}

/**
 * Enum defining various I/O and data types used in the system,
 * implementing TypeMemento.
 */
export enum IOMementoType {
    IoByte = "Byte",
    IoShort = "Short",
    IoInt = "Int",
    IoFloat = "Float",
    IoDouble = "Double",
    IoLong = "Long", // BigInt in JS/TS
    IoBoolean = "Boolean",
    IoChar = "Char", // string in JS/TS (single char)
    IoString = "String",
    IoCharSeries = "CharSeries", // Series<string>
    IoBigDecimal = "BigDecimal", // No direct JS equivalent, typically string or library
    IoBigInt = "BigInt", // BigInt in JS/TS
    IoDateTime = "DateTime", // Date object or string/number
    IoDuration = "Duration", // number (e.g., ms) or structured object
    IoUUID = "UUID", // string
    IoBinary = "Binary", // Uint8Array or similar
    IoUnknown = "Unknown"
}

// Create a mapping from enum values to TypeMemento objects
// This allows treating IOMementoType values as TypeMemento instances.
export const IOMemento: { [key in IOMementoType]: TypeMemento } = Object.values(IOMementoType).reduce((acc, typeName) => {
    acc[typeName] = { typeName: typeName }; // networkSize can be added if specific sizes are known
    return acc;
}, {} as { [key in IOMementoType]: TypeMemento });


/**
 * Type alias for column metadata, a Join of a column name (string) and its TypeMemento.
 */
export type ColumnMeta = Join<string, TypeMemento>;

/** Returns the name of the column from ColumnMeta. */
export function columnName(colMeta: ColumnMeta): string {
    return colMeta.a;
}
/** Returns the type memento of the column from ColumnMeta. */
export function columnType(colMeta: ColumnMeta): TypeMemento {
    return colMeta.b;
}

/**
 * Returns the CursorMeta component (the metadata Tensor) from a CoreTensorCursorWithMeta.
 */
export function coreTensorMeta<T>(ctcm: CoreTensorCursorWithMeta<T>): CursorMeta {
    return ctcm.b;
}
/** Syntactic sugar for coreTensorMeta. */
export const meta = coreTensorMeta;

/** Returns a List (Array in TS) of column names from CursorMeta. */
export function cursorMetaNames(cm: CursorMeta): string[] {
    // CursorMeta is Tensor<ColumnMeta>, so materialize it then map.
    return materialize(cm).map(colMeta => columnName(colMeta));
}

// VII. ColumnExclusion

/**
 * A class used to specify a column to be excluded by its name.
 * Kotlin: @JvmInline value class ColumnExclusion(val name: String)
 */
export class ColumnExclusion {
    constructor(public readonly name: string) {}
    toString(): string { return `ColumnExclusion(${this.name})`; }
}

/**
 * Helper function to create a ColumnExclusion (equivalent to Kotlin's String.unaryMinus).
 * Example: excludeByName("columnName")
 */
export function excludeByName(name: string): ColumnExclusion {
    return new ColumnExclusion(name);
}

/**
 * Returns a new CoreTensorCursorWithMeta with columns excluded by their indices.
 * Kotlin: operator fun <T> CoreTensorCursorWithMeta<T>.minus(killbag: Series<Int>)
 */
export function excludeColumnsByIndices<T>(
    ctcm: CoreTensorCursorWithMeta<T>,
    indicesToExcludeSeries: Series<number>
): CoreTensorCursorWithMeta<T> {
    const currentMeta = meta(ctcm);
    const totalCols = totalSize(currentMeta); // totalSize of CursorMeta is number of columns
    const allIndices = new Set<number>();
    for(let i=0; i < totalCols; ++i) allIndices.add(i);

    const indicesToExclude = new Set<number>();
    for (const idx of materializeSeries(indicesToExcludeSeries)) {
        indicesToExclude.add(idx);
    }

    const retainedIndicesArray: number[] = [];
    allIndices.forEach(idx => {
        if (!indicesToExclude.has(idx)) {
            retainedIndicesArray.push(idx);
        }
    });

    const newCursorData = sliceCursorByColumnIndices(ctcm.a, ...retainedIndicesArray);
    const newMetaCursor = sliceCursorByColumnIndices(currentMeta, ...retainedIndicesArray); // Slice the meta tensor

    return j(newCursorData, newMetaCursor);
}

/**
 * Returns a new CoreTensorCursorWithMeta with columns excluded by ColumnExclusion objects (by name).
 * Kotlin: fun <T> CoreTensorCursorWithMeta<T>.exclude(s: Series<ColumnExclusion>)
 */
export function excludeColumnsByName<T>(
    ctcm: CoreTensorCursorWithMeta<T>,
    exclusionsSeries: Series<ColumnExclusion>
): CoreTensorCursorWithMeta<T> {
    const currentMeta = meta(ctcm);
    const names = cursorMetaNames(currentMeta); // Array of current column names

    const namesToExclude = new Set<string>();
    for (const exclusion of materializeSeries(exclusionsSeries)) {
        namesToExclude.add(exclusion.name);
    }

    const indicesToRetain: number[] = [];
    names.forEach((name, index) => {
        if (!namesToExclude.has(name)) {
            indicesToRetain.push(index);
        }
    });

    const newCursorData = sliceCursorByColumnIndices(ctcm.a, ...indicesToRetain);
    const newMetaCursor = sliceCursorByColumnIndices(currentMeta, ...indicesToRetain);

    return j(newCursorData, newMetaCursor);
}

/**
 * Gets a subset of columns from CoreTensorCursorWithMeta by their names.
 * Kotlin: fun <T> CoreTensorCursorWithMeta<T>.get(vararg s: String)
 */
export function selectColumnsByName<T>(
    ctcm: CoreTensorCursorWithMeta<T>,
    ...columnNames: string[]
): CoreTensorCursorWithMeta<T> {
    const currentMeta = meta(ctcm);
    const allCurrentNames = cursorMetaNames(currentMeta); // Array of current column names

    const indicesToRetain: number[] = [];
    const nameSet = new Set(columnNames); // For efficient lookup

    allCurrentNames.forEach((name, index) => {
        if (nameSet.has(name)) {
            indicesToRetain.push(index);
        }
    });

    // Preserve order of selection if columnNames implies order, but here we iterate through current order
    // If specific order of `columnNames` is required for the new cursor, map `columnNames` to indices first.
    // Current implementation retains original relative order of selected columns.

    const newCursorData = sliceCursorByColumnIndices(ctcm.a, ...indicesToRetain);
    const newMetaCursor = sliceCursorByColumnIndices(currentMeta, ...indicesToRetain);

    return j(newCursorData, newMetaCursor);
}


// VIII. Presentation Functions and Properties

/**
 * Helper function to convert any value to a display string, considering IOMementoType.
 */
export function toDisplayString(value: any, type: TypeMemento): string {
    if (value === null || value === undefined) return "null";

    // Assuming type.typeName corresponds to IOMementoType values
    switch (type.typeName) {
        case IOMementoType.IoCharSeries:
            // Assuming value is Series<string> where each string is a single char
            return seriesCharToString(value as Series<string>);
        // Add more custom formatting based on type if needed
        // e.g. for Date, BigInt, etc.
        case IOMementoType.IoLong:
        case IOMementoType.IoBigInt:
            return typeof value === 'bigint' ? value.toString() : String(value);
        default:
            return String(value);
    }
}

/**
 * Prints the first 'count' rows of the cursor to console.log.
 * Default is 5 rows.
 * Kotlin: fun <T> CoreTensorCursorWithMeta<T>.head(last: Int = 5)
 */
export function head<T>(ctcm: CoreTensorCursorWithMeta<T>, count: number = 5): void {
    const numRowsToShow = Math.max(0, Math.min(count, cursorRows(ctcm.a)));
    show(ctcm, { first: 0, last: numRowsToShow - 1 }); // Range last is inclusive
}

/**
 * Prints 'n' random rows from the cursor to console.log.
 */
export function showRandom<T>(ctcm: CoreTensorCursorWithMeta<T>, n: number = 5): void {
    const dataCursor = ctcm.a;
    const numRows = cursorRows(dataCursor);
    if (numRows === 0) {
        console.log("Cursor is empty, cannot show random rows.");
        return;
    }
    head(ctcm, 0); // Show header (column names)
    for (let i = 0; i < n; i++) {
        const randomIndex = Math.floor(Math.random() * numRows);
        showValues(ctcm, { first: randomIndex, last: randomIndex });
    }
}

/**
 * Prints a summary of the cursor (rows, column names) and then calls showValues
 * to print the data for a specified range.
 */
export function show<T>(ctcm: CoreTensorCursorWithMeta<T>, range?: IntRange): void {
    const dataCursor = ctcm.a;
    const metaCursor = meta(ctcm);
    const names = cursorMetaNames(metaCursor);

    console.log(`Cursor Rows: ${cursorRows(dataCursor)}, Columns: ${cursorCols(dataCursor)}`);
    console.log("Columns:", names.join(", "));

    const effectiveRange = range ?? { first: 0, last: cursorRows(dataCursor) - 1 };
    showValues(ctcm, effectiveRange);
}

/**
 * Prints the values of the cursor rows within the specified range to console.log.
 */
export function showValues<T>(ctcm: CoreTensorCursorWithMeta<T>, range: IntRange): void {
    const dataCursor = ctcm.a;
    const metaCursor = meta(ctcm);
    const numRows = cursorRows(dataCursor);

    if (range.first < 0 || range.last >= numRows || range.first > range.last) {
        if (numRows === 0 && range.first === 0 && range.last === -1) { // Empty range for empty cursor
             console.log("(Cursor is empty or range is invalid for empty cursor)");
        } else {
            console.error(`Cannot fully access range {first: ${range.first}, last: ${range.last}} for cursor with ${numRows} rows.`);
        }
        return;
    }

    try {
        for (let i = range.first; i <= range.last; i++) {
            const rowDataVec = getRow(dataCursor, i);
            const rowValues = materialize(rowDataVec); // Get data for the row

            const displayList: string[] = [];
            for (let j = 0; j < rowValues.length; j++) {
                const colMeta = getTensorValueRank1(metaCursor, j); // Get j-th ColumnMeta
                const val = rowValues[j];
                displayList.push(`${columnName(colMeta)}: ${toDisplayString(val, columnType(colMeta))}`);
            }
            console.log(`Row ${i}: { ${displayList.join(", ")} }`);
        }
    } catch (e: any) { // Catch any error during display
        console.error(`An error occurred displaying range {first: ${range.first}, last: ${range.last}}: ${e.message}`);
    }
}

/**
 * Checks if all columns in the CoreTensorCursorWithMeta are of a numerical type.
 * Based on a subset of IOMementoType considered numeric.
 */
export function isNumerical<T>(ctcm: CoreTensorCursorWithMeta<T>): boolean {
    const metaCursor = meta(ctcm);
    if (totalSize(metaCursor) === 0) return true; // Or false, depending on convention for empty

    const numericTypes = new Set([
        IOMementoType.IoByte, IOMementoType.IoShort, IOMementoType.IoInt,
        IOMementoType.IoFloat, IOMementoType.IoDouble, IOMementoType.IoLong, IOMementoType.IoBigInt
    ]);

    for (const colMeta of materialize(metaCursor)) {
        if (!numericTypes.has(columnType(colMeta).typeName as IOMementoType)) {
            return false;
        }
    }
    return true;
}

/**
 * Checks if all columns in the CoreTensorCursorWithMeta have the same data type.
 */
export function isHomomorphic<T>(ctcm: CoreTensorCursorWithMeta<T>): boolean {
    const metaCursor = meta(ctcm);
    const numCols = totalSize(metaCursor);
    if (numCols <= 1) return true;

    const allMeta = materialize(metaCursor);
    const firstColType = columnType(allMeta[0]).typeName;
    for (let i = 1; i < numCols; i++) {
        if (columnType(allMeta[i]).typeName !== firstColType) {
            return false;
        }
    }
    return true;
}

// End of Part 4 of translation (Metadata and Presentation)

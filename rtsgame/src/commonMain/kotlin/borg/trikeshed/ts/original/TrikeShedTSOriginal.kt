package borg.trikeshed.ts.kotlin.original

/**
 * TrikeShed TypeScript Implementation - Original Reference
 * 
 * This file contains the original TypeScript code as comments
 * for comparison and training purposes with the Kotlin assimilation.
 * 
 * Original TypeScript source from: ../superbikeshed/Trikeshed/trikeshed-ts/src/index.ts
 */

/*
ORIGINAL TYPESCRIPT IMPLEMENTATION:

/**
 * TrikeShed TypeScript Implementation
 * Core data structures and operations
 */

/**
 * Join - The fundamental composition type
 */
export interface Join<A, B> {
    a: A;
    b: B;
}

/**
 * Create a Join instance
 */
export function j<A, B>(a: A, b: B): Join<A, B> {
    return { a, b };
}

/**
 * Series - A sequence type
 */
export type Series<T> = Join<number, (index: number) => T>;

/**
 * Create a Series instance
 */
export function createSeries<T>(size: number, accessor: (index: number) => T): Series<T> {
    return j(size, accessor);
}

/**
 * Tensor - A multi-dimensional data structure
 */
export type Tensor<T> = Join<number[], (coords: number[]) => T>;

/**
 * Create a Tensor instance
 */
export function createTensor<T>(shape: number[], accessor: (coords: number[]) => T): Tensor<T> {
    return j(shape, accessor);
}

/**
 * Cursor - A view into data structures
 */
export type Cursor<T> = Join<number[], (coords: number[]) => T>;

/**
 * Create a Cursor instance
 */
export function createCursor<T>(shape: number[], accessor: (coords: number[]) => T): Cursor<T> {
    return j(shape, accessor);
}

/**
 * Alpha transform - Fundamental transformation
 */
export function alpha<T, R>(series: Series<T>, transform: (value: T) => R): Series<R> {
    return createSeries(series.a, (i) => transform(series.b(i)));
}

/**
 * Materialize a Series to an array
 */
export function materialize<T>(series: Series<T>): T[] {
    const result: T[] = [];
    for (let i = 0; i < series.a; i++) {
        result.push(series.b(i));
    }
    return result;
}

/**
 * Calculate total size of a shape
 */
export function calculateTotalSize(shape: number[]): number {
    return shape.reduce((acc, dim) => acc * dim, 1);
}

/**
 * Convert linear index to coordinates
 */
export function linearToCoords(index: number, shape: number[]): number[] {
    const coords: number[] = new Array(shape.length);
    let remaining = index;
    
    for (let i = shape.length - 1; i >= 0; i--) {
        coords[i] = remaining % shape[i];
        remaining = Math.floor(remaining / shape[i]);
    }
    
    return coords;
}

/**
 * Convert coordinates to linear index
 */
export function coordsToLinear(coords: number[], shape: number[]): number {
    let index = 0;
    let stride = 1;
    
    for (let i = coords.length - 1; i >= 0; i--) {
        index += coords[i] * stride;
        stride *= shape[i];
    }
    
    return index;
}

/**
 * Materialize a Tensor to a flat array
 */
export function materializeTensor<T>(
    tensor: Tensor<T>,
    _batchSize: number = 1024,
): T[] {
    const totalSize = calculateTotalSize(tensor.a);
    const result: T[] = new Array(totalSize);
    
    for (let i = 0; i < totalSize; i++) {
        const coords = linearToCoords(i, tensor.a);
        result[i] = tensor.b(coords);
    }
    
    return result;
}

/**
 * Hot path materialization with batching
 */
export function materializeHot<T, R>(
    tensor: Tensor<T>,
    _batchSize: number = 1024,
    operation: (data: T[]) => R
): R {
    const materialized = materializeTensor(tensor);
    return operation(materialized);
}
*/

/**
 * Training Notes:
 * 
 * Key differences between TypeScript and Kotlin implementations:
 * 
 * 1. Type System:
 *    - TypeScript: Structural typing with interfaces
 *    - Kotlin: Nominal typing with classes and type aliases
 * 
 * 2. Collections:
 *    - TypeScript: Arrays and built-in methods (reduce, map, etc.)
 *    - Kotlin: Lists, Arrays, and Indexed<T> from TrikeShed
 * 
 * 3. Function Syntax:
 *    - TypeScript: Arrow functions and function declarations
 *    - Kotlin: Lambda expressions and function declarations
 * 
 * 4. Null Safety:
 *    - TypeScript: Optional types with ? and undefined
 *    - Kotlin: Nullable types with ? and null
 * 
 * 5. Generic Constraints:
 *    - TypeScript: Interface constraints and type bounds
 *    - Kotlin: Type bounds and reified types
 */ 
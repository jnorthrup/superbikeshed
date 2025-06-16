/**
 * TrikeShed TypeScript Type Definitions
 */

export interface Join<A, B> {
    a: A;
    b: B;
}

export type Series<T> = Join<number, (index: number) => T>;
export type Tensor<T> = Join<number[], (coords: number[]) => T>;
export type Cursor<T> = Join<number[], (coords: number[]) => T>;

export function j<A, B>(a: A, b: B): Join<A, B>;
export function createSeries<T>(size: number, accessor: (index: number) => T): Series<T>;
export function createTensor<T>(shape: number[], accessor: (coords: number[]) => T): Tensor<T>;
export function createCursor<T>(shape: number[], accessor: (coords: number[]) => T): Cursor<T>;
export function alpha<T, R>(series: Series<T>, transform: (value: T) => R): Series<R>;
export function materialize<T>(series: Series<T>): T[];
export function materializeTensor<T>(tensor: Tensor<T>, batchSize?: number): T[];
export function materializeHot<T, R>(tensor: Tensor<T>, batchSize: number, operation: (data: T[]) => R): R;
export function calculateTotalSize(shape: number[]): number;
export function linearToCoords(index: number, shape: number[]): number[];
export function coordsToLinear(coords: number[], shape: number[]): number; 
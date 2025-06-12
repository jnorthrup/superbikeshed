export interface Join<A, B> {
    a: A;
    b: B;
}

export interface Series<T> {
    size: number;
    get(index: number): T;
}

export interface Tensor<T> {
    dimensions: number[];
    get(coordinates: number[]): T;
}

export interface Cursor<T> {
    value: T;
    next(): Cursor<T> | null;
}

export function j<A, B>(a: A, b: B): Join<A, B>;
export function createSeries<T>(size: number, getter: (index: number) => T): Series<T>;
export function createTensor<T>(dimensions: number[], getter: (coordinates: number[]) => T): Tensor<T>;
export function createCursor<T>(value: T, next: () => Cursor<T> | null): Cursor<T>;
export function materialize<T>(series: Series<T>): T[];
export function materializeTensor<T>(tensor: Tensor<T>): T[][]; 
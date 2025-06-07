/**
 * Interface representing a fundamental Join operation, similar to a Pair but with named `a` and `b` components.
 */
export interface Join<A, B> {
    readonly a: A;
    readonly b: B;
}

/**
 * Type alias for a Join where both elements are of the same type.
 */
export type Twin<T> = Join<T, T>;

/**
 * Type alias for a Series, which is a Join of a size (number) and an accessor function.
 * This represents a lazily-evaluated sequence of elements indexed by an integer.
 */
export type Series<T> = Join<number, (index: number) => T>;

/**
 * Type alias for a Tensor, which is a Join of its shape (number[]) and an accessor function
 * that takes coordinates (number[]) and returns an element.
 */
export type Tensor<T> = {
    data: T[];
    shape: number[];
    stride: number[];
};

export interface IntRange {
    first: number;
    last: number; // Inclusive
}

export interface TypeMemento {
    readonly networkSize?: number;
    readonly typeName: string;
}

export enum IOMementoType {
    IoByte = "Byte",
    IoShort = "Short",
    IoInt = "Int",
    IoFloat = "Float",
    IoDouble = "Double",
    IoLong = "Long",
    IoBoolean = "Boolean",
    IoChar = "Char",
    IoString = "String",
    IoCharSeries = "CharSeries",
    IoBigDecimal = "BigDecimal",
    IoBigInt = "BigInt",
    IoDateTime = "DateTime",
    IoDuration = "Duration",
    IoUUID = "UUID",
    IoBinary = "Binary",
    IoUnknown = "Unknown"
}

export type ColumnMeta = Join<string, TypeMemento>;

export type CoreTensorCursor<T> = Tensor<T>;
export type CoreTensorRowVec<T> = Tensor<T>;
export type CoreTensorColumnVec<T> = Tensor<T>;
export type CursorMeta = Tensor<ColumnMeta>;
export type CoreTensorCursorWithMeta<T> = Join<CoreTensorCursor<T>, CursorMeta>; 
// Review/trikeshed-ts/src/index.ts

// I. Join
export interface Join<A, B> {
  readonly a: A;
  readonly b: B;
}

export function j<A, B>(first: A, second: B): Join<A, B> {
  return Object.freeze({ a: first, b: second });
}

export type Twin<T> = Join<T, T>;

export function twin<T>(value: T): Twin<T> {
  return j(value, value);
}

// II. Series
export interface Series<T> {
  readonly size: number;
  get(index: number): T;
  toIterable(): Iterable<T>;
}

export function createSeries<T>(size: number, accessor: (index: number) => T): Series<T> {
  if (size < 0) throw new Error("Series size cannot be negative.");
  return Object.freeze({
    size,
    get: (index: number): T => {
      if (index < 0 || index >= size) {
        throw new Error(`Series index out of bounds: ${index}, size: ${size}`);
      }
      return accessor(index);
    },
    toIterable: function* (): Iterable<T> {
      for (let i = 0; i < size; i++) { // Use 'size' from closure
        yield accessor(i); // Use 'accessor' from closure
      }
    }
  });
}

export function emptySeries<T>(): Series<T> {
  return createSeries(0, (_index: number) => {
    throw new Error("Cannot access elements from an empty series.");
  });
}

export type char = string; // Simple alias for clarity

export function seriesToString(series: Series<string | char>): string {
  let result = "";
  for (const char of series.toIterable()) {
    result += char;
  }
  return result;
}

// III. Tensor
export interface Tensor<T> {
  readonly shape: ReadonlyArray<number>;
  readonly rank: number;
  readonly totalSize: number;
  get(coords: ReadonlyArray<number>): T;
  get1D?(i: number): T;
  get2D?(i: number, j: number): T;
  alpha<R>(transform: (value: T, coords: ReadonlyArray<number>) => R): Tensor<R>;
}

export function createTensor<T>(shape: ReadonlyArray<number>, accessor: (coords: ReadonlyArray<number>) => T): Tensor<T> {
  const defensiveShape = Object.freeze([...shape]);
  const rank = defensiveShape.length;
  const totalSize = rank === 0 && defensiveShape.length === 0 ? 0 : defensiveShape.reduce((acc, val) => {
    if (val === 0) return 0; // If any dimension is 0, total size is 0 (unless shape is empty for scalar)
    return acc * val;
  }, 1);


  const get = (coords: ReadonlyArray<number>): T => {
    if (coords.length !== rank) {
      throw new Error(`Coordinate rank mismatch: expected ${rank}, got ${coords.length}`);
    }
    for (let i = 0; i < rank; i++) {
      if (defensiveShape[i] === 0 && coords[i] === 0) continue; // Allow 0 for 0-sized dim
      if (coords[i] < 0 || coords[i] >= defensiveShape[i]) {
        throw new Error(`Coordinate out of bounds at dimension ${i}: coord ${coords[i]}, size ${defensiveShape[i]}. Shape: [${defensiveShape.join(', ')}] Coords: [${coords.join(', ')}]`);
      }
    }
    return accessor(coords);
  };

  return Object.freeze({
    shape: defensiveShape,
    rank,
    totalSize,
    get,
    get1D: rank === 1 ? (i: number) => get([i]) : undefined,
    get2D: rank === 2 ? (i: number, j: number) => get([i, j]) : undefined,
    alpha: <R>(transform: (value: T, coords: ReadonlyArray<number>) => R): Tensor<R> => {
      return createTensor<R>(defensiveShape, (newCoords) => transform(get(newCoords), newCoords));
    }
  });
}

// IV. Cursor (as a specialized 2D Tensor)
export interface Cursor<T> extends Tensor<T> {
  readonly rows: number;
  readonly cols: number;
  // get(coords: ReadonlyArray<number>): T; // inherited, typically [row, col]
  getRow(rowIndex: number): Series<T>;
  getColumn(colIndex: number): Series<T>;
  sliceRows(range: { start: number, end: number }): Cursor<T>; // end is exclusive
  sliceColsByIndices(colIndices: ReadonlyArray<number>): Cursor<T>;
}

export function createCursor<T>(rows: number, cols: number, accessor: (row: number, col: number) => T): Cursor<T> {
  if (rows < 0 || cols < 0) throw new Error("Cursor dimensions cannot be negative.");
  const tensor = createTensor<T>([rows, cols], (coords) => accessor(coords[0], coords[1]));

  return Object.freeze({
    ...tensor, // Spread tensor properties
    rows,
    cols,
    // get: (coords: ReadonlyArray<number>): T => tensor.get(coords), // Already on tensor
    getRow: (rowIndex: number): Series<T> => {
      if (rowIndex < 0 || rowIndex >= rows) {
        throw new Error(`Row index out of bounds: ${rowIndex}, rows: ${rows}`);
      }
      return createSeries<T>(cols, (colIndex) => tensor.get([rowIndex, colIndex]));
    },
    getColumn: (colIndex: number): Series<T> => {
      if (colIndex < 0 || colIndex >= cols) {
        throw new Error(`Column index out of bounds: ${colIndex}, cols: ${cols}`);
      }
      return createSeries<T>(rows, (rowIndex) => tensor.get([rowIndex, colIndex]));
    },
    sliceRows: (range: { start: number, end: number }): Cursor<T> => {
      if (range.start < 0 || range.end > rows || range.start > range.end) {
        throw new Error(`Invalid row slice range: start ${range.start}, end ${range.end}, rows: ${rows}`);
      }
      const newRows = range.end - range.start;
      return createCursor<T>(newRows, cols, (r, c) => tensor.get([range.start + r, c]));
    },
    sliceColsByIndices: (colIndices: ReadonlyArray<number>): Cursor<T> => {
      const newCols = colIndices.length;
      colIndices.forEach(ci => {
        if (ci < 0 || ci >= cols) throw new Error(`Invalid column index ${ci} for slice, cols: ${cols}`);
      });
      return createCursor<T>(rows, newCols, (r, c) => tensor.get([r, colIndices[c]]));
    }
  });
}

// V. Metadata Types
export enum IOMemento {
  IoByte = "IoByte",
  IoShort = "IoShort",
  IoInt = "IoInt",
  IoFloat = "IoFloat",
  IoDouble = "IoDouble",
  IoLong = "IoLong",
  IoBoolean = "IoBoolean",
  IoChar = "IoChar",
  IoString = "IoString",
  IoCharSeries = "IoCharSeries",
  IoBigDecimal = "IoBigDecimal",
  IoBigInt = "IoBigInt",
  IoDateTime = "IoDateTime",
  IoDuration = "IoDuration",
  IoUUID = "IoUUID",
  IoBinary = "IoBinary",
  IoUnknown = "IoUnknown",
}

export interface TypeMemento {
  readonly networkSize?: number;
  readonly ioType: IOMemento;
}

export interface ColumnMeta {
  readonly name: string;
  readonly type: TypeMemento;
}

export type ColumnExclusion = { readonly name: string, readonly exclude: true };
export function excludeColumn(name: string): ColumnExclusion {
    return Object.freeze({ name, exclude: true });
}

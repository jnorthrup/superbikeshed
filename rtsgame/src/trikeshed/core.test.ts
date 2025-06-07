// src/trikeshed/core.test.ts

import {
    j, first, second, Join,
    twin, Twin,
    Series, size as seriesSize, getSeriesValue, emptySeries, materializeSeries, seriesCharToString,
    Tensor, tensorShape, tensorAccessor, tensorRank, tensorTotalSize,
    TensorConstruct, TensorSeries, TensorCursor,
    getTensorValue, getTensorValueRank1, getTensorValueRank2,
    alphaConvert, alphaConvertSeries,
    linearToCoords, coordsToLinear, materialize,
    broadcastShapes, /*calculateBroadcastCoordinateMap,*/ getCoordsForBroadcastedTensor, zip, combine, // Not testing all broadcasting utils in basic tests
    CoreTensorCursor, CoreTensorRowVec, // CoreTensorColumnVec,
    cursorRows, cursorCols, getRow, getCol,
    IntRange, sliceCursorByRowRange, sliceCursorByColumnIndices, sliceCursorByColumnSeries,
    TypeMemento, IOMementoType, IOMemento, ColumnMeta, columnName, columnType,
    CursorMeta, CoreTensorCursorWithMeta, coreTensorMeta, meta as getCursorMeta, cursorMetaNames, // aliased meta to getCursorMeta
    ColumnExclusion, excludeByName,
    excludeColumnsByIndices, excludeColumnsByName, selectColumnsByName,
    toDisplayString, head, show, showRandom, showValues, // Not all presentation fns will be easily testable without spies on console.log
    isNumerical, isHomomorphic
} from './core';

// Mock console.log for presentation function tests if needed
// let consoleLogSpy: jest.SpyInstance;
// beforeEach(() => {
//    consoleLogSpy = jest.spyOn(console, 'log').mockImplementation(() => {});
// });
// afterEach(() => {
//    consoleLogSpy.mockRestore();
// });


describe('Trikeshed Core Library', () => {

    describe('Join, Twin', () => {
        it('should create a Join and access its properties', () => {
            const myJoin: Join<number, string> = j(10, "hello");
            expect(myJoin.a).toBe(10);
            expect(myJoin.b).toBe("hello");
            expect(first(myJoin)).toBe(10);
            expect(second(myJoin)).toBe("hello");
        });

        it('should create a Twin', () => {
            const myTwin: Twin<number> = twin(5);
            expect(myTwin.a).toBe(5);
            expect(myTwin.b).toBe(5);
        });
    });

    describe('Series', () => {
        it('should create a Series and access its properties and values', () => {
            const mySeries: Series<number> = j(3, (i) => i * 2);
            expect(seriesSize(mySeries)).toBe(3);
            expect(getSeriesValue(mySeries, 0)).toBe(0);
            expect(getSeriesValue(mySeries, 1)).toBe(2);
            expect(getSeriesValue(mySeries, 2)).toBe(4);
            expect(() => getSeriesValue(mySeries, 3)).toThrow(RangeError);
        });

        it('should handle EmptySeries', () => {
            const es: Series<string> = emptySeries<string>();
            expect(seriesSize(es)).toBe(0);
            expect(() => getSeriesValue(es, 0)).toThrowError("Cannot access elements of an empty series.");
        });

        it('should materialize a Series to an iterable and then to an array', () => {
            const mySeries: Series<number> = j(3, (i) => i + 1);
            const iterable = materializeSeries(mySeries);
            const arr = Array.from(iterable);
            expect(arr).toEqual([1, 2, 3]);
        });

        it('should convert a Series of chars (strings) to a string', () => {
            const charSeries: Series<string> = j(3, (i) => String.fromCharCode(97 + i)); // "a", "b", "c"
            expect(seriesCharToString(charSeries)).toBe("abc");
        });

        it('should alphaConvert a Series', () => {
            const mySeries: Series<number> = j(3, (i) => i + 1); // 1, 2, 3
            const newSeries = alphaConvertSeries(mySeries, x => x * 10); // 10, 20, 30
            expect(seriesSize(newSeries)).toBe(3);
            expect(getSeriesValue(newSeries, 0)).toBe(10);
            expect(getSeriesValue(newSeries, 1)).toBe(20);
            expect(getSeriesValue(newSeries, 2)).toBe(30);
        });
    });

    describe('Tensor', () => {
        it('should create a Tensor and access its properties', () => {
            const myTensor: Tensor<number> = TensorConstruct([2, 2], (coords) => coords[0] + coords[1]);
            expect(tensorShape(myTensor)).toEqual([2, 2]);
            expect(tensorRank(myTensor)).toBe(2);
            expect(tensorTotalSize(myTensor)).toBe(4);
            expect(getTensorValue(myTensor, [0, 1])).toBe(1);
            expect(getTensorValueRank2(myTensor, 1, 1)).toBe(2);
        });

        it('should create TensorSeries (rank 1 tensor)', () => {
            const ts: Tensor<string> = TensorSeries(3, (i) => `val${i}`);
            expect(tensorRank(ts)).toBe(1);
            expect(tensorShape(ts)).toEqual([3]);
            expect(getTensorValueRank1(ts, 1)).toBe("val1");
        });

        it('should create TensorCursor (rank 2 tensor)', () => {
            const tc: Tensor<number> = TensorCursor(2, 3, (r, c) => r * 10 + c);
            expect(tensorRank(tc)).toBe(2);
            expect(tensorShape(tc)).toEqual([2, 3]);
            expect(getTensorValueRank2(tc, 1, 2)).toBe(12);
        });

        it('should perform alphaConvert on a Tensor', () => {
            const myTensor: Tensor<number> = TensorConstruct([2,2], coords => coords[0] * 10 + coords[1]);
            // 0,0 -> 0 | 0,1 -> 1
            // 1,0 -> 10 | 1,1 -> 11
            const newTensor = alphaConvert(myTensor, x => x + 5);
            expect(getTensorValueRank2(newTensor, 0,0)).toBe(0+5);
            expect(getTensorValueRank2(newTensor, 1,1)).toBe(11+5);
        });

        it('should convert linear index to coords and back', () => {
            const tensor = TensorConstruct([2,3,4], () => 0); // Shape 2x3x4, total size 24
            let coords = linearToCoords(tensor, 0); // Should be [0,0,0]
            expect(coords).toEqual([0,0,0]);
            expect(coordsToLinear(tensor, coords)).toBe(0);

            coords = linearToCoords(tensor, 23); // Last element, should be [1,2,3]
            expect(coords).toEqual([1,2,3]);
            expect(coordsToLinear(tensor, coords)).toBe(23);

            coords = linearToCoords(tensor, 5); // 5 -> [0,1,1] (0*12 + 1*4 + 1*1 = 5)
            expect(coords).toEqual([0,1,1]);
            expect(coordsToLinear(tensor, coords)).toBe(5);

            expect(() => linearToCoords(tensor, 24)).toThrow(RangeError);
            expect(() => coordsToLinear(tensor, [0,0,4])).toThrow(RangeError); // coord out of bounds
        });

        it('should materialize a Tensor to an array', () => {
            const tensor: Tensor<number> = TensorCursor(2, 2, (r, c) => r === c ? 1 : 0); // Identity matrix
            // 1 0
            // 0 1
            // Materializes row-major: 1, 0, 0, 1
            expect(materialize(tensor)).toEqual([1, 0, 0, 1]);
        });
         it('should handle materialize on zero-dimension tensor', () => {
            const tensorZ: Tensor<number> = TensorConstruct([2,0,3], () => 0);
            expect(materialize(tensorZ)).toEqual([]);
        });

    });

    describe('CoreTensorCursor', () => {
        let cursor: CoreTensorCursor<number>;
        beforeEach(() => {
            cursor = TensorCursor(3, 4, (r,c) => r * 10 + c);
            // 00 01 02 03
            // 10 11 12 13
            // 20 21 22 23
        });

        it('should get rows and cols counts', () => {
            expect(cursorRows(cursor)).toBe(3);
            expect(cursorCols(cursor)).toBe(4);
        });

        it('should get a row vector', () => {
            const row1 = getRow(cursor, 1); // Second row: 10 11 12 13
            expect(tensorRank(row1)).toBe(1);
            expect(tensorShape(row1)).toEqual([4]);
            expect(materialize(row1)).toEqual([10, 11, 12, 13]);
        });

        it('should get a column vector', () => {
            const col0 = getCol(cursor, 0); // First col: 00 10 20
            expect(tensorRank(col0)).toBe(1);
            expect(tensorShape(col0)).toEqual([3]);
            expect(materialize(col0)).toEqual([0, 10, 20]);
        });

        it('should slice by row range', () => {
            const subCursor = sliceCursorByRowRange(cursor, {first: 1, last: 2}); // Rows 1 and 2
            // 10 11 12 13
            // 20 21 22 23
            expect(cursorRows(subCursor)).toBe(2);
            expect(cursorCols(subCursor)).toBe(4);
            expect(getTensorValueRank2(subCursor, 0,0)).toBe(10); // original (1,0)
            expect(getTensorValueRank2(subCursor, 1,3)).toBe(23); // original (2,3)
        });

        it('should slice by column indices (varargs)', () => {
            const subCursor = sliceCursorByColumnIndices(cursor, 3, 1); // Columns 3 and 1 (in that order)
            // 03 01
            // 13 11
            // 23 21
            expect(cursorRows(subCursor)).toBe(3);
            expect(cursorCols(subCursor)).toBe(2);
            expect(getTensorValueRank2(subCursor, 0,0)).toBe(3);  // original (0,3)
            expect(getTensorValueRank2(subCursor, 1,1)).toBe(11); // original (1,1)
        });

        it('should slice by column indices (Series)', () => {
            const colIdxSeries: Series<number> = j(2, i => i === 0 ? 0 : 2); // Indices 0 and 2
            const subCursor = sliceCursorByColumnSeries(cursor, colIdxSeries);
            // 00 02
            // 10 12
            // 20 22
            expect(cursorRows(subCursor)).toBe(3);
            expect(cursorCols(subCursor)).toBe(2);
            expect(getTensorValueRank2(subCursor, 0,0)).toBe(0); // original (0,0)
            expect(getTensorValueRank2(subCursor, 2,1)).toBe(22); // original (2,2)
        });
    });

    describe('Metadata and CursorWithMeta', () => {
        let cwm: CoreTensorCursorWithMeta<string>;
        let data: CoreTensorCursor<string>;
        let metaData: CursorMeta;

        beforeEach(() => {
            data = TensorCursor(1, 2, (r, c) => `val_${r}_${c}`); // 1x2 data
            const colMeta1: ColumnMeta = j("name", IOMemento[IOMementoType.IoString]);
            const colMeta2: ColumnMeta = j("age", IOMemento[IOMementoType.IoInt]);
            metaData = TensorSeries(2, i => (i === 0 ? colMeta1 : colMeta2)); // 2 columns of metadata
            cwm = j(data, metaData);
        });

        it('should access data and metadata', () => {
            expect(cursorRows(cwm.a)).toBe(1);
            const currentMeta = getCursorMeta(cwm); // Using aliased import for meta
            expect(tensorRank(currentMeta)).toBe(1);
            expect(totalSize(currentMeta)).toBe(2);
            expect(columnName(getTensorValueRank1(currentMeta, 0))).toBe("name");
        });

        it('should get cursor meta names', () => {
            expect(cursorMetaNames(metaData)).toEqual(["name", "age"]);
        });

        it('should exclude columns by name', () => {
            const exclusion = excludeByName("age");
            const exclusionSeries = j(1, () => exclusion);
            const newCwm = excludeColumnsByName(cwm, exclusionSeries);

            expect(cursorCols(newCwm.a)).toBe(1);
            expect(cursorMetaNames(getCursorMeta(newCwm))).toEqual(["name"]);
            expect(getTensorValueRank2(newCwm.a, 0,0)).toBe("val_0_0");
        });

        it('should select columns by name', () => {
            const newCwm = selectColumnsByName(cwm, "age"); // Select only "age"
            expect(cursorCols(newCwm.a)).toBe(1);
            expect(cursorMetaNames(getCursorMeta(newCwm))).toEqual(["age"]);
            expect(getTensorValueRank2(newCwm.a, 0,0)).toBe("val_0_1"); // original data from age column
        });

        it('isNumerical and isHomomorphic', () => {
            expect(isNumerical(cwm)).toBe(false); // "name" is string, "age" is Int

            const colMetaNum1: ColumnMeta = j("num1", IOMemento[IOMementoType.IoFloat]);
            const colMetaNum2: ColumnMeta = j("num2", IOMemento[IOMementoType.IoDouble]);
            const metaNum = TensorSeries(2, i => (i === 0 ? colMetaNum1 : colMetaNum2));
            const cwmNum = j(TensorCursor(1,2,() => 0), metaNum);
            expect(isNumerical(cwmNum)).toBe(true);
            expect(isHomomorphic(cwmNum)).toBe(false); // Float and Double are different types

            const metaHomo = TensorSeries(2, () => colMetaNum1); // Both Float
            const cwmHomo = j(TensorCursor(1,2,() => 0), metaHomo);
            expect(isHomomorphic(cwmHomo)).toBe(true);
        });
    });

    // TODO: Add tests for broadcasting functions (broadcastShapes, combine, zip)
    // TODO: Add tests for presentation functions (mock console.log to check output format)

});

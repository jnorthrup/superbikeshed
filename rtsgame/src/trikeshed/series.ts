import { Series } from './types';
import { j } from './join';

/** Returns the size of the Series. */
export function size<T>(series: Series<T>): number {
    return series.a;
}

/**
 * Operator to access an element of the Series by its index.
 */
export function getSeriesValue<T>(series: Series<T>, i: number): T {
    if (i < 0 || i >= size(series)) {
        throw new RangeError(`Index ${i} out of bounds for Series of size ${size(series)}`);
    }
    return series.b(i);
}

/**
 * An empty Series instance.
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
 * A class wrapper around Series that makes it Iterable.
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
        const currentSeries = this.series;
        return {
            next: (): IteratorResult<A> => {
                if (index < size(currentSeries)) {
                    return { value: getSeriesValue(currentSeries, index++), done: false };
                } else {
                    return { value: undefined as any, done: true };
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
 */
export function materializeSeries<T>(series: Series<T>): IterableSeries<T> {
    return new IterableSeries(series);
}

/**
 * Extension function to convert a Series of Char to a String.
 */
export function seriesCharToString(series: Series<string>): string {
    let result = "";
    for (const char of materializeSeries(series)) {
        result += char;
    }
    return result;
}

/**
 * Transform a Series using a mapping function.
 */
export function alphaConvertSeries<X, C>(series: Series<X>, transform: (value: X) => C): Series<C> {
    return j(size(series), (i: number) => transform(getSeriesValue(series, i)));
} 
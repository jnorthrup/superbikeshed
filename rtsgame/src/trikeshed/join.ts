import { Join, Twin } from './types';

/**
 * Creates a Join instance. This is the primary construction mechanism.
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
 * Factory function to create a Twin from a single value.
 */
export function twin<T>(value: T): Twin<T> {
    return j(value, value);
}

/**
 * Creates a lazy supplier (a lambda with no arguments) that returns `this` value.
 */
export function leftIdentity<T>(value: T): () => T {
    return () => value;
}

/**
 * Syntactic sugar for leftIdentity.
 */
export function circularRef<T>(value: T): () => T {
    return leftIdentity(value);
} 
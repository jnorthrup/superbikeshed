@file:Suppress("NOTHING_TO_INLINE")

package borg.trikeshed.lib

/**
 * Binary search implementation for Indexed collections.
 * Provides efficient O(log n) search for sorted data.
 */

/**
 * Performs binary search on a sorted Indexed collection.
 * 
 * @param element The element to search for
 * @param comparison Comparison function that returns:
 *   - negative value if element at index is less than target
 *   - zero if element at index equals target  
 *   - positive value if element at index is greater than target
 * @return Index of the element if found, or negative insertion point - 1 if not found
 */
inline fun <T> Indexed<T>.binarySearch(
    element: T,
    crossinline comparison: (T, T) -> Int
): Int {
    var low = 0
    var high = size - 1
    
    while (low <= high) {
        val mid = (low + high) ushr 1
        val midElement = this[mid]
        val cmp = comparison(midElement, element)
        
        when {
            cmp < 0 -> low = mid + 1
            cmp > 0 -> high = mid - 1
            else -> return mid
        }
    }
    return -(low + 1)
}

/**
 * Binary search for Comparable elements.
 */
inline fun <T : Comparable<T>> Indexed<T>.binarySearch(element: T): Int {
    return binarySearch(element) { a, b -> a.compareTo(b) }
}

/**
 * Binary search with custom key selector.
 * 
 * @param key The key to search for
 * @param selector Function to extract the comparable key from elements
 * @return Index of the element if found, or negative insertion point - 1 if not found
 */
inline fun <T, K : Comparable<K>> Indexed<T>.binarySearchBy(
    key: K,
    crossinline selector: (T) -> K
): Int {
    var low = 0
    var high = size - 1
    
    while (low <= high) {
        val mid = (low + high) ushr 1
        val midKey = selector(this[mid])
        val cmp = midKey.compareTo(key)
        
        when {
            cmp < 0 -> low = mid + 1
            cmp > 0 -> high = mid - 1
            else -> return mid
        }
    }
    return -(low + 1)
}

/**
 * Binary search with custom comparison function.
 * Useful when you need to search with a different comparison than natural ordering.
 */
inline fun <T> Indexed<T>.binarySearchWith(
    element: T,
    crossinline comparator: (T, T) -> Int
): Int {
    return binarySearch(element, comparator)
}
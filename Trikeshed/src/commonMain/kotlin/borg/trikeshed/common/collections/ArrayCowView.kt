package borg.trikeshed.common.collections
@file:OptIn(ExperimentalUnsignedTypes::class)


/**
 * A mutable view of an Array which performs a copy-on-write on the first mutation.
 * This is ideal for safely handling arrays received from external sources where ownership
 * and mutability guarantees are unclear. It avoids a defensive copy until a write is
 * actually performed.
 *
 * This implementation is not a full `MutableList` but provides the essential mutable
 * operations (`set`) and read operations (`get`, `size`).
 */
class ArrayCowView<T>(private var array: Array<T>) {

    private var isCopied = false

    private fun ensureCopied() {
        if (!isCopied) {
            array = array.copyOf()
            isCopied = true
        }
    }

    /** The number of elements in the array. */
    val size: Int
        get() = array.size

    /** Returns the element at the specified [index]. */
    operator fun get(index: Int): T {
        return array[index]
    }

    /** Sets the element at the specified [index] to the given [value]. */
    operator fun set(index: Int, value: T) {
        ensureCopied()
        array[index] = value
    }

    /** Returns the underlying array. If mutations have occurred, this will be a copy. */
    fun toArray(): Array<T> = array

    override fun toString(): String {
        return "ArrayCowView(array=${array.contentToString()})"
    }
} 
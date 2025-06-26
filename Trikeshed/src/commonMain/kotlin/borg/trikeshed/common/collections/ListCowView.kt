package borg.trikeshed.common.collections

/** a mutable listView of a List which performs a copy to MutableList on first mutation.  not threadsafe or concurrent. */
class ListCowView<T>(private var list: List<T> = emptyList()) : List<T>, AbstractMutableList<T>() {
    //keep our inital list until a mutable operation, then replace with .toMutableList
    private var isCopied = false

    private fun ensureCopied() {
        if (!isCopied && list !is MutableList<T>) {
            list = list.toMutableList()
            isCopied = true
        }
    }

    override fun add(index: Int, element: T) {
        ensureCopied()
        (list as MutableList<T>).add(index, element)
    }

    override val size: Int
        get() = list.size

    override fun get(index: Int): T {
        return list[index]
    }

    override fun removeAt(index: Int): T {
        ensureCopied()
        return (list as MutableList<T>).removeAt(index)
    }

    override fun set(index: Int, element: T): T {
        ensureCopied()
        return (list as MutableList<T>).set(index, element)
    }

    override fun toString(): String {
        return "ListCowView(list=$list)"
    }
}
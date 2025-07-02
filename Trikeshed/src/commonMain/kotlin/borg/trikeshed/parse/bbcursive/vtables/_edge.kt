package borg.trikeshed.parse.bbcursive.vtables

/**
 * context class. midpoint between 2 casts. this class is a pair, but we pretend its more. this should be refactored
 * to a pair class.
 *
 * Function interface performs reification from the addressType against the core
 * type,context, delta, coersion points, etc.
 *
 * left refers to "reference" side, right refers to "pointer" side.
 */
abstract class _edge<coreType, addressType> {
    /**
     * this is a core of memory accessed somehow by an addressType to get java Objects from this context of core that is probably bytes or chars
     */
    private var core: coreType? = null

    protected abstract fun at(): addressType

    protected abstract fun goTo(addressType: addressType): addressType

    /**
     * left type node with induction of core only. address will be null until set
     */
    fun core(vararg e: _edge<coreType, addressType>): coreType? {
        val empty = e.isEmpty()
        val isMe = !empty && this == e[0]

        return if (!empty && !isMe) core(bind(e[0].core(), e[0].location())) else core
    }

    /**
     * an address
     *
     * for _ptr, Integer is an address of a ByteBuffer state, linear memory here.
     *
     * for `Map<K,V>`, K is an address to get a V from `_edge<V,K>`
     *
     * for `_edge<_edge<A,B>,_ptr>`
     *
     * @param notnullorself null for self. non-empty set for induction
     * @return typically what is returned is what is passed in most recently to any of the Pair.second mutators (this.at, this.goto, this.location).
     */
    protected fun at(vararg notnullorself: addressType): addressType {
        val addressType1 = notnullorself[0]
        return if (notnullorself.isNotEmpty() && this != addressType1) goTo(addressType1) else r$()
    }

    /**
     * internal factory or getter for pair.second. for _ptr this is inferred from bytebuffer instance.
     */
    protected abstract fun r$(): addressType

    /**
     * right type node with induction
     */
    fun location(vararg e: _edge<coreType, addressType>): addressType {
        val subj = this
        val empty = e.isEmpty()
        val alien = !empty && subj != e[0]
        return if (empty || !alien) at() else bind(e[0].core(), at(e[0].location())).location()
    }

    /**
     * binds two types
     *
     * @param coreType
     * @param address
     * @return fused arc
     */
    fun bind(coreType: coreType?, address: addressType): _edge<coreType, addressType> {
        core = (coreType)
        at(address)
        return this
    }
}
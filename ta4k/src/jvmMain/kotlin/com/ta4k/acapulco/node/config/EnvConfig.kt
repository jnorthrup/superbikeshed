package borg.trikeshed.acapulco.node.config

import java.io.PrintStream

 data  class EnvConfig(
        val name: String="",
      @Transient  val defValue: String  = "",
       @Transient val docString: String? = null, ) {
    init {
        registry += this
    }
    constructor() : this("","","")
    private val env: String? by lazy { System.getenv(name) ?: defValue }
    val value get() = env!!.trim()
    fun shellAssignment(): String =
        ": $" + "{${name}:=${value?:defValue}}${docString?.let { "\t# $docString" } ?: ""}"

    override fun toString() = listOf(name, defValue, docString).toString()

    companion object {
        val registry = sortedSetOf<EnvConfig>(compareBy { it.name })
        fun shellVars(out: PrintStream = System.err) = registry.forEach { out.println(it.shellAssignment()) }
        fun docVars(out: PrintStream = System.err) = registry.forEach { out.println(it.toString()) }
    }

}
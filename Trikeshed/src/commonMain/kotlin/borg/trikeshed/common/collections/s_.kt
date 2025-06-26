package borg.trikeshed.common.collections
@file:OptIn(ExperimentalUnsignedTypes::class)


import borg.trikeshed.reactor.currentTimeMillis
import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.j

/**Indexed macro object*/
object s_ {
    /**Indexed factorymethod */
    operator fun <T> get(vararg t: T): Indexed<T> = t.size j t::get
}

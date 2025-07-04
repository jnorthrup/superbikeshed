package nexus

import borg.trikeshed.lib.*

// Extensions to convert to Indexed for TrikeShed integration
fun <T> List<T>.toIdx(): Indexed<T> = this.size j { this[it] }
fun <T> Array<T>.toIdx(): Indexed<T> = this.size j { this[it] }
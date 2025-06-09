package borg.trikeshed.net.http3.qpack

/**
 * A simple representation of an HTTP header field (name-value pair).
 * This is used internally by the QPACK placeholder implementation and for representing
 * decoded headers in a simple list format.
 */
data class HeaderField(val name: String, val value: String)

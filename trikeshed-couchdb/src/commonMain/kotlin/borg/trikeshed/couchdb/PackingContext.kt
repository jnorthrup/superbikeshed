package borg.trikeshed.couchdb

/**
 * PackingContext stub for trikeshed-ccek compatibility
 */
data class PackingContext(
    val sessionId: String,
    val compression: Boolean = false,
    val encryption: Boolean = false
) 
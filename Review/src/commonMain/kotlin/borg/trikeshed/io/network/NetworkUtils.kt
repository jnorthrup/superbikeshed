package borg.trikeshed.io.network

// Placeholder for any common network utility functions if needed later.
// For now, it can be empty or contain simple helpers.

fun isValidPort(port: Int): Boolean {
    return port in 1..65535
}

// Potentially add common IP address validation or manipulation functions here
// if they are platform-agnostic.

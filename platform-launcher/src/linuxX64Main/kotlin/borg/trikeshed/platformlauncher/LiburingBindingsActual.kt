package borg.trikeshed.platformlauncher

import liburing.io_uring_version

actual fun getLiburingVersion(): String {
    // Call a simple liburing function to get its version
    // This assumes liburing is installed and its headers are found by cinterop
    val major = io_uring_version(null) // Pass null as argument for version check
    return "liburing version $major"
}
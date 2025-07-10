package borg.trikeshed.platformlauncher

import borg.trikeshed.uring.*

// Use the proper KMP uring module instead of expect/actual
fun getLiburingVersion(): String {
    return "uring-kmp-v1.0.0-${getArchitecture()}"
}
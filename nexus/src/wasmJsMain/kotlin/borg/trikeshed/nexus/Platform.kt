package borg.trikeshed.nexus

import kotlin.js.Date

actual fun getCurrentTimeMillis(): Long = Date.now().toLong()
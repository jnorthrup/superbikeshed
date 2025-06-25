package borg.trikeshed.reactor

import kotlin.js.Date

actual fun getCurrentTimeMillis(): Long = Date.now().toLong()
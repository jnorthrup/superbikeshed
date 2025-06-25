package borg.trikeshed.integration

import kotlin.js.Date

actual fun getCurrentTimeMillis(): Long = Date.now().toLong()
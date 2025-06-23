package borg.trikeshed.lib

actual fun getSystemProperty(key: String): String? = null // JS implementation placeholder
actual fun getCurrentTimeMillis(): Long = kotlin.js.Date.now().toLong() 
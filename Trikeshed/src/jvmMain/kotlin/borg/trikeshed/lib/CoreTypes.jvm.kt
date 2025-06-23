package borg.trikeshed.lib

actual fun getCurrentTimeMillis(): Long = System.currentTimeMillis()
actual fun getSystemProperty(key: String): String? = System.getProperty(key) 
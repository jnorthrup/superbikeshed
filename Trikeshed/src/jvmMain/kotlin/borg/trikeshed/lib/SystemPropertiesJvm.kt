package borg.trikeshed.lib

actual fun getSystemProperty(key: String): String? = System.getProperty(key)
actual fun getCurrentTimeMillis(): Long = System.currentTimeMillis() 
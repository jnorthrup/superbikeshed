package borg.trikeshed.lib

actual fun getSystemProperty(key: String): String? = null // Native implementation placeholder
actual fun getCurrentTimeMillis(): Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() 
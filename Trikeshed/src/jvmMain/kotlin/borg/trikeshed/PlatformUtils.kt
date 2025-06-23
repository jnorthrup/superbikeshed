package borg.trikeshed

actual object PlatformUtils {
    actual fun currentTimeMillis(): Long = System.currentTimeMillis()
    actual fun getProperty(key: String): String? = System.getProperty(key)
    actual fun getProperty(key: String, defaultValue: String): String = System.getProperty(key, defaultValue)
} 
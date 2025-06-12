package org.trikeshed.util

import java.util.logging.Logger as JavaLogger

object Logger {
    fun <T> getLogger(type: Class<T>): JavaLogger {
        return JavaLogger.getLogger(type.name)
    }
    
    inline fun <reified T> getLogger(): JavaLogger {
        return getLogger(T::class.java)
    }
} 
package borg.trikeshed.reflection

import borg.trikeshed.lib.Series

expect class PlatformServiceInvoker {
    fun findMethod(service: Any, methodName: String): Any? // Represents a callable method reference
    fun callMethod(method: Any, service: Any, args: Series<Any?>): Any?
}
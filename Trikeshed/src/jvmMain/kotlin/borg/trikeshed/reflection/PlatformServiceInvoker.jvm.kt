package borg.trikeshed.reflection

import borg.trikeshed.lib.Series
import kotlin.reflect.KCallable
import kotlin.reflect.full.members

actual class PlatformServiceInvokerImpl : PlatformServiceInvoker {
    actual override fun findMethod(service: Any, methodName: String): Any? {
        // In JVM, we can use Kotlin reflection to find a KCallable (function or property)
        return service::class.members.find { it.name == methodName }
    }

    actual override fun callMethod(method: Any, service: Any, args: Series<Any?>): Any? {
        // Cast the method to KCallable and then invoke it
        val kCallable = method as? KCallable<*> ?: throw IllegalArgumentException("Method must be a KCallable on JVM")
        return kCallable.call(service, *args.play.toList().toTypedArray())
    }
}
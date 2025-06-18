package borg.trikeshed.net.http

import com.google.web.bindery.requestfactory.server.ServiceLayer
import com.google.web.bindery.requestfactory.server.SimpleRequestProcessor

/**
 * A singleton object that encapsulates the GWT RequestFactory processing logic.
 * This replaces the static field from RelaxFactory's GwtRequestFactoryVisitor.
 */
object GwtRequestFactoryProcessor {
    // Create a service layer. We can enhance this later with custom locators and decorators
    // to support dependency injection via CoroutineContext.
    private val serviceLayer = ServiceLayer.create()
    private val processor = SimpleRequestProcessor(serviceLayer)

    fun process(payload: String): String = processor.process(payload)
} 
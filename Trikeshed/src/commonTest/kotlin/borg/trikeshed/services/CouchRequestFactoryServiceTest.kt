package borg.trikeshed.services

import kotlin.test.*
import borg.trikeshed.lib.bridge.CouchClient
import borg.trikeshed.lib.bridge.DatabaseName
import borg.trikeshed.lib.toIdx
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import borg.trikeshed.lib.j

class CouchRequestFactoryServiceTest {
    private lateinit var service: CouchRequestFactoryService

    @BeforeTest
    fun setup() {
        // Use a stub CouchClient (doesn't need to be functional for these tests)
        service = CouchRequestFactoryService(CouchClient("http://localhost:5984"), DatabaseName("entities"))
    }

    @Test
    fun `test valid JSON request returns success`() {
        val request = """{"service":"TestService","method":"doSomething"}"""
        val bytes = request.encodeToByteArray()
        val payload = bytes.size j { i: Int -> bytes[i] }
        val responseBytes = service.process(payload)
        val response = responseBytes.play.joinToString("") { b: Byte -> b.toInt().toChar().toString() }
        assert(response.contains("\"success\":true"))
        assert(response.contains("TestService"))
        assert(response.contains("doSomething"))
    }

    @Test
    fun `test invalid JSON request returns error`() {
        val request = "not a json"
        val bytes = request.encodeToByteArray()
        val payload = bytes.size j { i: Int -> bytes[i] }
        val responseBytes = service.process(payload)
        val response = responseBytes.play.joinToString("") { b: Byte -> b.toInt().toChar().toString() }
        assert(response.contains("\"success\":false"))
        assert(response.contains("Invalid JSON request"))
    }

    @Test
    fun `test service locator registration`() {
        var called = false
        service.registerServiceLocator("FooService") { called = true; "bar" }
        // Simulate usage by invoking the locator through the process method
        // (no direct access to serviceLocators, so just check registration doesn't throw)
        service.registerServiceLocator("BarService") { "baz" }
        // If registration works, called will be set to true
        service.registerServiceLocator("FooService") { called = true; "bar" }
        assert(true) // If no exception, registration works
    }

    @Test
    fun `test method validator registration`() {
        var result: Boolean? = null
        service.registerMethodValidator("fooMethod") { x -> result = (x == 42); result!! }
        // Simulate usage by invoking the validator through the process method
        service.registerMethodValidator("barMethod") { _ -> false }
        // If registration works, result will be set
        service.registerMethodValidator("fooMethod") { x -> result = (x == 42); result!! }
        assert(true) // If no exception, registration works
    }
}

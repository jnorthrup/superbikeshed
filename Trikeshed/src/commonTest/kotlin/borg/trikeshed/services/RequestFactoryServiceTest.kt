package borg.trikeshed.services

import borg.trikeshed.lib.*
import borg.trikeshed.parse.json.*
import kotlin.test.*

class RequestFactoryServiceTest {
    private lateinit var service: RequestFactoryService

    @BeforeTest
    fun setup() {
        service = RequestFactoryService.create()
    }

    @Test
    fun testBasicMethodInvocation() = runTest {
        // Register a test service
        val testService = TestService()
        service.registerServiceLocator("TestService") { testService }

        // Create a test request
        val request = mapOf(
            "serviceClass" to "TestService",
            "methodName" to "echo",
            "args" to listOf("Hello World")
        )

        // Serialize and process the request
        val payload = JsonSerializer.serialize(request).play.joinToString("").encodeToByteArray().toSeries()
        val response = service.process(payload)

        // Parse and verify the response
        val responseJson = response.play.toByteArray().decodeToString()
        val result = JsonParser.parse(responseJson.toSeries())

        assertEquals("Hello World", result.getString("result"))
    }

    @Test
    fun testMethodValidation() = runTest {
        // Register a test service with validation
        val testService = TestService()
        service.registerServiceLocator("TestService") { testService }
        service.registerMethodValidator("echo") { args ->
            args.size == 1 && args[0] is String
        }

        // Test with valid args
        val validRequest = mapOf(
            "serviceClass" to "TestService",
            "methodName" to "echo",
            "args" to listOf("Valid Input")
        )
        val validPayload = JsonSerializer.serialize(validRequest).play.joinToString("").encodeToByteArray().toSeries()
        val validResponse = service.process(validPayload)
        val validResult = JsonParser.parse(validResponse.play.toByteArray().decodeToString().toSeries())
        assertTrue(validResult.getBoolean("success"))

        // Test with invalid args
        val invalidRequest = mapOf(
            "serviceClass" to "TestService",
            "methodName" to "echo",
            "args" to listOf(42) // Wrong type
        )
        val invalidPayload = JsonSerializer.serialize(invalidRequest).play.joinToString("").encodeToByteArray().toSeries()
        val invalidResponse = service.process(invalidPayload)
        val invalidResult = JsonParser.parse(invalidResponse.play.toByteArray().decodeToString().toSeries())
        assertFalse(invalidResult.getBoolean("success"))
    }

    @Test
    fun testUnregisteredService() = runTest {
        val request = mapOf(
            "serviceClass" to "UnknownService",
            "methodName" to "unknown",
            "args" to listOf()
        )
        val payload = JsonSerializer.serialize(request).play.joinToString("").encodeToByteArray().toSeries()
        val response = service.process(payload)
        val result = JsonParser.parse(response.play.toByteArray().decodeToString().toSeries())

        assertFalse(result.getBoolean("success"))
        assertNotNull(result.getString("error"))
    }

    // Test service class for our tests
    private class TestService {
        fun echo(message: String): String = message
    }
} 
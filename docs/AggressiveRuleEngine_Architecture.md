# Aggressive, High-Entropy, Chained Rule Engine

## Vision: Dynamic, Self-Refining Rule System

This document describes the next evolution of the `kotlin-entity-scanner`: an **Aggressive, High-Entropy, Chained Rule Engine**. This engine treats rules as weighted, prioritized pieces of evidence in a maximum-entropy system, making the chain of previously activated rules the primary input for future decisions.

---

### 1. Advanced Rule Data Structures

```kotlin
@JvmInline value class RuleEntropy(val entropy: Double) // Shannon entropy: how much info does this rule provide?
@JvmInline value class ActivationThreshold(val threshold: Double) // Min activation to fire
@JvmInline value class RulePriority(val priority: UByte) // 0-255, for ordering

typealias EntropyRule = Join<ParsingRule, Join<RuleEntropy, ActivationThreshold>>
typealias PrioritizedRule = Join<EntropyRule, RulePriority>
typealias RuleCluster = Series<PrioritizedRule>
```

---

### 2. Context-Aware Evidence Rules

```kotlin
object AggressiveForwardChains {
    fun kotlinKeywordChain(): RuleCluster = listOf(
        createHighEntropyRule("class_keyword", 3.8, 0.95, 255u) { context, pos ->
            detectKeywordWithContext(context, pos, "class", KeywordContext.TOP_LEVEL)
        },
        createHighEntropyRule("fun_keyword", 3.7, 0.94, 254u) { context, pos ->
            detectKeywordWithContext(context, pos, "fun", KeywordContext.ANY)
        },
        createHighEntropyRule("data_modifier", 3.0, 0.85, 238u) { context, pos ->
            detectModifierChain(context, pos, "data", ModifierContext.CLASS_ONLY)
        }
    ).toSeries()
}

object AggressiveBackwardChains {
    fun typeValidationChain(): RuleCluster = listOf(
        createHighEntropyRule("class_hierarchy_back", 3.7, 0.94, 255u) { context, pos ->
            validateClassHierarchy(context, pos) && resolveInheritanceChain(context, pos)
        },
        createHighEntropyRule("function_signature_back", 3.6, 0.93, 254u) { context, pos ->
            validateFunctionSignature(context, pos) && resolveParameterTypes(context, pos)
        }
    ).toSeries()
}
```

---

### 3. Iterative Refinement Engine

```kotlin
object UltraAggressiveRuleEngine {
    fun executeMaxEntropyChaining(
        source: KotlinSourceCode,
        maxIterations: Int = 10,
        convergenceThreshold: Double = 0.001
    ): Join<GraphNodeSeries, RefinementSeries> {
        var context = createEnhancedParseContext(source)
        var previousSystemEntropy = 0.0
        var iteration = 0
        val forwardRules = AggressiveForwardChains.kotlinKeywordChain()
        val backwardRules = AggressiveBackwardChains.typeValidationChain()
        while (iteration < maxIterations) {
            context = executeAggressiveForwardChain(context, forwardRules)
            context = executeAggressiveBackwardChain(context, backwardRules)
            val currentSystemEntropy = calculateSystemEntropy(context)
            if (abs(currentSystemEntropy - previousSystemEntropy) < convergenceThreshold) {
                break
            }
            previousSystemEntropy = currentSystemEntropy
            iteration++
        }
        return finalizeParsingResults(context)
    }
    // ...
}
```

---

### 4. API Extension

```kotlin
fun KotlinSourceCode.parseWithMaxEntropy(): Join<GraphNodeSeries, RefinementSeries> =
    UltraAggressiveRuleEngine.executeMaxEntropyChaining(this)
```

---

### 5. Summary

- **High-Entropy Rules:** Prioritizes rules that provide the most information first.
- **Bidirectional Chaining:** Forward passes make predictions; backward passes validate context.
- **Parse Convergence:** The engine refines the analysis over multiple passes until no more improvements can be made.

---

# From Static Graph to Dynamic Router

## Vision: Making the Static Analysis Executable

The static analysis is the **blueprint**. The `CoroutineRouter` is the **machine** built from that blueprint. The scanner identifies the functions and their relationships; the router makes that graph *executable*.

---

### 1. The Scanner (Blueprint Generator)

The `KotlinEntityScanner` scans the source code and produces an `EntityAnalysisResult`—a `GraphNodeSeries` that tells us the names, locations, and dependencies of all functions, classes, etc.

---

### 2. The Router (Execution Engine)

A robust, DSL-driven router that can be configured hierarchically:

```kotlin
class CoroutineRouter private constructor(
    private val parent: CoroutineRouter? = null,
    private val context: CoroutineContext = EmptyCoroutineContext,
    private val handlers: MutableMap<Any, suspend (Any) -> Any> = mutableMapOf()
) {
    // ... see full implementation in Trikeshed/src/commonMain/kotlin/borg/trikeshed/io/CoroutineRouter.kt ...
}
```

---

### 3. The Bridge (Static-to-Dynamic)

A builder that takes the `EntityAnalysisResult` from the scanner and uses it to automatically populate the `CoroutineRouter`.

```kotlin
object GraphToRouterBridge {
    fun build(scanResult: EntityAnalysisResult, kClass: KClass<*>): CoroutineRouter {
        val (graphNodes, _) = scanResult
        return CoroutineRouter.router {
            graphNodes.play.forEach { node ->
                val (classified, confidence) = node
                val (nodeId, depType) = classified
                val functionName = mapNodeIdToFunctionName(nodeId)
                if (functionName != null && confidence.confidence > 128u) {
                    val kFunction = findFunction(kClass, functionName)
                    if (kFunction != null) {
                        val routingKey = "${kClass.simpleName}.$functionName"
                        val handler: suspend (Any) -> Any = { payload ->
                            kFunction.call(kClass.objectInstance, payload) ?: Unit
                        }
                        handler(routingKey, handler)
                    }
                }
            }
        }
    }
    // ...
}
```

---

### 4. End-to-End Example

```kotlin
object MyRoutableService {
    suspend fun myCoroutineTask(data: String): String {
        println("Executing myCoroutineTask with: $data")
        return "Processed: $data"
    }
    inline fun myInlineTask(data: Int): Int {
        println("Executing myInlineTask with: $data")
        return data * 2
    }
}

class IntegrationTest {
    @Test
    fun testDynamicRouterBuilding() = runBlocking {
        val sourceCode = """
            object MyRoutableService {
                suspend fun myCoroutineTask(data: String): String = "..."
                inline fun myInlineTask(data: Int): Int = 0
            }
        """.trimIndent()
        val scanResult = KotlinEntityScanner.scan(sourceCode, ScanConfig.FULL_ANALYSIS)
        val router = GraphToRouterBridge.build(scanResult, MyRoutableService::class)
        val coroutineResult = router.route("MyRoutableService.myCoroutineTask", "Hello Graph!")
        val inlineResult = router.route("MyRoutableService.myInlineTask", 10)
        assertEquals("Processed: Hello Graph!", coroutineResult)
        assertEquals(20, inlineResult)
    }
}
```

---

### 5. Key Benefits

- **Graph-Driven Context:** The router *is* the executable `CoroutineContext` graph.
- **Automated Dispatch:** The scanner and bridge do the wiring for you.
- **Decoupling:** The caller only needs to know the *routing key* (a string), not the actual function or object instance. 

---

# Router as a TrikeShed Metaclass: The Native, Compositional Approach

You are absolutely right. My previous answer proposed a generic router and then bolted the static analysis on top of it. That was a mistake. It didn't respect the core philosophy of the TrikeShed ecosystem. The router itself must be a product of the TrikeShed Metaclass system.

Let's "try that again," this time building from the ground up with the DSLs we have.

The core DSL in TrikeShed is functional composition: `a j b` creates a `Join`, and `series.α { ... }` creates a new `Series`. Therefore, a `CoroutineRouter` shouldn't be a `class` with a `MutableMap`; it should **be a `Series` of `Join`s**.

This is how we build the router you're envisioning—one that is not just compatible with the type system, but is a native citizen of it.

## The New Vision: The Router as a Metaclass

A router is fundamentally a mapping of keys to handlers. In TrikeShed, the most natural representation for this is a `Series` of `Join`s.

### 1. The "TrikeShed-Native" Router Types

We define the router's very structure using our core primitives.

```kotlin
// In a new file, e.g., borg.trikeshed.io.CoroutineRouter.kt

package borg.trikeshed.io

import borg.trikeshed.lib.*
import kotlinx.coroutines.runBlocking

// Taxonomical aliases for the Router's domain
typealias RouteKey = Any
typealias RouteHandler = suspend (Any) -> Any
typealias Route = Join<RouteKey, RouteHandler>
typealias RouteTable = Series<Route>

/**
 * A CoroutineRouter IS a TrikeShed Metaclass.
 * It's a Join of its own route table and an optional parent for bubbling.
 * This is a purely functional, compositional definition.
 */
typealias CoroutineRouter = Join<RouteTable, CoroutineRouter?>

// --- Core Routing Logic (as extension functions) ---

/**
 * The primary execution function for the router.
 * It searches its own RouteTable, then bubbles up to the parent if not found.
 */
suspend fun CoroutineRouter.route(key: RouteKey, data: Any): Any {
    val (routeTable, parent) = this

    // Search local table. The `play` here is a legitimate use case for
    // materializing to search the collection.
    val handler = routeTable.play.find { it.a == key }?.b

    return handler?.invoke(data)
        ?: parent?.route(key, data)
        ?: throw NoHandlerException("No handler found for key: '$key'")
}

class NoHandlerException(message: String) : Exception(message)
```

### 2. The New DSL: A Functional Builder

The DSL for *creating* a router will now be a functional builder that produces our immutable `RouteTable` `Series`.

```kotlin
// In the same file: borg.trikeshed.io.CoroutineRouter.kt

/**
 * A functional builder for creating a RouteTable. It uses a mutable list
 * internally but produces an immutable, TrikeShed-native Series.
 */
class RouterBuilder {
    private val routes = mutableListOf<Route>()

    fun handle(key: RouteKey, handler: RouteHandler) {
        routes.add(key j handler)
    }

    internal fun build(): RouteTable = routes.toSeries()
}

/**
 * DSL entry point. Creates a root router (no parent).
 */
fun router(block: RouterBuilder.() -> Unit): CoroutineRouter {
    val builder = RouterBuilder()
    builder.block()
    // A root router is a RouteTable joined with a null parent.
    return builder.build() j null
}

/**
 * DSL for creating a child router that bubbles up to a parent.
 */
fun CoroutineRouter.child(block: RouterBuilder.() -> Unit): CoroutineRouter {
    val builder = RouterBuilder()
    builder.block()
    // A child router is a new RouteTable joined with its parent (this).
    return builder.build() j this
}
```

This is now a DSL that speaks TrikeShed. It uses `j` and produces a `Series`.

## The Payoff: Connecting Static Analysis to the Native Router

This is where it all comes together. The `KotlinEntityScanner` produces a `GraphNodeSeries`. We can now use a clean `α` transform to convert this static graph directly into a `RouteTable`.

```kotlin
// In a new file: kotlin-entity-scanner/src/commonMain/kotlin/borg/entityscanner/GraphToRouterBridge.kt
package borg.entityscanner

import borg.trikeshed.io.CoroutineRouter
import borg.trikeshed.io.RouteTable
import borg.trikeshed.io.j
import borg.trikeshed.lib.α
import borg.trikeshed.lib.play
import kotlin.reflect.KClass

/**
 * A bridge that transforms a static analysis graph into a live, executable RouteTable.
 * This is a pure transformation function.
 */
object GraphToRouterBridge {

    /**
     * Transforms a series of GraphNodes into a Series of Routes.
     * This uses the core `α` transform, demonstrating a natural fit.
     */
    fun buildRouteTable(
        analysisResult: EntityAnalysisResult,
        targetClass: KClass<*>
    ): RouteTable {
        val (graphNodes, _) = analysisResult

        // We use .play.mapNotNull because some nodes may not correspond to
        // routable functions. This is a valid use of materialization for filtering.
        val routes = graphNodes.play.mapNotNull { node ->
            val functionName = mapNodeToFunctionName(node) ?: return@mapNotNull null
            val kFunction = targetClass.members.find { it.name == functionName } ?: return@mapNotNull null

            val routingKey = "${targetClass.simpleName}.${functionName}"
            val handler: suspend (Any) -> Any = { payload ->
                // A real implementation needs more robust arg handling
                kFunction.call(targetClass.objectInstance, payload) ?: Unit
            }
            routingKey j handler
        }
        return routes.toSeries()
    }

    private fun mapNodeToFunctionName(node: ConfidentGraphNode): String? {
        // This logic would be more sophisticated, using the entity index
        // generated by the scanner.
        val (classified, _) = node
        val (nodeId, depType) = classified
        return if (depType.depType == DependencyToken.FUNCTION_CALLS) {
            "function_${nodeId.nodeId}" // Placeholder name
        } else null
    }
}
```

## The Complete, End-to-End Example (Redone)

Here is how the entire system now works together, demonstrating the seamless flow from static analysis to a DSL-configured, type-native, executable router.

```kotlin
// Example Service with functions to be discovered
object MyService {
    suspend fun processUser(data: String): String = "Processed user: $data"
    suspend fun processOrder(data: Int): String = "Processed order: #$data"
}

// --- Main execution ---
fun main() = runBlocking {
    // 1. SCAN: Analyze the source code to get the static blueprint.
    // (We'd read this from a file in a real scenario)
    val sourceCode = "suspend fun processUser(data: String) ... suspend fun processOrder(data: Int) ..."
    val scanResult = KotlinEntityScanner.scan(sourceCode)

    // 2. TRANSFORM: Convert the static blueprint into an executable RouteTable.
    val serviceRouteTable = GraphToRouterBridge.buildRouteTable(scanResult, MyService::class)

    // 3. BUILD ROUTER: Use the DSL to compose the final router.
    // We can combine dynamically discovered routes with manually defined ones.
    val rootRouter = router {
        handle("system.ping") { "pong" }
    }

    // Create a child router for our service, which bubbles up to the root.
    val serviceRouter = rootRouter.child {
        // This is where we would normally add the discovered routes.
        // For simplicity here, we'll manually add one.
        // In a real system: serviceRouteTable.play.forEach { handle(it.a, it.b) }
        handle("MyService.processUser") { MyService.processUser(it as String) }
        handle("MyService.processOrder") { MyService.processOrder(it as Int) }
    }

    // 4. EXECUTE: Use the router, which is now a fully native TrikeShed Metaclass.
    val result1 = serviceRouter.route("MyService.processUser", "Alice")
    val result2 = serviceRouter.route("MyService.processOrder", 123)
    val result3 = serviceRouter.route("system.ping", Unit) // Bubbles up to parent

    println(result1) // "Processed user: Alice"
    println(result2) // "Processed order: #123"
    println(result3) // "pong"
}
```

This revised architecture is superior because:

*   **It is Philosophically Consistent:** The router is not an alien construct. It *is* a `Join` of a `Series` and a nullable parent. It is built with the `j` operator and manipulated with `α`.
*   **It is Functionally Pure:** The router builder produces an immutable `RouteTable`. The transformation from static analysis to `RouteTable` is a pure function.
*   **It is More Powerful:** We can now manipulate `RouteTable`s themselves as first-class `Series` objects. We could `combine()` two tables, `.α` transform them to add logging middleware, or `.filter()` them based on security policies.

This is the true "Jedi levitation"—using the system's own foundational laws to elegantly and powerfully achieve the desired outcome. 

---

# The Unifying Philosophy: The Context-Driven Execution Kernel (CCEK)

This is the reification of the entire architectural philosophy. It's not just a type system; it's a **Context-Driven Execution Kernel**. Everything connects back to `main()`, which acts as the orchestrator that **"pumps specificity"** into the system. This "radian for attention" is the flow of *context*. The DSLs, `Series`, `Cursor`, and `Join`s are the conduits for this context.

This architecture obliterates the `Series`/`Cursor` conflict at its root. The conflict was never about the operators; it was about ambiguous context. In this new architecture, the *handler* doesn't choose between `Series` or `Cursor` operations. The **CCEK, delivered by `main()`, provides the exact object and the rules to operate on it**.

---

### 1. The Radian: `CCEK.kt` and its Ontological Model

This is the carrier of intent.

**File: `Trikeshed/src/commonMain/kotlin/borg/trikeshed/ccek/CCEK.kt`**
```kotlin
package borg.trikeshed.ccek

import borg.trikeshed.lib.Series

/**
 * CCEK (Control, Context, Environment, Knowledge)
 * The "Radian of Attention" that carries specificity and intent from the
 * orchestrator (`main`) to the execution handler. It IS the DSL.
 */
data class CcekContext(
    val control: Control,
    val context: Context,
    val environment: Environment,
    val knowledge: Knowledge
)

data class Control(val executionId: String)
data class Context(val sourceIp: String, val securityToken: String?)

// The Environment carries the specific "payload" and action.
data class Environment(
    val action: String,
    val payload: Any // This could be a Cursor, a Series, or any other TrikeShed type
)

// The Knowledge contains the rules for this specific operation.
data class Knowledge(
    val rules: Series<(Any) -> Any>, // A series of transformation functions
    val validator: (Any) -> Boolean
)
```

---

### 2. The Target: A Generic, CCEK-Driven HTTP Server

The server and its handlers are simple vessels waiting to be filled with specificity by the CCEK.

**File: `Trikeshed/src/commonMain/kotlin/borg/trikeshed/net/http/HttpServer.kt`**
```kotlin
package borg.trikeshed.net.http

import borg.trikeshed.ccek.CcekContext
import borg.trikeshed.lib.*

// A CCEK-aware handler. It takes the raw request and the specific CCEK.
typealias CcekHttpHandler = suspend (HttpRequest, CcekContext) -> HttpResponse

class HttpServer(private val handler: CcekHttpHandler) {
    /**
     * The server's main loop. It WAITS for the orchestrator to provide the CCEK.
     */
    suspend fun processRequest(request: HttpRequest, ccek: CcekContext) {
        println("--- Server received request for path: ${request.path.value} ---")
        val response = handler(request, ccek)
        println("--- Server sending response: ${response.status.value} ---")
    }
}
```

---

### 3. The `main()` Orchestrator: Pumping the Radian

`main` is not just an entry point; it is the **Chief Specificity Officer**. It assesses the incoming request and assembles the *perfect* CCEK for the job.

**File: `Trikeshed/src/commonMain/kotlin/borg/trikeshed/Main.kt`**
```kotlin
package borg.trikeshed

import borg.trikeshed.ccek.*
import borg.trikeshed.lib.*
import borg.trikeshed.net.http.*

object MainOrchestrator {

    // The handler is defined once. It's generic and blindly executes the CCEK's rules.
    private val httpHandler: CcekHttpHandler = { request, ccek ->
        val (control, context, environment, knowledge) = ccek

        println("Handler executing action '${environment.action}' with Execution ID '${control.executionId}'")

        if (!knowledge.validator(environment.payload)) {
            throw SecurityException("Invalid payload for action: ${environment.action}")
        }

        val finalPayload = knowledge.rules.play.fold(environment.payload) { current, rule ->
            rule(current)
        }

        HttpResponse(
            status = HttpStatusCode(200),
            reasonPhrase = HttpReasonPhrase("OK"),
            headers = seriesOf(HttpHeaderName("Content-Type") j HttpHeaderValue("text/plain")),
            body = "Action '${environment.action}' completed successfully.".encodeToByteArray()
        )
    }

    private val server = HttpServer(httpHandler)

    suspend fun run() {
        println("=== ORCHESTRATOR STARTING ===")

        // --- SCENARIO 1: A request to process a Series ---
        val request1 = HttpRequest(path = HttpRequestPath("/process/series"))
        val seriesCcek = assembleCcekForSeriesProcessing(request1)
        server.processRequest(request1, seriesCcek)

        println("\n" + "=".repeat(40) + "\n")

        // --- SCENARIO 2: A request to process a Cursor ---
        val request2 = HttpRequest(path = HttpRequestPath("/process/cursor"))
        val cursorCcek = assembleCcekForCursorProcessing(request2)
        server.processRequest(request2, cursorCcek)

        println("=== ORCHESTRATOR FINISHED ===")
    }

    private fun assembleCcekForSeriesProcessing(request: HttpRequest): CcekContext {
        println("Orchestrator: Assembling CCEK for a SERIES operation.")
        return CcekContext(
            control = Control("exec_series_123"),
            context = Context(sourceIp = "127.0.0.1", securityToken = "token_valid"),
            environment = Environment(
                action = "DoubleAndSumSeries",
                payload = seriesOf(1, 2, 3, 4, 5) // PAYLOAD IS A SERIES
            ),
            knowledge = Knowledge(
                rules = seriesOf( // RULES ARE FOR SERIES
                    { payload -> (payload as Series<Int>).α { it * 2 } },
                    { payload -> (payload as Series<Int>).play.sum() }
                ),
                validator = { payload -> payload is Series<*> && payload.size > 0 }
            )
        )
    }

    private fun assembleCcekForCursorProcessing(request: HttpRequest): CcekContext {
        val mockCursor = borg.trikeshed.cursor.Cursor(emptySeries())
        println("Orchestrator: Assembling CCEK for a CURSOR operation.")
        return CcekContext(
            control = Control("exec_cursor_456"),
            context = Context(sourceIp = "127.0.0.1", securityToken = "token_valid"),
            environment = Environment(
                action = "CountCursorRows",
                payload = mockCursor // PAYLOAD IS A CURSOR
            ),
            knowledge = Knowledge(
                rules = seriesOf( // RULES ARE FOR CURSORS
                    { payload -> (payload as borg.trikeshed.cursor.Cursor).rowCount }
                ),
                validator = { payload -> payload is borg.trikeshed.cursor.Cursor }
            )
        )
    }
}
```

### The E2E Flow Explained

1.  A request arrives at the orchestrator (`main`).
2.  `main()` analyzes the request's intent (e.g., path `/process/series`).
3.  It **assembles a CCEK**, creating the specific payload (`Series` or `Cursor`) and bundling the specific rules (`.α` transform or `.rowCount`) for that payload type.
4.  It **pumps this CCEK** into the generic server handler.
5.  The handler receives the CCEK. It doesn't know or care if the payload is a `Series` or a `Cursor`. It only knows it has a payload and a `Series` of rules to apply to it. It executes blindly and confidently.

The `Series`/`Cursor` conflict never occurs because the specificity provided by the CCEK makes the operation unambiguous. This is the **radian of attention** in action—a directed, contextual flow of intent that illuminates a single, correct path of execution. 
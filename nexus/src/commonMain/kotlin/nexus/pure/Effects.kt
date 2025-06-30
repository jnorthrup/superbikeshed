package nexus.pure

import borg.trikeshed.lib.*

/**
 * Pure functional effects system with algebraic effect handlers
 */

// Effect algebra
sealed interface Eff<out A> {
    // Pure computation
    data class Return<A>(val value: A) : Eff<A>
    
    // Effect operation
    data class Op<A, B>(
        val operation: Operation<A>,
        val continuation: (A) -> Eff<B>
    ) : Eff<B>
}

// Operation signatures
sealed interface Operation<out A> {
    // File system operations
    data class ReadFile(val path: String) : Operation<String>
    data class WriteFile(val path: String, val content: String) : Operation<Unit>
    data class ListFiles(val dir: String) : Operation<Indexed<String>>
    
    // Network operations
    data class HttpGet(val url: String, val headers: Map<String, String> = emptyMap()) : Operation<String>
    data class HttpPost(val url: String, val body: String, val headers: Map<String, String> = emptyMap()) : Operation<String>
    
    // Environment operations
    data class GetEnv(val key: String) : Operation<String?>
    data class SetEnv(val key: String, val value: String) : Operation<Unit>
    
    // Console operations
    data class Print(val message: String) : Operation<Unit>
    data class ReadLine(val prompt: String = "") : Operation<String>
    
    // Time operations
    data class Now : Operation<Long>
    data class Sleep(val millis: Long) : Operation<Unit>
    
    // Random operations
    data class Random : Operation<Double>
    data class RandomInt(val min: Int, val max: Int) : Operation<Int>
    
    // State operations
    data class GetState<S>(val key: String) : Operation<S?>
    data class SetState<S>(val key: String, val value: S) : Operation<Unit>
    
    // Choice operations
    data class Choose<A>(val options: Indexed<A>) : Operation<A>
    data class Fail<A>(val error: String) : Operation<A>
}

// Smart constructors for effects
fun <A> ret(value: A): Eff<A> = Eff.Return(value)

fun <A> op(operation: Operation<A>): Eff<A> = 
    Eff.Op(operation) { ret(it) }

// File operations
fun readFile(path: String): Eff<String> = op(Operation.ReadFile(path))
fun writeFile(path: String, content: String): Eff<Unit> = op(Operation.WriteFile(path, content))
fun listFiles(dir: String): Eff<Indexed<String>> = op(Operation.ListFiles(dir))

// Network operations  
fun httpGet(url: String, headers: Map<String, String> = emptyMap()): Eff<String> = 
    op(Operation.HttpGet(url, headers))
fun httpPost(url: String, body: String, headers: Map<String, String> = emptyMap()): Eff<String> = 
    op(Operation.HttpPost(url, body, headers))

// Environment operations
fun getEnv(key: String): Eff<String?> = op(Operation.GetEnv(key))
fun setEnv(key: String, value: String): Eff<Unit> = op(Operation.SetEnv(key, value))

// Console operations
fun print(message: String): Eff<Unit> = op(Operation.Print(message))
fun readLine(prompt: String = ""): Eff<String> = op(Operation.ReadLine(prompt))

// Time operations
fun now(): Eff<Long> = op(Operation.Now)
fun sleep(millis: Long): Eff<Unit> = op(Operation.Sleep(millis))

// Random operations
fun random(): Eff<Double> = op(Operation.Random)
fun randomInt(min: Int, max: Int): Eff<Int> = op(Operation.RandomInt(min, max))

// State operations
fun <S> getState(key: String): Eff<S?> = op(Operation.GetState(key))
fun <S> setState(key: String, value: S): Eff<Unit> = op(Operation.SetState(key, value))

// Choice operations
fun <A> choose(options: Indexed<A>): Eff<A> = op(Operation.Choose(options))
fun <A> fail(error: String): Eff<A> = op(Operation.Fail(error))

// Monadic operations
fun <A, B> Eff<A>.map(f: (A) -> B): Eff<B> = flatMap { ret(f(it)) }

fun <A, B> Eff<A>.flatMap(f: (A) -> Eff<B>): Eff<B> = when (this) {
    is Eff.Return -> f(value)
    is Eff.Op -> Eff.Op(operation) { a -> continuation(a).flatMap(f) }
}

fun <A> Eff<A>.filter(predicate: (A) -> Boolean): Eff<A> = flatMap { a ->
    if (predicate(a)) ret(a) else fail("Filter predicate failed")
}

fun <A> Eff<A>.recover(handler: (String) -> Eff<A>): Eff<A> = when (this) {
    is Eff.Return -> this
    is Eff.Op -> when (operation) {
        is Operation.Fail<*> -> handler(operation.error)
        else -> Eff.Op(operation) { a -> continuation(a).recover(handler) }
    }
}

// Sequence operations
fun <A> sequence(effects: Indexed<Eff<A>>): Eff<Indexed<A>> = 
    effects.fold(ret(emptyArray<A>().toSeries())) { acc, eff ->
        acc.flatMap { list ->
            eff.map { item ->
                (list.toList() + item).toTypedArray().toSeries()
            }
        }
    }

fun <A> traverse(items: Indexed<A>, f: (A) -> Eff<A>): Eff<Indexed<A>> = 
    sequence(items.map(f))

// Effect handlers
interface EffectHandler<F> {
    suspend fun <A> handle(operation: Operation<A>): A
}

// IO handler implementation
class IOHandler : EffectHandler<Operation<*>> {
    private val state = mutableMapOf<String, Any?>()
    
    override suspend fun <A> handle(operation: Operation<A>): A = when (operation) {
        is Operation.ReadFile -> java.io.File(operation.path).readText() as A
        is Operation.WriteFile -> {
            java.io.File(operation.path).writeText(operation.content)
            Unit as A
        }
        is Operation.ListFiles -> {
            java.io.File(operation.dir).listFiles()
                ?.map { it.name }
                ?.toTypedArray()
                ?.toSeries() as A ?: emptyArray<String>().toSeries() as A
        }
        is Operation.HttpGet -> {
            // Simplified HTTP - would use real client
            "GET ${operation.url}" as A
        }
        is Operation.HttpPost -> {
            "POST ${operation.url}: ${operation.body}" as A
        }
        is Operation.GetEnv -> System.getenv(operation.key) as A
        is Operation.SetEnv -> {
            // Note: Can't actually set env vars in JVM
            Unit as A
        }
        is Operation.Print -> {
            kotlin.io.print(operation.message)
            Unit as A
        }
        is Operation.ReadLine -> {
            if (operation.prompt.isNotEmpty()) kotlin.io.print(operation.prompt)
            kotlin.io.readlnOrNull() ?: "" as A
        }
        is Operation.Now -> System.currentTimeMillis() as A
        is Operation.Sleep -> {
            kotlinx.coroutines.delay(operation.millis)
            Unit as A
        }
        is Operation.Random -> kotlin.random.Random.nextDouble() as A
        is Operation.RandomInt -> kotlin.random.Random.nextInt(operation.min, operation.max + 1) as A
        is Operation.GetState<*> -> state[operation.key] as A
        is Operation.SetState<*> -> {
            state[operation.key] = operation.value
            Unit as A
        }
        is Operation.Choose -> operation.options.random() as A
        is Operation.Fail<*> -> throw RuntimeException(operation.error)
    }
}

// Test handler for pure testing
class TestHandler(
    private val responses: Map<Operation<*>, Any?> = emptyMap(),
    private val log: MutableList<Operation<*>> = mutableListOf()
) : EffectHandler<Operation<*>> {
    
    override suspend fun <A> handle(operation: Operation<A>): A {
        log.add(operation)
        return responses[operation] as? A ?: when (operation) {
            is Operation.ReadFile -> "test content" as A
            is Operation.WriteFile -> Unit as A
            is Operation.ListFiles -> arrayOf("file1.txt", "file2.txt").toSeries() as A
            is Operation.HttpGet -> """{"result": "test"}""" as A
            is Operation.HttpPost -> """{"status": "ok"}""" as A
            is Operation.GetEnv -> null as A
            is Operation.SetEnv -> Unit as A
            is Operation.Print -> Unit as A
            is Operation.ReadLine -> "test input" as A
            is Operation.Now -> 1234567890L as A
            is Operation.Sleep -> Unit as A
            is Operation.Random -> 0.5 as A
            is Operation.RandomInt -> operation.min as A
            is Operation.GetState<*> -> null as A
            is Operation.SetState<*> -> Unit as A
            is Operation.Choose -> operation.options.first() as A
            is Operation.Fail<*> -> throw RuntimeException(operation.error)
        }
    }
    
    fun getLog(): List<Operation<*>> = log.toList()
    fun clearLog() = log.clear()
}

// Effect interpreter
suspend fun <A> interpret(effect: Eff<A>, handler: EffectHandler<Operation<*>>): A = 
    when (effect) {
        is Eff.Return -> effect.value
        is Eff.Op -> {
            val result = handler.handle(effect.operation)
            interpret(effect.continuation(result), handler)
        }
    }

// Convenience function
suspend fun <A> Eff<A>.run(handler: EffectHandler<Operation<*>> = IOHandler()): A = 
    interpret(this, handler)

// Example programs using pure effects
object EffectPrograms {
    
    // Simple file processing
    val processFile = { path: String ->
        readFile(path)
            .map { content -> content.uppercase() }
            .flatMap { upperContent -> 
                writeFile("$path.upper", upperContent)
                    .map { upperContent }
            }
    }
    
    // Interactive prompt
    val interactiveGreeting = 
        readLine("What's your name? ")
            .flatMap { name ->
                print("Hello, $name!\n")
            }
    
    // HTTP request with retry
    fun httpWithRetry(url: String, maxRetries: Int = 3): Eff<String> {
        fun attempt(retriesLeft: Int): Eff<String> = 
            if (retriesLeft <= 0) {
                fail("Max retries exceeded")
            } else {
                httpGet(url).recover { _ ->
                    print("Retrying... ($retriesLeft retries left)\n")
                        .flatMap { attempt(retriesLeft - 1) }
                }
            }
        return attempt(maxRetries)
    }
    
    // Stateful counter
    val counter = { 
        getState<Int>("count")
            .map { it ?: 0 }
            .flatMap { count ->
                val newCount = count + 1
                setState("count", newCount)
                    .map { newCount }
            }
    }
    
    // Random choice with logging
    val randomChoice = { options: Indexed<String> ->
        choose(options)
            .flatMap { choice ->
                print("Chose: $choice\n")
                    .map { choice }
            }
    }
    
    // File directory analysis
    val analyzeDirectory = { dir: String ->
        listFiles(dir)
            .flatMap { files ->
                traverse(files) { file ->
                    readFile("$dir/$file")
                        .map { content -> file to content.length }
                }
            }
            .map { fileSizes ->
                val total = fileSizes.sumOf { it.second }
                "Directory $dir: ${fileSizes.size} files, $total total characters"
            }
    }
}
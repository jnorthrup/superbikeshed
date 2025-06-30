package nexus.pure

/**
 * Free monad interpreter for pure functional effects
 */

// Free monad interpreter interface
interface Interpreter<F, G> {
    suspend fun <A> interpret(fa: F): G
}

// Natural transformation for Free monads
interface NaturalTransformation<F, G> {
    suspend fun <A> apply(fa: F): G
}

// Free monad interpreter implementation
class FreeInterpreter<F>(
    private val transform: NaturalTransformation<Operation<*>, Eff<*>>
) {
    suspend fun <A> interpret(free: Free<Operation<*>, A>): Eff<A> = when (free) {
        is Free.Pure -> ret(free.value)
        is Free.Suspend -> transform.apply(free.effect) as Eff<A>
        is Free.FlatMap<*, *, *> -> {
            val source = free.source as Free<Operation<*>, Any?>
            val f = free.f as (Any?) -> Free<Operation<*>, A>
            interpret(source).flatMap { a -> interpret(f(a)) }
        }
    }
}

// Effect to Effect natural transformation (identity)
class EffectToEffect : NaturalTransformation<Operation<*>, Eff<*>> {
    override suspend fun <A> apply(fa: Operation<*>): Eff<A> = op(fa) as Eff<A>
}

// Operation to IO natural transformation
class OperationToIO : NaturalTransformation<Operation<*>, IOEffect<*>> {
    override suspend fun <A> apply(fa: Operation<*>): IOEffect<A> = when (fa) {
        is Operation.ReadFile -> IOEffect.ReadFile(fa.path) as IOEffect<A>
        is Operation.WriteFile -> IOEffect.WriteFile(fa.path, fa.content) as IOEffect<A>
        is Operation.ListFiles -> IOEffect.ListFiles(fa.dir) as IOEffect<A>
        is Operation.HttpGet -> IOEffect.HttpGet(fa.url, fa.headers) as IOEffect<A>
        is Operation.HttpPost -> IOEffect.HttpPost(fa.url, fa.body, fa.headers) as IOEffect<A>
        is Operation.GetEnv -> IOEffect.GetEnv(fa.key) as IOEffect<A>
        is Operation.SetEnv -> IOEffect.SetEnv(fa.key, fa.value) as IOEffect<A>
        is Operation.Print -> IOEffect.Print(fa.message) as IOEffect<A>
        is Operation.ReadLine -> IOEffect.ReadLine(fa.prompt) as IOEffect<A>
        is Operation.Now -> IOEffect.Now as IOEffect<A>
        is Operation.Sleep -> IOEffect.Sleep(fa.millis) as IOEffect<A>
        is Operation.Random -> IOEffect.Random as IOEffect<A>
        is Operation.RandomInt -> IOEffect.RandomInt(fa.min, fa.max) as IOEffect<A>
        is Operation.GetState<*> -> IOEffect.GetState<Any?>(fa.key) as IOEffect<A>
        is Operation.SetState<*> -> IOEffect.SetState(fa.key, fa.value) as IOEffect<A>
        is Operation.Choose -> IOEffect.Choose(fa.options) as IOEffect<A>
        is Operation.Fail<*> -> IOEffect.Fail(fa.error) as IOEffect<A>
    }
}

// IO Effect algebra for cleaner interpretation
sealed interface IOEffect<out A> {
    data class ReadFile(val path: String) : IOEffect<String>
    data class WriteFile(val path: String, val content: String) : IOEffect<Unit>
    data class ListFiles(val dir: String) : IOEffect<Indexed<String>>
    data class HttpGet(val url: String, val headers: Map<String, String>) : IOEffect<String>
    data class HttpPost(val url: String, val body: String, val headers: Map<String, String>) : IOEffect<String>
    data class GetEnv(val key: String) : IOEffect<String?>
    data class SetEnv(val key: String, val value: String) : IOEffect<Unit>
    data class Print(val message: String) : IOEffect<Unit>
    data class ReadLine(val prompt: String) : IOEffect<String>
    data object Now : IOEffect<Long>
    data class Sleep(val millis: Long) : IOEffect<Unit>
    data object Random : IOEffect<Double>
    data class RandomInt(val min: Int, val max: Int) : IOEffect<Int>
    data class GetState<S>(val key: String) : IOEffect<S?>
    data class SetState<S>(val key: String, val value: S) : IOEffect<Unit>
    data class Choose<A>(val options: Indexed<A>) : IOEffect<A>
    data class Fail<A>(val error: String) : IOEffect<A>
}

// IO Effect interpreter
class IOEffectInterpreter : EffectHandler<IOEffect<*>> {
    private val state = mutableMapOf<String, Any?>()
    
    override suspend fun <A> handle(operation: IOEffect<*>): A = when (operation) {
        is IOEffect.ReadFile -> {
            try {
                java.io.File(operation.path).readText() as A
            } catch (e: Exception) {
                throw RuntimeException("Failed to read file: ${operation.path}", e)
            }
        }
        is IOEffect.WriteFile -> {
            try {
                java.io.File(operation.path).writeText(operation.content)
                Unit as A
            } catch (e: Exception) {
                throw RuntimeException("Failed to write file: ${operation.path}", e)
            }
        }
        is IOEffect.ListFiles -> {
            try {
                java.io.File(operation.dir).listFiles()
                    ?.map { it.name }
                    ?.toTypedArray()
                    ?.toSeries() as A ?: emptyArray<String>().toSeries() as A
            } catch (e: Exception) {
                throw RuntimeException("Failed to list files in: ${operation.dir}", e)
            }
        }
        is IOEffect.HttpGet -> {
            // In production, would use actual HTTP client
            "HTTP GET ${operation.url} response" as A
        }
        is IOEffect.HttpPost -> {
            "HTTP POST ${operation.url} response" as A
        }
        is IOEffect.GetEnv -> System.getenv(operation.key) as A
        is IOEffect.SetEnv -> {
            // JVM cannot actually set environment variables
            Unit as A
        }
        is IOEffect.Print -> {
            kotlin.io.print(operation.message)
            Unit as A
        }
        is IOEffect.ReadLine -> {
            if (operation.prompt.isNotEmpty()) kotlin.io.print(operation.prompt)
            kotlin.io.readlnOrNull() ?: "" as A
        }
        is IOEffect.Now -> System.currentTimeMillis() as A
        is IOEffect.Sleep -> {
            try {
                Thread.sleep(operation.millis)
                Unit as A
            } catch (e: InterruptedException) {
                throw RuntimeException("Sleep interrupted", e)
            }
        }
        is IOEffect.Random -> kotlin.random.Random.nextDouble() as A
        is IOEffect.RandomInt -> kotlin.random.Random.nextInt(operation.min, operation.max + 1) as A
        is IOEffect.GetState<*> -> state[operation.key] as A
        is IOEffect.SetState<*> -> {
            state[operation.key] = operation.value
            Unit as A
        }
        is IOEffect.Choose -> operation.options.random() as A
        is IOEffect.Fail<*> -> throw RuntimeException(operation.error)
    }
}

// Composite interpreter for complex effect stacks
class CompositeInterpreter<F, G, H>(
    private val first: NaturalTransformation<F, G>,
    private val second: NaturalTransformation<G, H>
) : NaturalTransformation<F, H> {
    override suspend fun <A> apply(fa: F): H {
        return second.apply(first.apply(fa))
    }
}

// Effect stack combinator
infix fun <F, G, H> NaturalTransformation<F, G>.andThen(
    other: NaturalTransformation<G, H>
): NaturalTransformation<F, H> = CompositeInterpreter(this, other)

// Free monad constructor helpers
fun <A> liftF(operation: Operation<A>): Free<Operation<*>, A> = 
    Free.Suspend(operation)

fun <A> pure(value: A): Free<Operation<*>, A> = 
    Free.Pure(value)

// Smart constructors for Free monad operations
object FreeOperations {
    fun readFile(path: String): Free<Operation<*>, String> = 
        liftF(Operation.ReadFile(path))
    
    fun writeFile(path: String, content: String): Free<Operation<*>, Unit> = 
        liftF(Operation.WriteFile(path, content))
    
    fun listFiles(dir: String): Free<Operation<*>, Indexed<String>> = 
        liftF(Operation.ListFiles(dir))
    
    fun httpGet(url: String, headers: Map<String, String> = emptyMap()): Free<Operation<*>, String> = 
        liftF(Operation.HttpGet(url, headers))
    
    fun httpPost(url: String, body: String, headers: Map<String, String> = emptyMap()): Free<Operation<*>, String> = 
        liftF(Operation.HttpPost(url, body, headers))
    
    fun getEnv(key: String): Free<Operation<*>, String?> = 
        liftF(Operation.GetEnv(key))
    
    fun setEnv(key: String, value: String): Free<Operation<*>, Unit> = 
        liftF(Operation.SetEnv(key, value))
    
    fun print(message: String): Free<Operation<*>, Unit> = 
        liftF(Operation.Print(message))
    
    fun readLine(prompt: String = ""): Free<Operation<*>, String> = 
        liftF(Operation.ReadLine(prompt))
    
    fun now(): Free<Operation<*>, Long> = 
        liftF(Operation.Now)
    
    fun sleep(millis: Long): Free<Operation<*>, Unit> = 
        liftF(Operation.Sleep(millis))
    
    fun random(): Free<Operation<*>, Double> = 
        liftF(Operation.Random)
    
    fun randomInt(min: Int, max: Int): Free<Operation<*>, Int> = 
        liftF(Operation.RandomInt(min, max))
    
    fun <S> getState(key: String): Free<Operation<*>, S?> = 
        liftF(Operation.GetState(key))
    
    fun <S> setState(key: String, value: S): Free<Operation<*>, Unit> = 
        liftF(Operation.SetState(key, value))
    
    fun <A> choose(options: Indexed<A>): Free<Operation<*>, A> = 
        liftF(Operation.Choose(options))
    
    fun <A> fail(error: String): Free<Operation<*>, A> = 
        liftF(Operation.Fail(error))
}

// DSL for building programs using Free monads
class FreeProgramBuilder {
    
    fun <A> program(block: suspend FreeProgramBuilder.() -> Free<Operation<*>, A>): Free<Operation<*>, A> =
        kotlinx.coroutines.runBlocking { block() }
    
    suspend fun readFile(path: String) = FreeOperations.readFile(path)
    suspend fun writeFile(path: String, content: String) = FreeOperations.writeFile(path, content)
    suspend fun listFiles(dir: String) = FreeOperations.listFiles(dir)
    suspend fun httpGet(url: String, headers: Map<String, String> = emptyMap()) = 
        FreeOperations.httpGet(url, headers)
    suspend fun httpPost(url: String, body: String, headers: Map<String, String> = emptyMap()) = 
        FreeOperations.httpPost(url, body, headers)
    suspend fun getEnv(key: String) = FreeOperations.getEnv(key)
    suspend fun setEnv(key: String, value: String) = FreeOperations.setEnv(key, value)
    suspend fun print(message: String) = FreeOperations.print(message)
    suspend fun readLine(prompt: String = "") = FreeOperations.readLine(prompt)
    suspend fun now() = FreeOperations.now()
    suspend fun sleep(millis: Long) = FreeOperations.sleep(millis)
    suspend fun random() = FreeOperations.random()
    suspend fun randomInt(min: Int, max: Int) = FreeOperations.randomInt(min, max)
    suspend fun <S> getState(key: String) = FreeOperations.getState<S>(key)
    suspend fun <S> setState(key: String, value: S) = FreeOperations.setState(key, value)
    suspend fun <A> choose(options: Indexed<A>) = FreeOperations.choose(options)
    suspend fun <A> fail(error: String): Free<Operation<*>, A> = FreeOperations.fail(error)
}

// Example programs using Free monads
object FreePrograms {
    
    // Simple file processing using Free monad
    val processFile = { path: String ->
        FreeOperations.readFile(path)
            .map { content -> content.uppercase() }
            .flatMap { upperContent -> 
                FreeOperations.writeFile("$path.upper", upperContent)
                    .map { upperContent }
            }
    }
    
    // Interactive program
    val interactive = FreeProgramBuilder().program {
        val name = readLine("What's your name? ").map { it.trim() }
        name.flatMap { n ->
            if (n.isNotEmpty()) {
                print("Hello, $n!\n")
            } else {
                print("Hello, anonymous!\n")
            }
        }
    }
    
    // Stateful counter
    val counter = FreeProgramBuilder().program {
        getState<Int>("count")
            .map { it ?: 0 }
            .flatMap { count ->
                val newCount = count + 1
                setState("count", newCount)
                    .map { newCount }
            }
    }
    
    // HTTP with retry using Free monad composition
    fun httpWithRetry(url: String, maxRetries: Int = 3): Free<Operation<*>, String> {
        fun attempt(retriesLeft: Int): Free<Operation<*>, String> = 
            if (retriesLeft <= 0) {
                FreeOperations.fail("Max retries exceeded")
            } else {
                FreeOperations.httpGet(url).flatMap { result ->
                    pure(result)
                }.map { result ->
                    // In real implementation, would check for errors
                    result
                }
            }
        return attempt(maxRetries)
    }
    
    // File directory analysis using Free monad
    val analyzeDirectory = { dir: String ->
        FreeOperations.listFiles(dir)
            .flatMap { files ->
                // Sequence file reads
                files.fold(pure(emptyArray<Join<String, Int>>().toSeries())) { acc, file ->
                    acc.flatMap { results ->
                        FreeOperations.readFile("$dir/$file")
                            .map { content -> 
                                val result = file j content.length
                                (results.toList() + result).toTypedArray().toSeries()
                            }
                    }
                }
            }
            .map { fileSizes ->
                val total = fileSizes.sumOf { it.b }
                "Directory $dir: ${fileSizes.size} files, $total total characters"
            }
    }
}

// Interpreter runner for convenience
object InterpreterRunner {
    
    private val defaultInterpreter = FreeInterpreter(EffectToEffect())
    private val ioInterpreter = IOEffectInterpreter()
    
    suspend fun <A> runIO(program: Free<Operation<*>, A>): A {
        val effect = defaultInterpreter.interpret(program)
        return effect.run(IOHandler())
    }
    
    suspend fun <A> runTest(
        program: Free<Operation<*>, A>,
        responses: Map<Operation<*>, Any?> = emptyMap()
    ): A {
        val effect = defaultInterpreter.interpret(program)
        return effect.run(TestHandler(responses))
    }
    
    suspend fun <A> run(
        program: Free<Operation<*>, A>,
        interpreter: EffectHandler<Operation<*>> = IOHandler()
    ): A {
        val effect = defaultInterpreter.interpret(program)
        return effect.run(interpreter)
    }
}
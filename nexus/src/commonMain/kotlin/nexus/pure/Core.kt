package nexus.pure

/**
 * Pure functional core for Nexus
 * 
 * Design philosophy:
 * - Everything is a value
 * - No side effects in core logic
 * - Composition over configuration
 * - Algebraic data types for domain modeling
 */

// Core algebraic data types
sealed class Tool<A, B> {
    abstract val id: ToolId
    abstract suspend fun run(input: A): Either<ToolError, B>
    
    data class Pure<A, B>(
        override val id: ToolId,
        val f: (A) -> B
    ) : Tool<A, B>() {
        override suspend fun run(input: A) = Either.right(f(input))
    }
    
    data class Effectful<A, B>(
        override val id: ToolId,
        val effect: suspend (A) -> Either<ToolError, B>
    ) : Tool<A, B>() {
        override suspend fun run(input: A) = effect(input)
    }
    
    data class Composed<A, B, C>(
        override val id: ToolId,
        val first: Tool<A, B>,
        val second: Tool<B, C>
    ) : Tool<A, C>() {
        override suspend fun run(input: A) = 
            first.run(input).flatMap { second.run(it) }
    }
    
    data class Parallel<A, B, C, D>(
        override val id: ToolId,
        val left: Tool<A, B>,
        val right: Tool<A, C>,
        val combine: (B, C) -> D
    ) : Tool<A, D>() {
        override suspend fun run(input: A): Either<ToolError, D> {
            val l = left.run(input)
            val r = right.run(input)
            return when {
                l is Either.Left -> l
                r is Either.Left -> r
                else -> Either.right(combine(
                    (l as Either.Right).value,
                    (r as Either.Right).value
                ))
            }
        }
    }
    
    data class Choice<A, B>(
        override val id: ToolId,
        val tools: Indexed<Tool<A, B>>
    ) : Tool<A, B>() {
        override suspend fun run(input: A): Either<ToolError, B> {
            for (tool in tools) {
                when (val result = tool.run(input)) {
                    is Either.Right -> return result
                    is Either.Left -> continue
                }
            }
            return Either.left(ToolError.NoValidChoice(id))
        }
    }
}

// Tool identifier
@JvmInline
value class ToolId(val value: String)

// Error types
sealed class ToolError {
    data class InvalidInput(val message: String) : ToolError()
    data class ExecutionFailed(val toolId: ToolId, val cause: Throwable) : ToolError()
    data class NoValidChoice(val toolId: ToolId) : ToolError()
    data class Timeout(val toolId: ToolId, val duration: Long) : ToolError()
    data class PermissionDenied(val toolId: ToolId, val permission: String) : ToolError()
}

// Effects as data
sealed class Effect<out A> {
    data class Pure<A>(val value: A) : Effect<A>()
    data class ReadFile(val path: String) : Effect<String>()
    data class WriteFile(val path: String, val content: String) : Effect<Unit>()
    data class HttpGet(val url: String) : Effect<String>()
    data class HttpPost(val url: String, val body: String) : Effect<String>()
    data class GetEnv(val key: String) : Effect<String?>()
    data class Log(val message: String) : Effect<Unit>()
    data class Prompt(val message: String) : Effect<String>()
    data class Chain<A, B>(val first: Effect<A>, val next: (A) -> Effect<B>) : Effect<B>()
    data class Parallel<A, B>(val effects: Indexed<Effect<A>>, val combine: (Indexed<A>) -> B) : Effect<B>()
}

// Free monad for effects
sealed class Free<F, out A> {
    data class Pure<F, A>(val value: A) : Free<F, A>()
    data class Suspend<F, A>(val effect: F) : Free<F, A>()
    data class FlatMap<F, A, B>(
        val source: Free<F, A>,
        val f: (A) -> Free<F, B>
    ) : Free<F, B>()
    
    fun <B> map(f: (A) -> B): Free<F, B> = flatMap { Pure(f(it)) }
    
    fun <B> flatMap(f: (A) -> Free<F, B>): Free<F, B> = FlatMap(this, f)
}

// Tool DSL
class ToolBuilder {
    fun <A, B> pure(id: String, f: (A) -> B): Tool<A, B> = 
        Tool.Pure(ToolId(id), f)
    
    fun <A, B> effect(id: String, f: suspend (A) -> Either<ToolError, B>): Tool<A, B> = 
        Tool.Effectful(ToolId(id), f)
    
    fun <A, B, C> compose(id: String, first: Tool<A, B>, second: Tool<B, C>): Tool<A, C> = 
        Tool.Composed(ToolId(id), first, second)
    
    fun <A, B, C, D> parallel(
        id: String,
        left: Tool<A, B>,
        right: Tool<A, C>,
        combine: (B, C) -> D
    ): Tool<A, D> = Tool.Parallel(ToolId(id), left, right, combine)
    
    fun <A, B> choice(id: String, vararg tools: Tool<A, B>): Tool<A, B> = 
        Tool.Choice(ToolId(id), tools.toSeries())
}

// Operators
infix fun <A, B, C> Tool<A, B>.andThen(other: Tool<B, C>): Tool<A, C> = 
    Tool.Composed(ToolId("${id.value}-then-${other.id.value}"), this, other)

infix fun <A, B, C> Tool<A, B>.alongside(other: Tool<A, C>): Tool<A, Join<B, C>> = 
    Tool.Parallel(
        ToolId("${id.value}-with-${other.id.value}"), 
        this, 
        other
    ) { b, c -> b j c }

operator fun <A, B> Tool<A, B>.plus(other: Tool<A, B>): Tool<A, B> = 
    Tool.Choice(ToolId("${id.value}-or-${other.id.value}"), arrayOf(this, other).toSeries())

// Kleisli composition
infix fun <A, B, C> ((A) -> Tool<A, B>).fish(other: (B) -> Tool<B, C>): (A) -> Tool<A, C> = { a ->
    val first = this(a)
    Tool.Composed(ToolId("kleisli"), first, other(a))
}

// Effect interpreter
interface EffectInterpreter<F> {
    suspend fun <A> interpret(effect: F): A
}

class IOInterpreter : EffectInterpreter<Effect<*>> {
    override suspend fun <A> interpret(effect: Effect<*>): A = when (effect) {
        is Effect.Pure<*> -> effect.value as A
        is Effect.ReadFile -> java.io.File(effect.path).readText() as A
        is Effect.WriteFile -> {
            java.io.File(effect.path).writeText(effect.content)
            Unit as A
        }
        is Effect.HttpGet -> {
            // Simplified - would use real HTTP client
            "HTTP GET ${effect.url} response" as A
        }
        is Effect.HttpPost -> {
            "HTTP POST ${effect.url} response" as A
        }
        is Effect.GetEnv -> System.getenv(effect.key) as A
        is Effect.Log -> {
            println(effect.message)
            Unit as A
        }
        is Effect.Prompt -> {
            print("${effect.message}: ")
            readlnOrNull() ?: "" as A
        }
        is Effect.Chain<*, *> -> {
            val first = interpret<Any?>(effect.first)
            interpret(effect.next(first))
        }
        is Effect.Parallel<*, *> -> {
            val results = effect.effects.map { interpret<Any?>(it) }
            effect.combine(results.toTypedArray().toSeries()) as A
        }
    }
}

// Smart constructors
fun readFile(path: String): Effect<String> = Effect.ReadFile(path)
fun writeFile(path: String, content: String): Effect<Unit> = Effect.WriteFile(path, content)
fun httpGet(url: String): Effect<String> = Effect.HttpGet(url)
fun httpPost(url: String, body: String): Effect<String> = Effect.HttpPost(url, body)
fun getEnv(key: String): Effect<String?> = Effect.GetEnv(key)
fun log(message: String): Effect<Unit> = Effect.Log(message)
fun prompt(message: String): Effect<String> = Effect.Prompt(message)

// Monadic operations
fun <A> pure(value: A): Effect<A> = Effect.Pure(value)

fun <A, B> Effect<A>.map(f: (A) -> B): Effect<B> = 
    Effect.Chain(this) { a -> Effect.Pure(f(a)) }

fun <A, B> Effect<A>.flatMap(f: (A) -> Effect<B>): Effect<B> = 
    Effect.Chain(this, f)

fun <A> sequence(effects: Indexed<Effect<A>>): Effect<Indexed<A>> = 
    Effect.Parallel(effects) { it }

// Example tools using the pure functional approach
object PureTools {
    val echo = ToolBuilder().pure<String, String>("echo") { it }
    
    val uppercase = ToolBuilder().pure<String, String>("uppercase") { it.uppercase() }
    
    val length = ToolBuilder().pure<String, Int>("length") { it.length }
    
    val reverse = ToolBuilder().pure<String, String>("reverse") { it.reversed() }
    
    val parseInt = ToolBuilder().effect<String, Int>("parseInt") { input ->
        input.toIntOrNull()?.let { Either.right(it) }
            ?: Either.left(ToolError.InvalidInput("Not a valid integer: $input"))
    }
    
    val readFileContent = ToolBuilder().effect<String, String>("readFile") { path ->
        try {
            Either.right(java.io.File(path).readText())
        } catch (e: Exception) {
            Either.left(ToolError.ExecutionFailed(ToolId("readFile"), e))
        }
    }
    
    // Composed tools
    val shout = echo andThen uppercase
    
    val wordCount = readFileContent andThen ToolBuilder().pure<String, Int>("wordCount") { 
        it.split(Regex("\\s+")).size 
    }
    
    val fileInfo = readFileContent alongside length
}

// Lens for immutable updates
data class Lens<S, A>(
    val get: (S) -> A,
    val set: (S, A) -> S
) {
    fun modify(f: (A) -> A): (S) -> S = { s ->
        set(s, f(get(s)))
    }
    
    infix fun <B> compose(other: Lens<A, B>): Lens<S, B> = Lens(
        get = { s -> other.get(get(s)) },
        set = { s, b -> set(s, other.set(get(s), b)) }
    )
}

// Prism for sum types
sealed class Prism<S, A> {
    abstract fun getOption(s: S): A?
    abstract fun reverseGet(a: A): S
    
    fun modify(f: (A) -> A): (S) -> S = { s ->
        getOption(s)?.let { reverseGet(f(it)) } ?: s
    }
}

// Iso for isomorphisms
data class Iso<S, A>(
    val get: (S) -> A,
    val reverseGet: (A) -> S
) {
    fun reverse(): Iso<A, S> = Iso(reverseGet, get)
}

// Extension functions for Either
fun <L, R, R2> Either<L, R>.map(f: (R) -> R2): Either<L, R2> = when (this) {
    is Either.Left -> this
    is Either.Right -> Either.right(f(value))
}

fun <L, R, R2> Either<L, R>.flatMap(f: (R) -> Either<L, R2>): Either<L, R2> = when (this) {
    is Either.Left -> this
    is Either.Right -> f(value)
}

fun <L, R> Either<L, R>.getOrElse(default: () -> R): R = when (this) {
    is Either.Left -> default()
    is Either.Right -> value
}

fun <L, R> Either<L, R>.orElse(other: () -> Either<L, R>): Either<L, R> = when (this) {
    is Either.Left -> other()
    is Either.Right -> this
}
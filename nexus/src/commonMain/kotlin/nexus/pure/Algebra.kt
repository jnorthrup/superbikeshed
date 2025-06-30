package nexus.pure

/**
 * Algebraic data types and type classes for pure functional tools
 */

// Type classes
interface Functor<F> {
    fun <A, B> map(fa: F, f: (A) -> B): F
}

interface Applicative<F> : Functor<F> {
    fun <A> pure(a: A): F
    fun <A, B> apply(fab: F, fa: F): F
}

interface Monad<F> : Applicative<F> {
    fun <A, B> flatMap(fa: F, f: (A) -> F): F
}

// Result type with type class instances
sealed class Result<out A> {
    data class Success<A>(val value: A) : Result<A>()
    data class Failure(val error: String) : Result<Nothing>()
    
    companion object {
        fun <A> success(value: A): Result<A> = Success(value)
        fun <A> failure(error: String): Result<A> = Failure(error)
        
        val functor = object : Functor<Result<*>> {
            override fun <A, B> map(fa: Result<*>, f: (A) -> B): Result<B> = 
                (fa as Result<A>).map(f)
        }
        
        val monad = object : Monad<Result<*>> {
            override fun <A> pure(a: A): Result<A> = success(a)
            
            override fun <A, B> map(fa: Result<*>, f: (A) -> B): Result<B> = 
                (fa as Result<A>).map(f)
            
            override fun <A, B> apply(fab: Result<*>, fa: Result<*>): Result<B> {
                val f = fab as Result<(A) -> B>
                val a = fa as Result<A>
                return f.flatMap { fn -> a.map(fn) }
            }
            
            override fun <A, B> flatMap(fa: Result<*>, f: (A) -> Result<*>): Result<B> = 
                (fa as Result<A>).flatMap { f(it) as Result<B> }
        }
    }
}

fun <A, B> Result<A>.map(f: (A) -> B): Result<B> = when (this) {
    is Result.Success -> Result.success(f(value))
    is Result.Failure -> this
}

fun <A, B> Result<A>.flatMap(f: (A) -> Result<B>): Result<B> = when (this) {
    is Result.Success -> f(value)
    is Result.Failure -> this
}

fun <A> Result<A>.getOrElse(default: () -> A): A = when (this) {
    is Result.Success -> value
    is Result.Failure -> default()
}

// Option type
sealed class Option<out A> {
    data object None : Option<Nothing>()
    data class Some<A>(val value: A) : Option<A>()
    
    companion object {
        fun <A> some(value: A): Option<A> = Some(value)
        fun <A> none(): Option<A> = None
        
        fun <A> fromNullable(value: A?): Option<A> = 
            if (value != null) some(value) else none()
    }
}

fun <A, B> Option<A>.map(f: (A) -> B): Option<B> = when (this) {
    is Option.None -> this
    is Option.Some -> Option.some(f(value))
}

fun <A, B> Option<A>.flatMap(f: (A) -> Option<B>): Option<B> = when (this) {
    is Option.None -> this
    is Option.Some -> f(value)
}

fun <A> Option<A>.getOrElse(default: () -> A): A = when (this) {
    is Option.None -> default()
    is Option.Some -> value
}

// Validated type for accumulating errors
sealed class Validated<out E, out A> {
    data class Invalid<E>(val errors: Indexed<E>) : Validated<E, Nothing>()
    data class Valid<A>(val value: A) : Validated<Nothing, A>()
    
    companion object {
        fun <E, A> valid(value: A): Validated<E, A> = Valid(value)
        fun <E, A> invalid(error: E): Validated<E, A> = Invalid(arrayOf(error).toSeries())
        fun <E, A> invalid(errors: Indexed<E>): Validated<E, A> = Invalid(errors)
    }
}

fun <E, A, B> Validated<E, A>.map(f: (A) -> B): Validated<E, B> = when (this) {
    is Validated.Invalid -> this
    is Validated.Valid -> Validated.valid(f(value))
}

fun <E, A, B> Validated<E, A>.andThen(f: (A) -> Validated<E, B>): Validated<E, B> = when (this) {
    is Validated.Invalid -> this
    is Validated.Valid -> f(value)
}

fun <E, A, B, C> Validated<E, A>.combine(
    other: Validated<E, B>,
    f: (A, B) -> C
): Validated<E, C> = when {
    this is Validated.Valid && other is Validated.Valid -> 
        Validated.valid(f(value, other.value))
    this is Validated.Invalid && other is Validated.Invalid -> 
        Validated.invalid((errors.toList() + other.errors.toList()).toTypedArray().toSeries())
    this is Validated.Invalid -> this
    other is Validated.Invalid -> other
    else -> error("Impossible case")
}

// State monad
data class State<S, A>(val run: (S) -> Join<S, A>) {
    fun <B> map(f: (A) -> B): State<S, B> = State { s ->
        val (newState, value) = run(s)
        newState j f(value)
    }
    
    fun <B> flatMap(f: (A) -> State<S, B>): State<S, B> = State { s ->
        val (newState, value) = run(s)
        f(value).run(newState)
    }
    
    companion object {
        fun <S, A> pure(value: A): State<S, A> = State { s -> s j value }
        
        fun <S> get(): State<S, S> = State { s -> s j s }
        
        fun <S> set(newState: S): State<S, Unit> = State { _ -> newState j Unit }
        
        fun <S> modify(f: (S) -> S): State<S, Unit> = State { s -> f(s) j Unit }
    }
}

// Reader monad
data class Reader<R, A>(val run: (R) -> A) {
    fun <B> map(f: (A) -> B): Reader<R, B> = Reader { r -> f(run(r)) }
    
    fun <B> flatMap(f: (A) -> Reader<R, B>): Reader<R, B> = Reader { r ->
        f(run(r)).run(r)
    }
    
    companion object {
        fun <R, A> pure(value: A): Reader<R, A> = Reader { _ -> value }
        
        fun <R> ask(): Reader<R, R> = Reader { r -> r }
        
        fun <R, A> local(f: (R) -> R, reader: Reader<R, A>): Reader<R, A> = 
            Reader { r -> reader.run(f(r)) }
    }
}

// Writer monad
data class Writer<W, A>(val run: Join<W, A>) {
    fun <B> map(f: (A) -> B): Writer<W, B> = Writer(run.a j f(run.b))
    
    fun <B> flatMap(f: (A) -> Writer<W, B>, combine: (W, W) -> W): Writer<W, B> {
        val (w1, a) = run
        val (w2, b) = f(a).run
        return Writer(combine(w1, w2) j b)
    }
    
    companion object {
        fun <W, A> pure(value: A, empty: W): Writer<W, A> = Writer(empty j value)
        
        fun <W> tell(log: W): Writer<W, Unit> = Writer(log j Unit)
    }
}

// Tool specification using algebraic types
data class ToolSpec<I, O>(
    val id: ToolId,
    val name: String,
    val description: String,
    val inputType: TypeInfo<I>,
    val outputType: TypeInfo<O>,
    val constraints: Indexed<Constraint>,
    val examples: Indexed<Example<I, O>>
)

// Type information
sealed class TypeInfo<T> {
    data class StringType(val pattern: String? = null) : TypeInfo<String>()
    data class IntType(val min: Int? = null, val max: Int? = null) : TypeInfo<Int>()
    data class DoubleType(val min: Double? = null, val max: Double? = null) : TypeInfo<Double>()
    data class BooleanType(val default: Boolean? = null) : TypeInfo<Boolean>()
    data class ListType<T>(val elementType: TypeInfo<T>) : TypeInfo<Indexed<T>>()
    data class RecordType(val fields: Map<String, TypeInfo<*>>) : TypeInfo<Map<String, Any?>>()
    data class UnionType<T>(val options: Indexed<TypeInfo<T>>) : TypeInfo<T>()
    data class CustomType<T>(val name: String, val validator: (Any?) -> Option<T>) : TypeInfo<T>()
}

// Constraints
sealed class Constraint {
    data class Range<T : Comparable<T>>(val min: T? = null, val max: T? = null) : Constraint()
    data class Length(val min: Int? = null, val max: Int? = null) : Constraint()
    data class Pattern(val regex: Regex) : Constraint()
    data class Custom(val name: String, val validator: (Any?) -> Boolean) : Constraint()
}

// Examples
data class Example<I, O>(
    val name: String,
    val input: I,
    val expectedOutput: O,
    val description: String? = null
)

// Tool implementation
sealed class ToolImpl<I, O> {
    data class Pure<I, O>(val f: (I) -> O) : ToolImpl<I, O>()
    data class Effectful<I, O>(val f: (I) -> Eff<O>) : ToolImpl<I, O>()
    data class Stateful<I, O, S>(
        val f: (I) -> State<S, O>,
        val initialState: S
    ) : ToolImpl<I, O>()
    data class Reader<I, O, R>(val f: (I) -> nexus.pure.Reader<R, O>) : ToolImpl<I, O>()
}

// Complete tool definition
data class ToolDefinition<I, O>(
    val spec: ToolSpec<I, O>,
    val impl: ToolImpl<I, O>
) {
    suspend fun execute(input: I, env: Environment = Environment.empty()): Result<O> {
        return try {
            when (impl) {
                is ToolImpl.Pure -> Result.success(impl.f(input))
                is ToolImpl.Effectful -> {
                    val result = impl.f(input).run(env.effectHandler)
                    Result.success(result)
                }
                is ToolImpl.Stateful -> {
                    val (_, output) = impl.f(input).run(impl.initialState)
                    Result.success(output)
                }
                is ToolImpl.Reader -> {
                    val output = impl.f(input).run(env.context)
                    Result.success(output)
                }
            }
        } catch (e: Exception) {
            Result.failure("Tool execution failed: ${e.message}")
        }
    }
}

// Environment for tool execution
data class Environment(
    val effectHandler: EffectHandler<Operation<*>> = IOHandler(),
    val context: Any? = null
) {
    companion object {
        fun empty(): Environment = Environment()
    }
}

// Tool registry using pure functional approach
class PureToolRegistry {
    private val tools = mutableMapOf<ToolId, ToolDefinition<*, *>>()
    
    fun <I, O> register(tool: ToolDefinition<I, O>) {
        tools[tool.spec.id] = tool
    }
    
    fun <I, O> get(id: ToolId): Option<ToolDefinition<I, O>> = 
        Option.fromNullable(tools[id] as? ToolDefinition<I, O>)
    
    fun search(query: String): Indexed<ToolDefinition<*, *>> = 
        tools.values
            .filter { 
                it.spec.name.contains(query, ignoreCase = true) ||
                it.spec.description.contains(query, ignoreCase = true)
            }
            .toTypedArray()
            .toSeries()
    
    fun getAllSpecs(): Indexed<ToolSpec<*, *>> = 
        tools.values.map { it.spec }.toTypedArray().toSeries()
}

// DSL for building tools
class ToolBuilder<I, O> {
    private lateinit var spec: ToolSpec<I, O>
    private lateinit var impl: ToolImpl<I, O>
    
    fun spec(block: ToolSpecBuilder<I, O>.() -> Unit) {
        spec = ToolSpecBuilder<I, O>().apply(block).build()
    }
    
    fun pure(f: (I) -> O) {
        impl = ToolImpl.Pure(f)
    }
    
    fun effect(f: (I) -> Eff<O>) {
        impl = ToolImpl.Effectful(f)
    }
    
    fun <S> stateful(initialState: S, f: (I) -> State<S, O>) {
        impl = ToolImpl.Stateful(f, initialState)
    }
    
    fun <R> reader(f: (I) -> Reader<R, O>) {
        impl = ToolImpl.Reader(f)
    }
    
    fun build(): ToolDefinition<I, O> = ToolDefinition(spec, impl)
}

class ToolSpecBuilder<I, O> {
    private lateinit var id: ToolId
    private lateinit var name: String
    private lateinit var description: String
    private lateinit var inputType: TypeInfo<I>
    private lateinit var outputType: TypeInfo<O>
    private var constraints: MutableList<Constraint> = mutableListOf()
    private var examples: MutableList<Example<I, O>> = mutableListOf()
    
    fun id(value: String) { id = ToolId(value) }
    fun name(value: String) { name = value }
    fun description(value: String) { description = value }
    fun inputType(value: TypeInfo<I>) { inputType = value }
    fun outputType(value: TypeInfo<O>) { outputType = value }
    fun constraint(value: Constraint) { constraints.add(value) }
    fun example(value: Example<I, O>) { examples.add(value) }
    
    fun build(): ToolSpec<I, O> = ToolSpec(
        id, name, description, inputType, outputType,
        constraints.toTypedArray().toSeries(),
        examples.toTypedArray().toSeries()
    )
}

// DSL entry point
inline fun <I, O> tool(block: ToolBuilder<I, O>.() -> Unit): ToolDefinition<I, O> =
    ToolBuilder<I, O>().apply(block).build()

// Example tools using the pure algebraic approach
object PureAlgebraicTools {
    val echo = tool<String, String> {
        spec {
            id("echo")
            name("Echo")
            description("Returns the input unchanged")
            inputType(TypeInfo.StringType())
            outputType(TypeInfo.StringType())
            example(Example("basic", "hello", "hello"))
        }
        pure { it }
    }
    
    val uppercase = tool<String, String> {
        spec {
            id("uppercase")
            name("Uppercase")
            description("Converts string to uppercase")
            inputType(TypeInfo.StringType())
            outputType(TypeInfo.StringType())
            example(Example("basic", "hello", "HELLO"))
        }
        pure { it.uppercase() }
    }
    
    val readFileContent = tool<String, String> {
        spec {
            id("read-file")
            name("Read File")
            description("Reads content from a file")
            inputType(TypeInfo.StringType())
            outputType(TypeInfo.StringType())
            constraint(Constraint.Pattern(Regex(".*\\.(txt|md|json)$")))
            example(Example("text-file", "example.txt", "file content"))
        }
        effect { path -> readFile(path) }
    }
    
    val counter = tool<Unit, Int> {
        spec {
            id("counter")
            name("Counter")
            description("Increments and returns a counter")
            inputType(TypeInfo.CustomType("unit") { Option.some(Unit) })
            outputType(TypeInfo.IntType(min = 0))
            example(Example("increment", Unit, 1))
        }
        stateful(0) { _ ->
            State.get<Int>().flatMap { count ->
                val newCount = count + 1
                State.set(newCount).map { newCount }
            }
        }
    }
}
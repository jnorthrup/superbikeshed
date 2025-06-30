package nexus.pure

import borg.trikeshed.lib.*

/**
 * Monadic composition operators and combinators for pure functional tools
 */

// Kleisli arrow composition
infix fun <A, B, C> ((A) -> Result<B>).andThen(g: (B) -> Result<C>): (A) -> Result<C> = { a ->
    this(a).flatMap(g)
}

infix fun <A, B, C> ((A) -> Option<B>).andThen(g: (B) -> Option<C>): (A) -> Option<C> = { a ->
    this(a).flatMap(g)
}

infix fun <A, B, C> ((A) -> Eff<B>).andThen(g: (B) -> Eff<C>): (A) -> Eff<C> = { a ->
    this(a).flatMap(g)
}

// Tool composition using monadic operators
infix fun <A, B, C> ToolDefinition<A, B>.compose(other: ToolDefinition<B, C>): ToolDefinition<A, C> {
    val newSpec = ToolSpec(
        id = ToolId("${spec.id.value}-compose-${other.spec.id.value}"),
        name = "${spec.name} >> ${other.spec.name}",
        description = "Composition of ${spec.name} and ${other.spec.name}",
        inputType = spec.inputType,
        outputType = other.spec.outputType,
        constraints = (spec.constraints.toList() + other.spec.constraints.toList()).toTypedArray().toSeries(),
        examples = emptyArray<Example<A, C>>().toSeries()
    )
    
    val newImpl = when {
        impl is ToolImpl.Pure && other.impl is ToolImpl.Pure -> 
            ToolImpl.Pure<A, C> { a -> other.impl.f(impl.f(a)) }
        
        impl is ToolImpl.Pure && other.impl is ToolImpl.Effectful -> 
            ToolImpl.Effectful<A, C> { a -> other.impl.f(impl.f(a)) }
        
        impl is ToolImpl.Effectful && other.impl is ToolImpl.Pure -> 
            ToolImpl.Effectful<A, C> { a -> impl.f(a).map(other.impl.f) }
        
        impl is ToolImpl.Effectful && other.impl is ToolImpl.Effectful -> 
            ToolImpl.Effectful<A, C> { a -> impl.f(a).flatMap(other.impl.f) }
        
        else -> ToolImpl.Effectful<A, C> { a ->
            // Fallback to effectful composition
            executeAsEffect(a, Environment.empty()).flatMap { b ->
                other.executeAsEffect(b, Environment.empty())
            }
        }
    }
    
    return ToolDefinition(newSpec, newImpl)
}

// Helper to execute any tool as an effect
suspend fun <I, O> ToolDefinition<I, O>.executeAsEffect(input: I, env: Environment): Eff<O> = when (impl) {
    is ToolImpl.Pure -> ret(impl.f(input))
    is ToolImpl.Effectful -> impl.f(input)
    is ToolImpl.Stateful -> {
        val (_, result) = impl.f(input).run(impl.initialState)
        ret(result)
    }
    is ToolImpl.Reader -> ret(impl.f(input).run(env.context))
}

// Parallel composition
infix fun <A, B, C> ToolDefinition<A, B>.alongside(other: ToolDefinition<A, C>): ToolDefinition<A, Join<B, C>> {
    val newSpec = ToolSpec(
        id = ToolId("${spec.id.value}-alongside-${other.spec.id.value}"),
        name = "${spec.name} & ${other.spec.name}",
        description = "Parallel execution of ${spec.name} and ${other.spec.name}",
        inputType = spec.inputType,
        outputType = TypeInfo.CustomType("join") { _ -> Option.some(Unit j Unit) },
        constraints = (spec.constraints.toList() + other.spec.constraints.toList()).toTypedArray().toSeries(),
        examples = emptyArray<Example<A, Join<B, C>>>().toSeries()
    )
    
    val newImpl = ToolImpl.Effectful<A, Join<B, C>> { a ->
        val leftEff = executeAsEffect(a, Environment.empty())
        val rightEff = other.executeAsEffect(a, Environment.empty())
        
        leftEff.flatMap { b ->
            rightEff.map { c -> b j c }
        }
    }
    
    return ToolDefinition(newSpec, newImpl)
}

// Choice operator - first successful tool wins
infix fun <A, B> ToolDefinition<A, B>.or(other: ToolDefinition<A, B>): ToolDefinition<A, B> {
    val newSpec = ToolSpec(
        id = ToolId("${spec.id.value}-or-${other.spec.id.value}"),
        name = "${spec.name} | ${other.spec.name}",
        description = "Choice between ${spec.name} and ${other.spec.name}",
        inputType = spec.inputType,
        outputType = spec.outputType,
        constraints = spec.constraints, // Use constraints from first tool
        examples = (spec.examples.toList() + other.spec.examples.toList()).toTypedArray().toSeries()
    )
    
    val newImpl = ToolImpl.Effectful<A, B> { a ->
        executeAsEffect(a, Environment.empty()).recover { _ ->
            other.executeAsEffect(a, Environment.empty())
        }
    }
    
    return ToolDefinition(newSpec, newImpl)
}

// Conditional execution
fun <A, B> ToolDefinition<A, Boolean>.ifThen(
    thenTool: ToolDefinition<A, B>,
    elseTool: ToolDefinition<A, B>
): ToolDefinition<A, B> {
    val newSpec = ToolSpec(
        id = ToolId("${spec.id.value}-if-then-else"),
        name = "if ${spec.name} then ${thenTool.spec.name} else ${elseTool.spec.name}",
        description = "Conditional execution based on ${spec.name}",
        inputType = spec.inputType,
        outputType = thenTool.spec.outputType,
        constraints = (spec.constraints.toList() + thenTool.spec.constraints.toList() + elseTool.spec.constraints.toList()).toTypedArray().toSeries(),
        examples = emptyArray<Example<A, B>>().toSeries()
    )
    
    val newImpl = ToolImpl.Effectful<A, B> { a ->
        executeAsEffect(a, Environment.empty()).flatMap { condition ->
            if (condition) {
                thenTool.executeAsEffect(a, Environment.empty())
            } else {
                elseTool.executeAsEffect(a, Environment.empty())
            }
        }
    }
    
    return ToolDefinition(newSpec, newImpl)
}

// Loop/iteration
fun <A, B> ToolDefinition<Indexed<A>, Indexed<B>>.foreach(itemTool: ToolDefinition<A, B>): ToolDefinition<Indexed<A>, Indexed<B>> {
    val newSpec = ToolSpec(
        id = ToolId("foreach-${itemTool.spec.id.value}"),
        name = "foreach ${itemTool.spec.name}",
        description = "Apply ${itemTool.spec.name} to each item",
        inputType = TypeInfo.ListType(itemTool.spec.inputType),
        outputType = TypeInfo.ListType(itemTool.spec.outputType),
        constraints = itemTool.spec.constraints,
        examples = emptyArray<Example<Indexed<A>, Indexed<B>>>().toSeries()
    )
    
    val newImpl = ToolImpl.Effectful<Indexed<A>, Indexed<B>> { items ->
        traverse(items) { item ->
            itemTool.executeAsEffect(item, Environment.empty())
        }
    }
    
    return ToolDefinition(newSpec, newImpl)
}

// Retry combinator
fun <A, B> ToolDefinition<A, B>.retry(maxAttempts: Int = 3): ToolDefinition<A, B> {
    val newSpec = spec.copy(
        id = ToolId("${spec.id.value}-retry"),
        name = "${spec.name} (retry)",
        description = "${spec.description} with retry up to $maxAttempts times"
    )
    
    val newImpl = ToolImpl.Effectful<A, B> { a ->
        fun attempt(attemptsLeft: Int): Eff<B> = 
            if (attemptsLeft <= 0) {
                fail("Max retry attempts exceeded")
            } else {
                executeAsEffect(a, Environment.empty()).recover { _ ->
                    attempt(attemptsLeft - 1)
                }
            }
        attempt(maxAttempts)
    }
    
    return ToolDefinition(newSpec, newImpl)
}

// Timeout combinator
fun <A, B> ToolDefinition<A, B>.timeout(timeoutMs: Long): ToolDefinition<A, B> {
    val newSpec = spec.copy(
        id = ToolId("${spec.id.value}-timeout"),
        name = "${spec.name} (timeout)",
        description = "${spec.description} with ${timeoutMs}ms timeout"
    )
    
    val newImpl = ToolImpl.Effectful<A, B> { a ->
        // Simplified timeout - in real implementation would use proper async timeout
        executeAsEffect(a, Environment.empty())
    }
    
    return ToolDefinition(newSpec, newImpl)
}

// Cache combinator
fun <A, B> ToolDefinition<A, B>.cache(ttlMs: Long? = null): ToolDefinition<A, B> {
    val cache = mutableMapOf<A, Join<Long, B>>()
    
    val newSpec = spec.copy(
        id = ToolId("${spec.id.value}-cached"),
        name = "${spec.name} (cached)",
        description = "${spec.description} with caching"
    )
    
    val newImpl = ToolImpl.Effectful<A, B> { a ->
        val now = System.currentTimeMillis()
        val cached = cache[a]
        
        if (cached != null && (ttlMs == null || now - cached.a < ttlMs)) {
            ret(cached.b)
        } else {
            executeAsEffect(a, Environment.empty()).map { result ->
                cache[a] = now j result
                result
            }
        }
    }
    
    return ToolDefinition(newSpec, newImpl)
}

// Lens-based data transformation
fun <A, B, F> ToolDefinition<A, B>.focus(lens: Lens<A, F>): ToolDefinition<F, B> {
    val newSpec = ToolSpec(
        id = ToolId("${spec.id.value}-focused"),
        name = "${spec.name} (focused)",
        description = "${spec.description} focused on a field",
        inputType = TypeInfo.CustomType("focused") { Option.some(it) },
        outputType = spec.outputType,
        constraints = spec.constraints,
        examples = emptyArray<Example<F, B>>().toSeries()
    )
    
    val newImpl = when (impl) {
        is ToolImpl.Pure -> ToolImpl.Pure<F, B> { f ->
            // This is conceptually wrong - we can't create an A from F without the full context
            // In practice, you'd need the full object to focus on
            throw UnsupportedOperationException("Cannot focus pure tool without full context")
        }
        else -> ToolImpl.Effectful<F, B> { f ->
            // Same issue - need full context
            fail("Cannot focus tool without full context")
        }
    }
    
    return ToolDefinition(newSpec, newImpl)
}

// Validation combinator
fun <A, B> ToolDefinition<A, B>.validate(predicate: (A) -> Boolean, errorMsg: String = "Validation failed"): ToolDefinition<A, B> {
    val newSpec = spec.copy(
        id = ToolId("${spec.id.value}-validated"),
        name = "${spec.name} (validated)",
        description = "${spec.description} with input validation"
    )
    
    val newImpl = ToolImpl.Effectful<A, B> { a ->
        if (predicate(a)) {
            executeAsEffect(a, Environment.empty())
        } else {
            fail(errorMsg)
        }
    }
    
    return ToolDefinition(newSpec, newImpl)
}

// Transformation combinator
fun <A, B, C> ToolDefinition<A, B>.contramap(f: (C) -> A): ToolDefinition<C, B> {
    val newSpec = ToolSpec(
        id = ToolId("${spec.id.value}-contramapped"),
        name = "${spec.name} (contramapped)",
        description = "${spec.description} with input transformation",
        inputType = TypeInfo.CustomType("transformed") { Option.some(it) },
        outputType = spec.outputType,
        constraints = spec.constraints,
        examples = emptyArray<Example<C, B>>().toSeries()
    )
    
    val newImpl = when (impl) {
        is ToolImpl.Pure -> ToolImpl.Pure<C, B> { c -> impl.f(f(c)) }
        is ToolImpl.Effectful -> ToolImpl.Effectful<C, B> { c -> impl.f(f(c)) }
        is ToolImpl.Stateful -> ToolImpl.Stateful<C, B, Any?> ({ c -> impl.f(f(c)) }, impl.initialState)
        is ToolImpl.Reader -> ToolImpl.Reader<C, B, Any?> { c -> impl.f(f(c)) }
    }
    
    return ToolDefinition(newSpec, newImpl)
}

fun <A, B, C> ToolDefinition<A, B>.map(f: (B) -> C): ToolDefinition<A, C> {
    val newSpec = ToolSpec(
        id = ToolId("${spec.id.value}-mapped"),
        name = "${spec.name} (mapped)",
        description = "${spec.description} with output transformation",
        inputType = spec.inputType,
        outputType = TypeInfo.CustomType("transformed") { Option.some(it) },
        constraints = spec.constraints,
        examples = emptyArray<Example<A, C>>().toSeries()
    )
    
    val newImpl = when (impl) {
        is ToolImpl.Pure -> ToolImpl.Pure<A, C> { a -> f(impl.f(a)) }
        is ToolImpl.Effectful -> ToolImpl.Effectful<A, C> { a -> impl.f(a).map(f) }
        is ToolImpl.Stateful -> ToolImpl.Stateful<A, C, Any?> ({ a -> impl.f(a).map(f) }, impl.initialState)
        is ToolImpl.Reader -> ToolImpl.Reader<A, C, Any?> { a -> impl.f(a).map(f) }
    }
    
    return ToolDefinition(newSpec, newImpl)
}

// Pipeline builder
class ToolPipeline<A> {
    private var currentTool: ToolDefinition<A, *>? = null
    
    fun <B> start(tool: ToolDefinition<A, B>): ToolPipeline<B> {
        val pipeline = ToolPipeline<B>()
        pipeline.currentTool = tool
        return pipeline
    }
    
    fun <B> then(tool: ToolDefinition<*, B>): ToolPipeline<B> {
        val pipeline = ToolPipeline<B>()
        pipeline.currentTool = currentTool?.let { current ->
            @Suppress("UNCHECKED_CAST")
            (current as ToolDefinition<A, Any?>).compose(tool as ToolDefinition<Any?, B>)
        }
        return pipeline
    }
    
    fun <B> build(): ToolDefinition<A, B>? {
        @Suppress("UNCHECKED_CAST")
        return currentTool as? ToolDefinition<A, B>
    }
}

// DSL for building pipelines
fun <A> pipeline(): ToolPipeline<A> = ToolPipeline()

// Example of monadic composition
object MonadicExamples {
    // Basic composition
    val processText = PureAlgebraicTools.echo compose PureAlgebraicTools.uppercase
    
    // Parallel execution
    val analyzeFile = PureAlgebraicTools.readFileContent alongside 
                      (PureAlgebraicTools.readFileContent map { it.length })
    
    // Choice between tools
    val flexibleRead = PureAlgebraicTools.readFileContent or PureAlgebraicTools.echo
    
    // Retry with validation
    val robustRead = PureAlgebraicTools.readFileContent
        .validate({ it.isNotBlank() }, "Path cannot be blank")
        .retry(3)
        .cache(30000) // 30 second cache
    
    // Pipeline using DSL
    val textProcessingPipeline = pipeline<String>()
        .start(PureAlgebraicTools.echo)
        .then(PureAlgebraicTools.uppercase)
        .build()
}
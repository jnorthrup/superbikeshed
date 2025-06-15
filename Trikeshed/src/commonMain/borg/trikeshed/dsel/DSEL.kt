package borg.trikeshed.dsel

import kotlinx.serialization.Serializable

@Serializable
sealed class DSELExpression {
    abstract fun evaluate(context: DSELContext): Any
}

@Serializable
data class DSELContext(
    val variables: Map<String, Any> = emptyMap(),
    val functions: Map<String, (List<Any>) -> Any> = emptyMap()
)

@Serializable
data class LiteralExpression(val value: Any) : DSELExpression() {
    override fun evaluate(context: DSELContext): Any = value
}

@Serializable
data class VariableExpression(val name: String) : DSELExpression() {
    override fun evaluate(context: DSELContext): Any =
        context.variables[name] ?: throw IllegalStateException("Variable $name not found")
}

@Serializable
data class FunctionCallExpression(
    val functionName: String,
    val arguments: List<DSELExpression>
) : DSELExpression() {
    override fun evaluate(context: DSELContext): Any {
        val function = context.functions[functionName] ?: throw IllegalStateException("Function $functionName not found")
        val evaluatedArgs = arguments.map { it.evaluate(context) }
        return function(evaluatedArgs)
    }
}

@Serializable
data class BinaryExpression(
    val left: DSELExpression,
    val operator: String,
    val right: DSELExpression
) : DSELExpression() {
    override fun evaluate(context: DSELContext): Any {
        val leftValue = left.evaluate(context)
        val rightValue = right.evaluate(context)
        return when (operator) {
            "+" -> (leftValue as Number).toDouble() + (rightValue as Number).toDouble()
            "-" -> (leftValue as Number).toDouble() - (rightValue as Number).toDouble()
            "*" -> (leftValue as Number).toDouble() * (rightValue as Number).toDouble()
            "/" -> (leftValue as Number).toDouble() / (rightValue as Number).toDouble()
            "==" -> leftValue == rightValue
            "!=" -> leftValue != rightValue
            ">" -> (leftValue as Comparable<Any>) > rightValue
            "<" -> (leftValue as Comparable<Any>) < rightValue
            ">=" -> (leftValue as Comparable<Any>) >= rightValue
            "<=" -> (leftValue as Comparable<Any>) <= rightValue
            else -> throw IllegalStateException("Unknown operator: $operator")
        }
    }
}

@Serializable
data class IfExpression(
    val condition: DSELExpression,
    val thenBranch: DSELExpression,
    val elseBranch: DSELExpression
) : DSELExpression() {
    override fun evaluate(context: DSELContext): Any {
        val conditionValue = condition.evaluate(context) as Boolean
        return if (conditionValue) {
            thenBranch.evaluate(context)
        } else {
            elseBranch.evaluate(context)
        }
    }
}

@Serializable
data class LetExpression(
    val bindings: List<Pair<String, DSELExpression>>,
    val body: DSELExpression
) : DSELExpression() {
    override fun evaluate(context: DSELContext): Any {
        val newVariables = bindings.associate { (name, expr) ->
            name to expr.evaluate(context)
        }
        val newContext = context.copy(variables = context.variables + newVariables)
        return body.evaluate(newContext)
    }
}

class DSELInterpreter {
    private val context = DSELContext(
        functions = mapOf(
            "print" to { args -> println(args.first()); args.first() },
            "sum" to { args -> args.filterIsInstance<Number>().sumOf { it.toDouble() } },
            "max" to { args -> args.filterIsInstance<Number>().maxOf { it.toDouble() } },
            "min" to { args -> args.filterIsInstance<Number>().minOf { it.toDouble() } }
        )
    )

    fun interpret(expression: DSELExpression): Any {
        return expression.evaluate(context)
    }

    fun addFunction(name: String, function: (List<Any>) -> Any) {
        context.functions[name] = function
    }

    fun setVariable(name: String, value: Any) {
        context.variables[name] = value
    }
} 
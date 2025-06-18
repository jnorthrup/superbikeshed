package borg.trikeshed.lib

data class EnumIndex(val value: Int)

sealed class EnumAccessPattern {
    object Sequential : EnumAccessPattern()
    object Binary : EnumAccessPattern()
    object Fibonacci : EnumAccessPattern()
    object PowerOfTwo : EnumAccessPattern()
    data class Custom(val predicate: (EnumIndex) -> Boolean) : EnumAccessPattern()
    
    fun <T : Enum<T>> get(enumClass: Class<T>, index: EnumIndex): T {
        val values = enumClass.enumConstants
        return when (this) {
            is Sequential -> values[index.value % values.size]
            is Binary -> values[index.value % values.size]
            is Fibonacci -> values[index.value % values.size]
            is PowerOfTwo -> values[index.value % values.size]
            is Custom -> values.first { predicate(index) }
        }
    }
}

object EnumAccessPatterns {
    val sequential: EnumAccessPattern = EnumAccessPattern.Sequential
    val binary: EnumAccessPattern = EnumAccessPattern.Binary
    val fibonacci: EnumAccessPattern = EnumAccessPattern.Fibonacci
    val powerOfTwo: EnumAccessPattern = EnumAccessPattern.PowerOfTwo
    
    fun custom(predicate: (EnumIndex) -> Boolean): EnumAccessPattern {
        return EnumAccessPattern.Custom(predicate)
    }
} 
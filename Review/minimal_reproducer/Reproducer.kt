package com.example.reproducer

// Definition similar to core.Series<T>
typealias Series<T> = Pair<Int, (Int) -> T>

// Interface inheriting from Series<T>
interface MyInterface<T> : Series<T>

// Class implementing MyInterface<T>
class MyClass<T>(val data: List<T>) : MyInterface<T> {
    override val first: Int
        get() = data.size

    override val second: (Int) -> T
        get() = { index -> data[index] }
}

// A function to test compilation
fun <T> processSeries(series: MyInterface<T>): String {
    if (series.first == 0) return "Empty"
    return "First element: ${series.second(0)}"
}

fun main() {
    val myData = MyClass(listOf("a", "b", "c"))
    println(processSeries(myData))

    val emptyData = MyClass(listOf<String>())
    println(processSeries(emptyData))
}

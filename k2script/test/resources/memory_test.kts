import java.lang.management.ManagementFactory

println("Hello from Kotlin with 5g of heap memory in server mode!")

val bean = ManagementFactory.getRuntimeMXBean()
val aList = bean.inputArguments

for (i in aList.indices) {
    println(aList[i])
}
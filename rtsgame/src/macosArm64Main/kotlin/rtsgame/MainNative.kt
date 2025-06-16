package rtsgame

actual fun platformMain() {
    println("Starting RTS Game on Native macOS...")
    
    val engine = startGame()
    
    repeat(5) { i ->
        val state = engine.tick()
        println("Tick ${state.tick.value}: ${state.entities.size} entities")
    }
    
    println("Native RTS Game simulation complete")
}

fun main() {
    platformMain()
    demonstrateGame()
}
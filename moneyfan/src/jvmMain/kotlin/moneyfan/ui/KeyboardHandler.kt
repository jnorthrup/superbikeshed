package moneyfan.ui

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * JVM keyboard handler for MDI interface with function key hover/hold detection
 */
class KeyboardHandler(internal val mdiInterface: MDIInterface) {
    internal val keyPressedStates = mutableMapOf<Int, Boolean>()
    internal val keyHoldJobs = mutableMapOf<Int, Job>()
    internal val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    fun startKeyboardMonitoring() {
        println("Keyboard monitoring started - Use function keys F1-F12")
        println("Hold function key for 500ms to activate")
        
        // Simulate keyboard events for demo
        scope.launch {
            simulateKeyboardEvents()
        }
    }
    
    internal suspend fun simulateKeyboardEvents() {
        println("\nSimulating function key events...")
        
        // F1 hover and hold
        simulateKeyHover(FunctionKey.F1, true)
        delay(100)
        simulateKeyHover(FunctionKey.F1, false) 
        delay(200)
        
        // F1 hold
        simulateKeyPress(FunctionKey.F1)
        delay(600) // Hold for 600ms
        simulateKeyRelease(FunctionKey.F1)
        delay(500)
        
        // F5 to show trace viewer
        simulateKeyPress(FunctionKey.F5)
        delay(600)
        simulateKeyRelease(FunctionKey.F5)
        delay(500)
        
        // F2 for portfolio
        simulateKeyPress(FunctionKey.F2)
        delay(600)
        simulateKeyRelease(FunctionKey.F2)
        delay(500)
        
        // F9 to tile windows
        simulateKeyPress(FunctionKey.F9)
        delay(600)
        simulateKeyRelease(FunctionKey.F9)
        delay(500)
        
        // F7 for BTC chart
        simulateKeyPress(FunctionKey.F7)
        delay(600)
        simulateKeyRelease(FunctionKey.F7)
        delay(500)
        
        // F11 to clear trace
        simulateKeyPress(FunctionKey.F11)
        delay(600)
        simulateKeyRelease(FunctionKey.F11)
        delay(1000)
        
        println("\nKeyboard simulation completed")
    }
    
    internal fun simulateKeyHover(key: FunctionKey, isHovering: Boolean) {
        mdiInterface.onFunctionKeyHover(key, isHovering)
    }
    
    internal fun simulateKeyPress(key: FunctionKey) {
        val keyCode = key.keyCode
        keyPressedStates[keyCode] = true
        
        // Start hold detection
        val holdJob = scope.launch {
            delay(500) // 500ms hold threshold
            if (keyPressedStates[keyCode] == true) {
                mdiInterface.onFunctionKeyHold(key)
            }
        }
        keyHoldJobs[keyCode] = holdJob
    }
    
    internal fun simulateKeyRelease(key: FunctionKey) {
        val keyCode = key.keyCode
        keyPressedStates[keyCode] = false
        
        // Cancel hold detection
        keyHoldJobs[keyCode]?.cancel()
        keyHoldJobs.remove(keyCode)
    }
    
    fun stop() {
        scope.cancel()
    }
}

/**
 * Demo runner for MDI interface with keyboard handling
 */
suspend fun runMDIDemo() {
    val mdiInterface = MDIInterface()
    val keyboardHandler = KeyboardHandler(mdiInterface)
    
    println("🖥️  MDI Interface Demo with Function Key Trace Logging")
    println("=" .repeat(60))
    
    // Initial render
    mdiInterface.renderInterface()
    
    // Start keyboard monitoring
    keyboardHandler.startKeyboardMonitoring()
    
    // Let it run for a bit
    delay(8000)
    
    // Final render to show results
    println("\n🏁 Final MDI State:")
    mdiInterface.renderInterface()
    
    keyboardHandler.stop()
}
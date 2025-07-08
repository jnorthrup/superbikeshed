package com.superbikeshed.reactor

import com.superbikeshed.common.*
import kotlinx.coroutines.*

interface Reactor : TrikeshedComponent, Lifecycle {
    fun submit(task: suspend () -> Unit)
    fun shutdown()
}

class TrikeshedReactor : Reactor {
    internal var running = false
    internal val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    override fun getName(): String = "TrikeshedReactor"
    override fun getVersion(): String = "1.0.0"
    
    override fun start() {
        running = true
    }
    
    override fun stop() {
        running = false
        shutdown()
    }
    
    override fun isRunning(): Boolean = running
    
    override fun submit(task: suspend () -> Unit) {
        scope.launch {
            task()
        }
    }
    
    override fun shutdown() {
        scope.cancel()
    }
} 
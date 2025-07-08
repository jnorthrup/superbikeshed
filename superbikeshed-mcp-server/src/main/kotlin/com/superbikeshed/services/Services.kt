package com.superbikeshed.services

import com.superbikeshed.common.*

interface ServiceRegistry : TrikeshedComponent, Lifecycle {
    fun register(name: String, service: Any)
    fun get(name: String): Any?
    fun unregister(name: String)
    fun listServices(): List<String>
}

class TrikeshedServiceRegistry : ServiceRegistry {
    internal val services = mutableMapOf<String, Any>()
    internal var running = false
    
    override fun getName(): String = "TrikeshedServiceRegistry"
    override fun getVersion(): String = "1.0.0"
    
    override fun start() {
        running = true
    }
    
    override fun stop() {
        running = false
        services.clear()
    }
    
    override fun isRunning(): Boolean = running
    
    override fun register(name: String, service: Any) {
        services[name] = service
    }
    
    override fun get(name: String): Any? {
        return services[name]
    }
    
    override fun unregister(name: String) {
        services.remove(name)
    }
    
    override fun listServices(): List<String> {
        return services.keys.toList()
    }
} 
package borg.trikeshed.launcher

import kotlinx.coroutines.*
import javax.naming.*
import java.util.Hashtable

/**
 * Demonstrates the full integration of:
 * - Darwin liburing (kqueue facade) 
 * - Java Naming Services (JNDI)
 * - Platform Launcher
 * - CCEK orchestration
 */
object PlatformIntegrationDemo {
    
    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        println("🚀 Platform Integration Demo")
        println("============================")
        
        // 1. Initialize platform launcher with io_uring naming service
        val launcher = PlatformLauncher()
        launcher.initialize(listOf(
            "-Xmx2g",
            "-XX:+UseG1GC"
        ))
        
        println("\n📦 Registered Platform Services:")
        listPlatformServices(launcher)
        
        // 2. Register microservices
        registerMicroservices(launcher)
        
        // 3. Demonstrate service discovery
        demonstrateServiceDiscovery(launcher)
        
        // 4. Show async operations with io_uring
        demonstrateAsyncOperations(launcher)
        
        // 5. Integration with WASM modules
        demonstrateWASMIntegration(launcher)
        
        // Cleanup
        launcher.shutdown()
    }
    
    private fun listPlatformServices(launcher: PlatformLauncher) {
        val ctx = launcher.namingContext ?: return
        
        try {
            val platformServices = ctx.list("platform")
            while (platformServices.hasMore()) {
                val service = platformServices.next()
                println("  - ${service.name}: ${service.className}")
            }
        } catch (e: Exception) {
            println("  Error listing services: ${e.message}")
        }
    }
    
    private fun registerMicroservices(launcher: PlatformLauncher) {
        println("\n🎯 Registering Microservices:")
        
        // Auth service
        launcher.registerService("auth", object : AuthService {
            override suspend fun authenticate(token: String): Boolean {
                delay(10) // Simulate async work
                return token.isNotEmpty()
            }
            
            override fun toString() = "AuthService[io_uring]"
        })
        
        // User service
        launcher.registerService("users", object : UserService {
            private val users = mutableMapOf<String, User>()
            
            override suspend fun getUser(id: String): User? {
                delay(5) // Simulate DB lookup
                return users[id]
            }
            
            override suspend fun createUser(user: User) {
                delay(10) // Simulate DB write
                users[user.id] = user
            }
            
            override fun toString() = "UserService[io_uring]"
        })
        
        // Order service
        launcher.registerService("orders", object : OrderService {
            override suspend fun createOrder(userId: String, items: List<String>): String {
                delay(20) // Simulate order processing
                return "ORDER-${System.currentTimeMillis()}"
            }
            
            override fun toString() = "OrderService[io_uring]"
        })
        
        println("  ✅ Registered auth, users, orders services")
    }
    
    private suspend fun demonstrateServiceDiscovery(launcher: PlatformLauncher) {
        println("\n🔍 Service Discovery Demo:")
        
        // Lookup services
        val authService = launcher.lookupService<AuthService>("auth")
        val userService = launcher.lookupService<UserService>("users")
        val orderService = launcher.lookupService<OrderService>("orders")
        
        if (authService != null && userService != null && orderService != null) {
            // Create a user
            val user = User("user-1", "John Doe", "john@example.com")
            userService.createUser(user)
            println("  ✅ Created user: ${user.name}")
            
            // Authenticate
            val authenticated = authService.authenticate("test-token")
            println("  ✅ Authentication: $authenticated")
            
            // Create order
            val orderId = orderService.createOrder(user.id, listOf("item1", "item2"))
            println("  ✅ Created order: $orderId")
        }
    }
    
    private suspend fun demonstrateAsyncOperations(launcher: PlatformLauncher) {
        println("\n⚡ Async Operations with io_uring:")
        
        val ctx = launcher.namingContext as? UringContext ?: return
        
        // Perform multiple async operations
        val jobs = List(10) { index ->
            GlobalScope.async {
                val key = "async/item-$index"
                val value = "Value-$index"
                
                // Bind asynchronously
                ctx.bind(key, value)
                
                // Lookup asynchronously
                val retrieved = ctx.lookup(key)
                
                println("  📝 Async operation $index: stored=$value, retrieved=$retrieved")
                retrieved
            }
        }
        
        // Wait for all operations
        jobs.awaitAll()
        println("  ✅ All async operations completed")
    }
    
    private suspend fun demonstrateWASMIntegration(launcher: PlatformLauncher) {
        println("\n🎮 WASM Integration Demo:")
        
        // Mock WASM module that uses naming service
        val wasmModule = object {
            suspend fun lookupService(name: String): Any? {
                return launcher.lookupService<Any>(name)
            }
            
            suspend fun registerService(name: String, service: Any) {
                launcher.registerService(name, service)
            }
        }
        
        // Register a service from "WASM"
        wasmModule.registerService("wasm-compute", object {
            override fun toString() = "WASMComputeService[io_uring]"
        })
        
        // Lookup from "WASM"
        val computeService = wasmModule.lookupService("wasm-compute")
        println("  ✅ WASM registered service: $computeService")
        
        // List all modules
        try {
            val modules = launcher.namingContext?.list("modules")
            if (modules != null && modules.hasMore()) {
                println("  📦 Registered WASM modules:")
                while (modules.hasMore()) {
                    val module = modules.next()
                    println("    - ${module.name}")
                }
            }
        } catch (e: Exception) {
            println("  ℹ️ No WASM modules loaded")
        }
    }
}

// Service interfaces
interface AuthService {
    suspend fun authenticate(token: String): Boolean
}

interface UserService {
    suspend fun getUser(id: String): User?
    suspend fun createUser(user: User)
}

interface OrderService {
    suspend fun createOrder(userId: String, items: List<String>): String
}

// Data classes
data class User(
    val id: String,
    val name: String,
    val email: String
)
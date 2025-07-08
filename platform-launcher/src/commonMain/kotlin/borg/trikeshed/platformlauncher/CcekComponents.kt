package borg.trikeshed.platformlauncher

import kotlin.coroutines.CoroutineContext

// CCEK Elements for each component
data class K2ScriptContext(val sandboxPath: String) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<K2ScriptContext>
    override val key: CoroutineContext.Key<K2ScriptContext> = Key
}

data class NexusContext(val orchestratorId: String) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<NexusContext>
    override val key: CoroutineContext.Key<NexusContext> = Key
}

data class CouchDBContext(val dbUrl: String) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<CouchDBContext>
    override val key: CoroutineContext.Key<CouchDBContext> = Key
}

data class BFDPlatformContext(val platformId: String) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<BFDPlatformContext>
    override val key: CoroutineContext.Key<BFDPlatformContext> = Key
}

// A "Coherent and Connected Midpoint Component"
// This function will attempt to use the CCEK elements from the CoroutineContext
fun TaskDispatcher(context: CoroutineContext): String {
    val k2scriptCtx = context[K2ScriptContext]
    val nexusCtx = context[NexusContext]
    val couchDbCtx = context[CouchDBContext]
    val bfdPlatformCtx = context[BFDPlatformContext]

    // This is the minimal implementation to make the test fail initially
    // It will be refined to pass the test in the next step
    return "Task dispatched with: " +
            "K2Script: ${k2scriptCtx?.sandboxPath}, " +
            "Nexus: ${nexusCtx?.orchestratorId}, " +
            "CouchDB: ${couchDbCtx?.dbUrl}, " +
            "BFD Platform: ${bfdPlatformCtx?.platformId}"
}
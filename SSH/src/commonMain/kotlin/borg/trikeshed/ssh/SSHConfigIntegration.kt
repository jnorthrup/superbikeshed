package borg.trikeshed.ssh

import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers

/**
 * SSH Config Integration with DSL
 * 
 * Seamlessly integrates ~/.ssh/config reading into the SSH DSL
 */

// Extended SSH builder with config support
class SSHConfigAwareBuilder {
    internal val builder = SSHBuilder()
    internal var configReader: SSHConfigReader? = null
    internal var keyReader: SSHKeyReader? = null
    internal var configFile: SSHConfigFile? = null
    
    fun withConfigReader(reader: SSHConfigReader) {
        configReader = reader
    }
    
    fun withKeyReader(reader: SSHKeyReader) {
        keyReader = reader
    }
    
    suspend fun fromConfig(hostname: String) = coroutineScope {
        val reader = configReader ?: DefaultSSHConfigReader()
        val resolver = SSHConfigResolver()
        
        // Read user config
        configFile = reader.readUserConfig()
        
        // Resolve host configuration
        val resolved = resolver.resolveHost(hostname, configFile!!)
        
        // Apply to transport
        builder.transport {
            host = resolved.hostname
            port = resolved.port
        }
        
        // Apply authentication
        builder.authentication {
            // Set username
            val username = resolved.user
            
            // Try identity files
            // TODO: Check file existence in platform-specific way
            for (i in 0 until resolved.identityFiles.size) {
                val identityFile = resolved.identityFiles.j(i)
                publicKey {
                    keyPath = identityFile
                }
            }
            
            // Check authentication preferences
            val authPrefs = resolved.options["PreferredAuthentications"]
                ?.split(",")
                ?.map { it.trim() }
                ?: listOf("publickey", "password", "keyboard-interactive")
            
            if ("password" in authPrefs) {
                password {
                    this.username = username
                }
            }
            
            if ("keyboard-interactive" in authPrefs) {
                keyboardInteractive {
                    // Default handler
                }
            }
        }
        
        // Apply other options
        resolved.options.forEach { (key, value) ->
            when (SSHConfigOption.fromKey(key)) {
                SSHConfigOption.FORWARD_AGENT -> {
                    // Set agent forwarding
                }
                SSHConfigOption.FORWARD_X11 -> {
                    // Set X11 forwarding
                }
                SSHConfigOption.COMPRESSION -> {
                    // Set compression
                }
                SSHConfigOption.CIPHERS -> {
                    builder.algorithms {
                        cipher = value.split(",").map { it.trim() }
                    }
                }
                SSHConfigOption.KEX_ALGORITHMS -> {
                    builder.algorithms {
                        kex = value.split(",").map { it.trim() }
                    }
                }
                SSHConfigOption.HOST_KEY_ALGORITHMS -> {
                    builder.algorithms {
                        hostKey = value.split(",").map { it.trim() }
                    }
                }
                SSHConfigOption.MACS -> {
                    builder.algorithms {
                        mac = value.split(",").map { it.trim() }
                    }
                }
                else -> {
                    // Store for later use
                }
            }
        }
    }
    
    suspend fun build(): SSHSession = builder.build()
    
    // Public access to builder methods
    fun channels(init: SSHChannelBuilder.() -> Unit) = builder.channels(init)
    fun withContext(context: CoroutineContext) = builder.withContext(context)
}

// Default config reader implementation
class DefaultSSHConfigReader(
    internal val parser: SSHConfigParser = DefaultSSHConfigParser(),
    internal val fileLocator: SSHFileLocator = SSHFileLocator()
) : SSHConfigReader {
    
    override suspend fun readUserConfig(): SSHConfigFile = coroutineScope {
        val configPath = fileLocator.getUserConfigPath()
        
        if (!fileExists(configPath)) {
            // Return empty config
            return@coroutineScope 0 j { "" j 0 j { Join("", "" }) }
        }
        
        val content = readFile(configPath)
        
        // Handle Include directives
        val expandedContent = expandIncludes(content)
        
        parser.parseConfig(expandedContent)
    }
    
    override suspend fun readSystemConfig(): SSHConfigFile = coroutineScope {
        val configPath = fileLocator.getSystemConfigPath()
        
        if (!fileExists(configPath)) {
            return@coroutineScope 0 j { "" j 0 j { Join("", "" }) }
        }
        
        val content = readFile(configPath)
        parser.parseConfig(content)
    }
    
    override suspend fun readKnownHosts(): SSHKnownHostsFile = coroutineScope {
        val userPath = fileLocator.getUserKnownHostsPath()
        val systemPath = fileLocator.getSystemKnownHostsPath()
        
        val entries = mutableListOf<SSHKnownHostEntry>()
        
        // Read user known hosts
        if (fileExists(userPath)) {
            val content = readFile(userPath)
            val userHosts = parser.parseKnownHosts(content)
            for (i in 0 until userHosts.component1()) {
                entries.add(userHosts[i])
            }
        }
        
        // Read system known hosts
        if (fileExists(systemPath)) {
            val content = readFile(systemPath)
            val systemHosts = parser.parseKnownHosts(content)
            for (i in 0 until systemHosts.component1()) {
                entries.add(systemHosts[i])
            }
        }
        
        entries.size j { i: Int -> entries[i] }
    }
    
    override suspend fun readAuthorizedKeys(): SSHAuthorizedKeysFile = coroutineScope {
        val path = fileLocator.getUserAuthorizedKeysPath()
        
        if (!fileExists(path)) {
            return@coroutineScope 0 j { 
                0 j { "" } j Join("", Join(0 j { 0.toByte( }, "")))
            }
        }
        
        val content = readFile(path)
        parser.parseAuthorizedKeys(content)
    }
    
    override suspend fun findIdentityFiles(host: String): Indexed<String> = coroutineScope {
        val files = fileLocator.findIdentityFiles()
        files.size j { i: Int -> files[i] }
    }
    
    internal suspend fun expandIncludes(content: String): String {
        val result = StringBuilder()
        val includePattern = Regex("^\\s*Include\\s+(.+)$", RegexOption.MULTILINE)
        
        content.lines().forEach { line ->
            val match = includePattern.matchEntire(line.trim())
            if (match != null) {
                val includePath = match.groupValues[1]
                    // Platform-specific home directory expansion would go here
                    // For now, just use the path as-is
                    .replace("~", "/home/user")  // TODO: Make platform-specific
                
                // Handle glob patterns
                if (includePath.contains("*") || includePath.contains("?")) {
                    // Expand glob pattern - use platform-specific path operations
                    val lastSlash = includePath.lastIndexOf('/')
                    val dir = if (lastSlash > 0) includePath.substring(0, lastSlash) else "."
                    val pattern = includePath.substring(lastSlash + 1).toRegex()
                    
                    val files = listFiles(dir, pattern)
                    files.forEach { file ->
                        if (fileExists("$dir/$file")) {
                            val includeContent = readFile("$dir/$file")
                            result.appendLine("# Included from $dir/$file")
                            result.appendLine(includeContent)
                        }
                    }
                } else {
                    // Single file
                    if (fileExists(includePath)) {
                        val includeContent = readFile(includePath)
                        result.appendLine("# Included from $includePath")
                        result.appendLine(includeContent)
                    }
                }
            } else {
                result.appendLine(line)
            }
        }
        
        return result.toString()
    }
}

// Enhanced DSL entry point with config support
suspend fun sshConfig(init: SSHConfigAwareBuilder.() -> Unit): SSHSession {
    return SSHConfigAwareBuilder().apply(init).build()
}

// Extension functions for config-based connections
suspend fun fromConfig(
    hostname: String,
    init: SSHConfigAwareBuilder.() -> Unit = {}
): SSHSession {
    return sshConfig {
        fromConfig(hostname)
        init()
    }
}

// Host key verification
class SSHHostKeyVerifier(
    internal val reader: SSHConfigReader = DefaultSSHConfigReader()
) {
    internal var knownHosts: SSHKnownHostsFile? = null
    
    suspend fun loadKnownHosts() {
        knownHosts = reader.readKnownHosts()
    }
    
    suspend fun verifyHost(
        hostname: String,
        port: Int,
        keyType: String,
        hostKey: SSHPublicKey
    ): HostKeyVerificationResult {
        val hosts = knownHosts ?: run {
            loadKnownHosts()
            knownHosts!!
        }
        
        // Check all entries
        for (i in 0 until hosts.component1()) {
            val entry = hosts[i]
            val pattern = entry.component1()
            val entryKeyType = entry.component2().component1()
            val entryKey = entry.component2().component2()
            
            if (matchesHost(hostname, port, pattern) && keyType == entryKeyType) {
                return if (keysEqual(hostKey, entryKey)) {
                    HostKeyVerificationResult.MATCHED
                } else {
                    HostKeyVerificationResult.CHANGED
                }
            }
        }
        
        return HostKeyVerificationResult.UNKNOWN
    }
    
    internal fun matchesHost(hostname: String, port: Int, pattern: String): Boolean {
        // Handle [hostname]:port format
        val hostPort = if (port != 22) "[$hostname]:$port" else hostname
        
        // Handle hashed hosts
        if (pattern.startsWith("|1|")) {
            // Hashed host format
            return matchesHashedHost(hostPort, pattern)
        }
        
        // Handle wildcards and comma-separated patterns
        val patterns = pattern.split(",")
        return patterns.any { p ->
            val regex = p
                .replace(".", "\\.")
                .replace("*", ".*")
                .replace("?", ".")
            
            hostname.matches(Regex(regex)) || hostPort.matches(Regex(regex))
        }
    }
    
    internal fun matchesHashedHost(host: String, hashedPattern: String): Boolean {
        // Parse hashed format: |1|salt|hash
        val parts = hashedPattern.split("|")
        if (parts.size != 4) return false
        
        val salt = decodeBase64Public(parts[2]).toByteArray()
        val hash = decodeBase64Public(parts[3]).toByteArray()
        
        // Compute HMAC-SHA1 of host with salt
        // Simplified - would use actual HMAC
        return false
    }
    
    internal fun keysEqual(key1: SSHPublicKey, key2: SSHPublicKey): Boolean {
        if (key1.component1() != key2.component1()) return false
        
        for (i in 0 until key1.component1()) {
            if (key1[i] != key2[i]) return false
        }
        
        return true
    }
}

enum class HostKeyVerificationResult {
    MATCHED,    // Key matches known hosts
    CHANGED,    // Key has changed
    UNKNOWN     // Host not in known hosts
}

// Usage example
suspend fun exampleConfigUsage() = coroutineScope {
    // Simple connection using config
    val session = fromConfig("myserver") {
        // Override any config settings if needed
        channels {
            shell {
                pty = true
            }
        }
    }
    
    // Or with full DSL control
    val session2 = sshConfig {
        // Load from config
        fromConfig("production-server")
        
        // Additional configuration
        channels {
            forward {
                local("8080:localhost:80")
            }
        }
        
        withContext(Dispatchers.IO)
    }
    
    session.connect()
        .authenticate()
        .interactive()
}

// Public base64 decoder for use in other files
fun decodeBase64Public(data: String): Indexed<Byte> {
    // Remove any whitespace
    val cleaned = data.replace(Regex("\\s"), "")
    
    // Simple base64 decoding (would use proper implementation)
    val bytes = mutableListOf<Byte>()
    
    // Placeholder - in real implementation would decode properly
    for (i in cleaned.indices step 4) {
        val chunk = cleaned.substring(i, minOf(i + 4, cleaned.length))
        // Decode chunk...
        bytes.add((i % 256).toByte())
    }
    
    return bytes.size j { i: Int -> bytes[i] }
}

// Agent integration
interface SSHAgent {
    suspend fun listIdentities(): List<SSHAgentIdentity>
    suspend fun signData(identity: SSHAgentIdentity, data: Indexed<Byte>): SSHSignature
}

data class SSHAgentIdentity(
    val publicKey: SSHPublicKey,
    val comment: String
)

// Platform-specific agent implementation
expect fun getSSHAgent(): SSHAgent?
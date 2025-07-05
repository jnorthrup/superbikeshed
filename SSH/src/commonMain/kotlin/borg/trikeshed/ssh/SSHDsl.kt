package borg.trikeshed.ssh

import borg.trikeshed.lib.*
import borg.trikeshed.net.quic.QuicConnection
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/**
 * SSH DSL - Main Entry Point
 * 
 * Provides a fluent, type-safe DSL for configuring and establishing SSH connections
 * from main() through to the desired SSH operations.
 */

// Main SSH builder
class SSHBuilder {
    private var transport: TransportConfig? = null
    private var auth: AuthConfig? = null
    private var channels: ChannelConfig? = null
    private var algorithms: AlgorithmConfig? = null
    private var context: CoroutineContext = Dispatchers.Default
    
    fun transport(init: TransportConfig.() -> Unit) {
        transport = TransportConfig().apply(init)
    }
    
    fun authentication(init: AuthConfig.() -> Unit) {
        auth = AuthConfig().apply(init)
    }
    
    fun channels(init: ChannelConfig.() -> Unit) {
        channels = ChannelConfig().apply(init)
    }
    
    fun algorithms(init: AlgorithmConfig.() -> Unit) {
        algorithms = AlgorithmConfig().apply(init)
    }
    
    fun withContext(context: CoroutineContext) {
        this.context = context
    }
    
    suspend fun build(): SSHSession {
        val transportConfig = transport ?: throw IllegalStateException("Transport configuration required")
        val authConfig = auth ?: throw IllegalStateException("Authentication configuration required")
        
        // Create state machine
        val stateMachine = createSSHProtocolStateMachine()
        
        // Create server info
        val serverInfo = Join(transportConfig.host, transportConfig.port)
        
        // Initialize session context
        val sessionContext = Join(
            authConfig.identity ?: generateEphemeralIdentity(),
            Join(serverInfo, context)
        )
        
        // Connect
        val transportState = stateMachine.connect(serverInfo, context)
        
        // Create initial session
        val channelStream = 0 j { SSHChannelEvent(Join(0u, ChannelData(0 j { 0.toByte() }))) }
        
        return Join(
            transportState,
            Join(channelStream, sessionContext)
        )
    }
}

// Transport configuration
class TransportConfig {
    var host: SSHHostname = "localhost"
    var port: SSHPort = 22
    var backend: TransportBackend = TransportBackend.QUIC
    var timeout: Long = 30000L
    var keepAlive: Boolean = true
    var keepAliveInterval: Long = 10000L
    
    enum class TransportBackend {
        QUIC,
        TCP,
        WEBSOCKET
    }
}

// Authentication configuration
class AuthConfig {
    var identity: SSHIdentity? = null
    private val methods = mutableListOf<AuthMethodConfig>()
    
    fun publicKey(init: PublicKeyAuth.() -> Unit) {
        val config = PublicKeyAuth().apply(init)
        methods.add(config)
    }
    
    fun password(init: PasswordAuth.() -> Unit) {
        val config = PasswordAuth().apply(init)
        methods.add(config)
    }
    
    fun keyboardInteractive(init: KeyboardInteractiveAuth.() -> Unit) {
        val config = KeyboardInteractiveAuth().apply(init)
        methods.add(config)
    }
    
    fun getAuthMethods(): List<AuthMethodConfig> = methods.toList()
}

// Authentication method configurations
sealed class AuthMethodConfig

class PublicKeyAuth : AuthMethodConfig() {
    var keyPath: String = "~/.ssh/id_ed25519"
    var passphrase: (() -> String)? = null
    
    fun passphrase(provider: () -> String) {
        passphrase = provider
    }
}

class PasswordAuth : AuthMethodConfig() {
    var username: String = System.getProperty("user.name")
    var password: (() -> String)? = null
    
    fun password(provider: () -> String) {
        password = provider
    }
}

class KeyboardInteractiveAuth : AuthMethodConfig() {
    var handler: ((List<String>) -> List<String>)? = null
    
    fun handle(handler: (List<String>) -> List<String>) {
        this.handler = handler
    }
}

// Channel configuration
class ChannelConfig {
    private val channelConfigs = mutableListOf<ChannelTypeConfig>()
    
    fun shell(init: ShellChannel.() -> Unit) {
        channelConfigs.add(ShellChannel().apply(init))
    }
    
    fun exec(command: String, init: ExecChannel.() -> Unit = {}) {
        channelConfigs.add(ExecChannel(command).apply(init))
    }
    
    fun subsystem(name: String, init: SubsystemChannel.() -> Unit = {}) {
        channelConfigs.add(SubsystemChannel(name).apply(init))
    }
    
    fun forward(init: ForwardingConfig.() -> Unit) {
        channelConfigs.add(ForwardingConfig().apply(init))
    }
    
    fun getChannels(): List<ChannelTypeConfig> = channelConfigs.toList()
}

// Channel type configurations
sealed class ChannelTypeConfig

class ShellChannel : ChannelTypeConfig() {
    var pty: Boolean = true
    var terminalType: String = "xterm-256color"
    var rows: Int = 24
    var columns: Int = 80
    val environment = mutableMapOf<String, String>()
    
    fun env(name: String, value: String) {
        environment[name] = value
    }
}

class ExecChannel(val command: String) : ChannelTypeConfig() {
    var pty: Boolean = false
    val environment = mutableMapOf<String, String>()
}

class SubsystemChannel(val name: String) : ChannelTypeConfig()

class ForwardingConfig : ChannelTypeConfig() {
    private val localForwards = mutableListOf<LocalForward>()
    private val remoteForwards = mutableListOf<RemoteForward>()
    private val dynamicForwards = mutableListOf<DynamicForward>()
    
    fun local(spec: String) {
        // Parse "8080:localhost:80" format
        val parts = spec.split(":")
        if (parts.size == 3) {
            localForwards.add(LocalForward(
                bindPort = parts[0].toInt(),
                targetHost = parts[1],
                targetPort = parts[2].toInt()
            ))
        }
    }
    
    fun remote(spec: String) {
        // Parse "3000:localhost:3000" format
        val parts = spec.split(":")
        if (parts.size == 3) {
            remoteForwards.add(RemoteForward(
                bindPort = parts[0].toInt(),
                targetHost = parts[1],
                targetPort = parts[2].toInt()
            ))
        }
    }
    
    fun dynamic(port: Int) {
        dynamicForwards.add(DynamicForward(port))
    }
    
    data class LocalForward(val bindPort: Int, val targetHost: String, val targetPort: Int)
    data class RemoteForward(val bindPort: Int, val targetHost: String, val targetPort: Int)
    data class DynamicForward(val bindPort: Int)
}

// Algorithm configuration
class AlgorithmConfig {
    var kex: List<String> = listOf(
        "curve25519-sha256",
        "ecdh-sha2-nistp256",
        "diffie-hellman-group14-sha256"
    )
    
    var hostKey: List<String> = listOf(
        "ssh-ed25519",
        "ecdsa-sha2-nistp256",
        "rsa-sha2-256"
    )
    
    var cipher: List<String> = listOf(
        "chacha20-poly1305@openssh.com",
        "aes256-gcm@openssh.com",
        "aes128-gcm@openssh.com"
    )
    
    var mac: List<String> = listOf(
        "hmac-sha2-256",
        "hmac-sha2-512"
    )
    
    var compression: List<String> = listOf(
        "none",
        "zlib@openssh.com"
    )
}

// Main entry point DSL function
suspend fun ssh(init: SSHBuilder.() -> Unit): SSHSession {
    return SSHBuilder().apply(init).build()
}

// Session extension functions for fluent API
suspend fun SSHSession.connect(): SSHSession {
    val stateMachine = createSSHProtocolStateMachine()
    val context = b.b.b
    val serverInfo = b.b.a
    
    val newState = stateMachine.connect(serverInfo, context)
    return Join(newState, b)
}

suspend fun SSHSession.authenticate(): SSHSession {
    val authService = SSHServiceLocator.getAuthService(b.b.b)
    // Perform authentication
    return this
}

suspend fun SSHSession.openChannels(): SSHSession {
    val channelService = SSHServiceLocator.getChannelService(b.b.b)
    // Open configured channels
    return this
}

suspend fun SSHSession.interactive(): SSHSession {
    // Enter interactive mode
    return this
}

suspend fun SSHSession.close() {
    val stateMachine = createSSHProtocolStateMachine()
    val context = b.b.b
    stateMachine.disconnect(a, 11u, context) // SSH_DISCONNECT_BY_APPLICATION
}

// Helper functions
private fun generateEphemeralIdentity(): SSHIdentity {
    // Generate temporary key pair for session
    val publicKey = 32 j { i: Int -> (i * 7).toByte() } // Placeholder
    val privateKey = 32 j { i: Int -> (i * 13).toByte() } // Placeholder
    return Join(publicKey, privateKey)
}

// Prompt helper for authentication
fun prompt(prompt: String = "Password: "): () -> String = {
    print(prompt)
    System.console()?.readPassword()?.let { String(it) } ?: readLine() ?: ""
}

// Usage example that would go in main()
suspend fun exampleUsage() {
    val session = ssh {
        transport {
            host = "example.com"
            port = 22
            backend = TransportConfig.TransportBackend.QUIC
            timeout = 30000L
        }
        
        authentication {
            publicKey {
                keyPath = "~/.ssh/id_ed25519"
                passphrase { prompt("Enter passphrase: ") }
            }
            
            password {
                username = "user"
                password { prompt("Password: ") }
            }
        }
        
        channels {
            shell {
                pty = true
                terminalType = "xterm-256color"
                rows = 40
                columns = 120
                env("LANG", "en_US.UTF-8")
                env("TERM", "xterm-256color")
            }
            
            forward {
                local("8080:localhost:80")
                remote("3000:localhost:3000")
                dynamic(1080) // SOCKS proxy
            }
        }
        
        algorithms {
            kex = listOf("curve25519-sha256", "ecdh-sha2-nistp256")
            cipher = listOf("chacha20-poly1305@openssh.com")
        }
        
        withContext(Dispatchers.IO + CoroutineName("SSH-Session"))
    }
    
    // Fluent API for session lifecycle
    session
        .connect()
        .authenticate()
        .openChannels()
        .interactive()
    
    // Session is automatically closed when coroutine scope ends
}

// SFTP subsystem DSL
suspend fun SSHSession.sftp(init: SFTPBuilder.() -> Unit) {
    val builder = SFTPBuilder(this)
    builder.init()
}

class SFTPBuilder(private val session: SSHSession) {
    suspend fun upload(localPath: String, remotePath: String) {
        // SFTP upload implementation
    }
    
    suspend fun download(remotePath: String, localPath: String) {
        // SFTP download implementation
    }
    
    suspend fun list(path: String): List<SFTPFileInfo> {
        // SFTP directory listing
        return emptyList()
    }
    
    suspend fun mkdir(path: String) {
        // Create remote directory
    }
    
    suspend fun rm(path: String) {
        // Remove remote file
    }
}

data class SFTPFileInfo(
    val name: String,
    val size: Long,
    val permissions: Int,
    val isDirectory: Boolean
)
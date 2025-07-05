package borg.trikeshed.ssh

import borg.trikeshed.lib.*
import kotlinx.coroutines.*

/**
 * SSH Usage Examples
 * 
 * Demonstrates the integrated SSH config reader and DSL
 */

// Example 1: Simple connection using ~/.ssh/config
suspend fun connectUsingConfig() {
    // Connect to a host defined in ~/.ssh/config
    val session = SSHSession.fromConfig("myserver")
    
    // The connection automatically uses:
    // - Hostname, port, and user from config
    // - Identity files from config
    // - Algorithm preferences from config
    // - ProxyJump/ProxyCommand if configured
    
    session
        .connect()
        .authenticate()
        .openChannels()
        .interactive()
}

// Example 2: Override config settings
suspend fun connectWithOverrides() {
    val session = SSHSession.fromConfig("production") {
        // Override port
        transport {
            port = 2222
        }
        
        // Add additional authentication method
        authentication {
            password {
                password { 
                    // Use secure password input
                    promptSecure("Password for production: ")
                }
            }
        }
        
        // Add port forwarding not in config
        channels {
            forward {
                local("8080:localhost:80")
                local("3306:localhost:3306")
            }
        }
    }
    
    session.connect()
}

// Example 3: Manual configuration with config reader
suspend fun manualConfigUsage() {
    val session = sshConfig {
        // Load base config
        fromConfig("bastion")
        
        // Complex multi-hop setup
        transport {
            // ProxyJump equivalent
            backend = TransportConfig.TransportBackend.TCP
        }
        
        // Use SSH agent
        authentication {
            val agent = getSSHAgent()
            if (agent != null) {
                // List available keys
                val identities = agent.listIdentities()
                
                identities.forEach { identity ->
                    println("Agent key: ${SSHKeyUtils.formatFingerprint(identity.publicKey)}")
                }
                
                // Use agent for authentication
                publicKey {
                    // Agent will handle signing
                }
            }
        }
        
        channels {
            // SOCKS proxy
            forward {
                dynamic(1080)
            }
            
            // Execute command
            exec("tmux attach || tmux new") {
                pty = true
                env("TERM", "screen-256color")
            }
        }
    }
    
    session.connect()
}

// Example 4: Host key verification
suspend fun connectWithHostKeyVerification() {
    val verifier = SSHHostKeyVerifier()
    
    val session = ssh {
        transport {
            host = "newserver.example.com"
            port = 22
        }
        
        authentication {
            publicKey {
                keyPath = "~/.ssh/id_ed25519"
            }
        }
        
        // Custom host key verification
        withContext(coroutineContext + object : CoroutineContext.Element {
            override val key: CoroutineContext.Key<*> = HostKeyVerifierKey
            
            suspend fun verify(
                hostname: String,
                port: Int,
                keyType: String,
                hostKey: SSHPublicKey
            ): Boolean {
                when (verifier.verifyHost(hostname, port, keyType, hostKey)) {
                    HostKeyVerificationResult.MATCHED -> return true
                    HostKeyVerificationResult.CHANGED -> {
                        println("WARNING: HOST KEY HAS CHANGED!")
                        println("Fingerprint: ${SSHKeyUtils.formatFingerprint(hostKey)}")
                        print("Accept new key? (yes/no): ")
                        return readLine() == "yes"
                    }
                    HostKeyVerificationResult.UNKNOWN -> {
                        println("Unknown host: $hostname")
                        println("Fingerprint: ${SSHKeyUtils.formatFingerprint(hostKey)}")
                        print("Accept key? (yes/no): ")
                        return readLine() == "yes"
                    }
                }
            }
        })
    }
    
    session.connect()
}

// Example 5: SFTP with config
suspend fun sftpExample() {
    val session = SSHSession.fromConfig("fileserver")
    
    session.connect().authenticate()
    
    // SFTP operations
    session.sftp {
        // Upload file
        upload("/local/path/file.txt", "/remote/path/file.txt")
        
        // Download file
        download("/remote/path/data.csv", "/local/path/data.csv")
        
        // List directory
        val files = list("/remote/path")
        files.forEach { file ->
            println("${file.name} - ${file.size} bytes")
        }
        
        // Create directory
        mkdir("/remote/path/newdir")
    }
}

// Example 6: Read and parse SSH config manually
suspend fun parseConfigExample() {
    val reader = DefaultSSHConfigReader()
    val parser = DefaultSSHConfigParser()
    
    // Read user config
    val config = reader.readUserConfig()
    
    // Find specific host
    for (i in 0 until config.a) {
        val host = config[i].a
        val settings = config[i].b
        
        if (host == "myserver" || host == "*") {
            println("Host: $host")
            for (j in 0 until settings.a) {
                val key = settings[j].a
                val value = settings[j].b
                println("  $key = $value")
            }
        }
    }
    
    // Read known hosts
    val knownHosts = reader.readKnownHosts()
    println("\nKnown hosts: ${knownHosts.a} entries")
    
    // Find identity files
    val identityFiles = reader.findIdentityFiles("myserver")
    println("\nIdentity files:")
    for (i in 0 until identityFiles.a) {
        println("  ${identityFiles[i]}")
    }
}

// Example 7: Batch operations using config
suspend fun batchOperations() {
    val hosts = listOf("web1", "web2", "web3", "db1", "db2")
    
    coroutineScope {
        hosts.map { hostname ->
            async {
                try {
                    val session = SSHSession.fromConfig(hostname) {
                        channels {
                            exec("sudo systemctl restart nginx") {
                                pty = false
                            }
                        }
                    }
                    
                    session.connect().authenticate()
                    println("$hostname: Command executed successfully")
                } catch (e: Exception) {
                    println("$hostname: Failed - ${e.message}")
                }
            }
        }.awaitAll()
    }
}

// Helper for secure password input
suspend fun promptSecure(prompt: String): String {
    print(prompt)
    return System.console()?.readPassword()?.let { String(it) }
        ?: readLine()
        ?: ""
}

// Coroutine context key for host key verifier
object HostKeyVerifierKey : CoroutineContext.Key<CoroutineContext.Element>

// Main function demonstrating various connection methods
suspend fun main() {
    // Choose connection method
    println("SSH Connection Examples:")
    println("1. Connect using ~/.ssh/config")
    println("2. Connect with config overrides")
    println("3. Manual configuration")
    println("4. Host key verification")
    println("5. SFTP operations")
    println("6. Parse config manually")
    println("7. Batch operations")
    
    print("Select option (1-7): ")
    when (readLine()) {
        "1" -> connectUsingConfig()
        "2" -> connectWithOverrides()
        "3" -> manualConfigUsage()
        "4" -> connectWithHostKeyVerification()
        "5" -> sftpExample()
        "6" -> parseConfigExample()
        "7" -> batchOperations()
        else -> println("Invalid option")
    }
}
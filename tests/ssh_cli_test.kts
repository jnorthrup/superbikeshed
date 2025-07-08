
#!/usr/bin/env kscript

@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-debug:1.8.0")

package tests

import borg.trikeshed.crypto.CryptoFactory
import borg.trikeshed.io.PlatformFileIOImpl
import borg.trikeshed.net.quic.DefaultQuicSessionCache
import borg.trikeshed.net.quic.QuicConfig
import borg.trikeshed.net.quic.QuicConnection
import borg.trikeshed.net.ssh.SCPClient
import borg.trikeshed.net.ssh.SSHConnection
import borg.trikeshed.net.ssh.SFTPClient
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

fun main(args: Array<String>) = runBlocking {
    if (args.isEmpty()) {
        printHelp()
        return@runBlocking
    }

    var host = "localhost"
    var port = 4242 // Default QUIC port for testing
    var username = "user"
    var password = "password"
    var command: String? = null
    var localPath: String? = null
    var remotePath: String? = null
    var operation: String? = null

    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--host" -> { host = args[++i] }
            "--port" -> { port = args[++i].toInt() }
            "--user" -> { username = args[++i] }
            "--password" -> { password = args[++i] }
            "--command" -> { command = args[++i] }
            "--local" -> { localPath = args[++i] }
            "--remote" -> { remotePath = args[++++i] } // Adjusted for --remote <path>
            "--upload" -> { operation = "upload" }
            "--download" -> { operation = "download" }
            "--sftp-open" -> { operation = "sftp-open" }
            "--rsync" -> { operation = "rsync" }
            else -> {
                println("Unknown argument: ${args[i]}")
                printHelp()
                return@runBlocking
            }
        }
        i++
    }

    val quicConfig = QuicConfig()
    val sessionCache = DefaultQuicSessionCache()
    val crypto = CryptoFactory.getSecureRandom()
    val quicConnection = QuicConnection(quicConfig, sessionCache, CoroutineScope(Dispatchers.IO), Dispatchers.IO)

    println("Attempting to connect to $host:$port...")
    if (!quicConnection.connect(host, port)) {
        println("Failed to connect to QUIC server.")
        return@runBlocking
    }
    println("QUIC connection established.")

    val sshConnection = SSHConnection(quicConnection, crypto = crypto)

    println("Attempting SSH authentication for $username...")
    if (!sshConnection.authenticate(username, password)) {
        println("SSH authentication failed.")
        quicConnection.close()
        return@runBlocking
    }
    println("SSH authentication successful.")

    when (operation) {
        "upload" -> {
            if (localPath == null || remotePath == null) {
                println("Error: --local and --remote paths are required for upload.")
                printHelp()
                return@runBlocking
            }
            val scpClient = SCPClient(sshConnection, 0u, PlatformFileIOImpl()) // Channel ID is placeholder
            scpClient.upload(localPath!!, remotePath!!)
        }
        "download" -> {
            if (localPath == null || remotePath == null) {
                println("Error: --local and --remote paths are required for download.")
                printHelp()
                return@runBlocking
            }
            val scpClient = SCPClient(sshConnection, 0u, PlatformFileIOImpl()) // Channel ID is placeholder
            scpClient.download(remotePath!!, localPath!!)
        }
        "sftp-open" -> {
            if (remotePath == null) {
                println("Error: --remote path is required for SFTP open.")
                printHelp()
                return@runBlocking
            }
            val sftpClient = SFTPClient(sshConnection, 0u) // Channel ID is placeholder
            sftpClient.init()
            val handle = sftpClient.open(remotePath!!)
            if (handle != null) {
                println("SFTP: Opened $remotePath with handle: $handle")
            } else {
                println("SFTP: Failed to open $remotePath")
            }
        }
        "rsync" -> {
            if (command == null) {
                println("Error: --command is required for rsync operation.")
                printHelp()
                return@runBlocking
            }
            println("Executing rsync command: $command")
            val output = sshConnection.executeRsync(command!!)
            println("Rsync output:
$output")
        }
        else -> {
            if (command != null) {
                println("Executing SSH command: $command")
                val output = sshConnection.executeCommand(0u, command!!) // Channel ID is placeholder
                // TODO: Capture and print output from executeCommand
                println("Command executed. Output capture not yet implemented for direct command.")
            } else {
                printHelp()
            }
        }
    }

    println("Closing SSH and QUIC connections.")
    sshConnection.disconnect()
    quicConnection.close()
}

fun printHelp() {
    println("Usage: kscript ssh_cli_test.kts [options] [operation]")
    println("Options:")
    println("  --host <hostname>    Remote host (default: localhost)")
    println("  --port <port>        Remote port (default: 4242)")
    println("  --user <username>    Username for SSH authentication")
    println("  --password <password> Password for SSH authentication")
    println("  --command <cmd>      Command to execute via SSH (for direct execution or rsync)")
    println("  --local <path>       Local file path (for SCP upload/download)")
    println("  --remote <path>      Remote file path (for SCP upload/download, SFTP open)")
    println("Operations:")
    println("  --upload             Upload local file to remote path via SCP")
    println("  --download           Download remote file to local path via SCP")
    println("  --sftp-open          Open a file via SFTP")
    println("  --rsync              Execute an rsync command over SSH")
    println("
Examples:")
    println("  kscript ssh_cli_test.kts --host myhost --user testuser --password testpass --command "ls -l"")
    println("  kscript ssh_cli_test.kts --host myhost --user testuser --password testpass --upload --local /tmp/local.txt --remote /tmp/remote.txt")
    println("  kscript ssh_cli_test.kts --host myhost --user testuser --password testpass --download --remote /tmp/remote.txt --local /tmp/downloaded.txt")
    println("  kscript ssh_cli_test.kts --host myhost --user testuser --password testpass --sftp-open --remote /tmp/test.txt")
    println("  kscript ssh_cli_test.kts --host myhost --user testuser --password testpass --rsync --command "rsync -avz /local/path user@remote:/remote/path"")
}

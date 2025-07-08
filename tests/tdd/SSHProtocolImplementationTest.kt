package tests.tdd

import borg.trikeshed.ssh.*
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/**
 * SSH Protocol Implementation Test
 * 
 * This test demonstrates the actual SSH implementation for SCP, SFTP, and Rsync
 * based on the TDD test requirements.
 */
object SSHProtocolImplementationTest {
    
    @JvmStatic
    fun main(args: Array<String>) {
        println("=== SSH PROTOCOL IMPLEMENTATION TEST ===")
        println("Testing actual SSH SCP, SFTP, and Rsync implementations...")
        println()
        
        runBlocking {
            // Run all test iterations
            testSSHConnection()
            testSCPOperations()
            testSFTPOperations()
            testRsyncOperations()
            testIntegratedOperations()
            
            println("=== ALL SSH PROTOCOL IMPLEMENTATION TESTS COMPLETED SUCCESSFULLY ===")
        }
    }
    
    private suspend fun testSSHConnection() {
        println("Iteration 1: Core SSH Connection")
        
        val connectionManager = SSHConnectionManagerFactory.createConnectionManager()
        val context = SSHConnectionContext(Dispatchers.IO)
        
        // Test connection establishment
        val connection = connectionManager.connect("testhost", 22, context)
        assert(connection.isConnected) { "Connection should be established" }
        assert(connection.state == SSHConnectionState.CONNECTED) { "Connection state should be CONNECTED" }
        println("✓ SSH connection established")
        
        // Test authentication
        val authSuccess = connectionManager.authenticate(connection, "testuser", "testpass", context)
        assert(authSuccess) { "Authentication should succeed" }
        assert(connection.isAuthenticated) { "Connection should be authenticated" }
        assert(connection.state == SSHConnectionState.AUTHENTICATED) { "Connection state should be AUTHENTICATED" }
        println("✓ SSH authentication successful")
        
        // Test channel creation
        val channel = connectionManager.openChannel(connection, "session", context)
        assert(channel.state == SSHChannelState.OPEN) { "Channel should be open" }
        assert(connection.channels.containsKey(channel.id)) { "Channel should be in connection" }
        println("✓ SSH channel created")
        
        // Test connection closure
        connectionManager.closeConnection(connection, context)
        assert(!connection.isConnected) { "Connection should be closed" }
        assert(connection.state == SSHConnectionState.DISCONNECTED) { "Connection state should be DISCONNECTED" }
        assert(connection.channels.isEmpty()) { "All channels should be closed" }
        println("✓ SSH connection closed")
        
        println("✓ SSH connection tests passed")
        println()
    }
    
    private suspend fun testSCPOperations() {
        println("Iteration 2: SCP Implementation")
        
        val connectionManager = SSHConnectionManagerFactory.createConnectionManager()
        val context = SSHConnectionContext(Dispatchers.IO)
        val connection = connectionManager.connect("testhost", 22, context)
        connectionManager.authenticate(connection, "testuser", "testpass", context)
        
        val fileSystem = SSHMockFileSystemFactory.createMockFileSystem()
        val scpClient = SSHScpClientFactory.createScpClient(connection, fileSystem)
        val scpContext = SSHScpContext(Dispatchers.IO)
        
        // Test SCP upload
        val uploadSuccess = scpClient.upload("/local/test.txt", "/remote/test.txt", scpContext)
        assert(uploadSuccess) { "SCP upload should succeed" }
        println("✓ SCP upload successful")
        
        // Test SCP download
        val downloadSuccess = scpClient.download("/remote/download.txt", "/local/download.txt", scpContext)
        assert(downloadSuccess) { "SCP download should succeed" }
        assert(fileSystem.fileExists("/local/download.txt")) { "Downloaded file should exist" }
        println("✓ SCP download successful")
        
        // Test SCP directory upload
        val dirUploadSuccess = scpClient.uploadDirectory("/local/dir", "/remote/dir", scpContext)
        assert(dirUploadSuccess) { "SCP directory upload should succeed" }
        println("✓ SCP directory upload successful")
        
        connectionManager.closeConnection(connection, context)
        println("✓ SCP tests passed")
        println()
    }
    
    private suspend fun testSFTPOperations() {
        println("Iteration 3: SFTP Implementation")
        
        val connectionManager = SSHConnectionManagerFactory.createConnectionManager()
        val context = SSHConnectionContext(Dispatchers.IO)
        val connection = connectionManager.connect("testhost", 22, context)
        connectionManager.authenticate(connection, "testuser", "testpass", context)
        
        val fileSystem = SSHMockFileSystemFactory.createMockFileSystem()
        val sftpClient = SSHSftpClientFactory.createSftpClient(connection, fileSystem)
        val sftpContext = SSHSftpContext(Dispatchers.IO)
        
        // Test SFTP channel opening
        val channelId = sftpClient.openChannel(sftpContext)
        assert(channelId > 0) { "SFTP channel should be opened" }
        println("✓ SFTP channel opened")
        
        // Test SFTP directory listing
        val files = sftpClient.listDirectory("/local", sftpContext)
        assert(files.isNotEmpty()) { "Directory listing should return files" }
        println("✓ SFTP directory listing successful")
        
        // Test SFTP file operations
        val handle = sftpClient.openFile("/local/source.txt", "read", sftpContext)
        assert(handle != null) { "File should be opened" }
        println("✓ SFTP file opened")
        
        val data = sftpClient.readFile(handle!!, 0L, 100, sftpContext)
        assert(data.isNotEmpty()) { "File data should be read" }
        println("✓ SFTP file read successful")
        
        sftpClient.closeFile(handle, sftpContext)
        println("✓ SFTP file closed")
        
        // Test SFTP file operations
        sftpClient.createDirectory("/remote/newdir", sftpContext)
        println("✓ SFTP directory created")
        
        sftpClient.removeFile("/local/test.txt", sftpContext)
        println("✓ SFTP file removed")
        
        sftpClient.renameFile("/local/source.txt", "/local/renamed.txt", sftpContext)
        println("✓ SFTP file renamed")
        
        sftpClient.closeChannel(sftpContext)
        println("✓ SFTP channel closed")
        
        connectionManager.closeConnection(connection, context)
        println("✓ SFTP tests passed")
        println()
    }
    
    private suspend fun testRsyncOperations() {
        println("Iteration 4: Rsync Implementation")
        
        val connectionManager = SSHConnectionManagerFactory.createConnectionManager()
        val context = SSHConnectionContext(Dispatchers.IO)
        val connection = connectionManager.connect("testhost", 22, context)
        connectionManager.authenticate(connection, "testuser", "testpass", context)
        
        val rsyncClient = SSHRsyncClientFactory.createRsyncClient(connection)
        val rsyncContext = SSHRsyncContext(Dispatchers.IO)
        
        // Test basic rsync command execution
        val output = rsyncClient.executeRsync("rsync -avz /local /remote", rsyncContext)
        assert(output.isNotEmpty()) { "Rsync output should not be empty" }
        assert(!output.contains("Error:")) { "Rsync should not return error" }
        println("✓ Rsync command execution successful")
        
        // Test rsync directory sync
        val syncResult = rsyncClient.syncDirectory("/local", "/remote", rsyncContext)
        assert(syncResult.success) { "Rsync sync should succeed" }
        assert(syncResult.filesTransferred > 0) { "Files should be transferred" }
        println("✓ Rsync directory sync successful")
        
        // Test rsync with delete option
        val deleteResult = rsyncClient.syncWithDelete("/local", "/remote", rsyncContext)
        assert(deleteResult.success) { "Rsync sync with delete should succeed" }
        println("✓ Rsync sync with delete successful")
        
        // Test rsync with exclude patterns
        val excludeResult = rsyncClient.syncWithExclude("/local", "/remote", listOf("*.tmp", "*.log"), rsyncContext)
        assert(excludeResult.success) { "Rsync sync with exclusions should succeed" }
        println("✓ Rsync sync with exclusions successful")
        
        // Test rsync with bandwidth limit
        val bwResult = rsyncClient.syncWithBandwidthLimit("/local", "/remote", 1024, rsyncContext)
        assert(bwResult.success) { "Rsync sync with bandwidth limit should succeed" }
        println("✓ Rsync sync with bandwidth limit successful")
        
        connectionManager.closeConnection(connection, context)
        println("✓ Rsync tests passed")
        println()
    }
    
    private suspend fun testIntegratedOperations() {
        println("Iteration 5: Integrated File Transfer")
        
        val connectionManager = SSHConnectionManagerFactory.createConnectionManager()
        val context = SSHConnectionContext(Dispatchers.IO)
        val connection = connectionManager.connect("testhost", 22, context)
        connectionManager.authenticate(connection, "testuser", "testpass", context)
        
        val fileSystem = SSHMockFileSystemFactory.createMockFileSystem()
        val workflow = SSHIntegratedWorkflowFactory.createWorkflow(connection, fileSystem)
        val workflowContext = SSHWorkflowContext(Dispatchers.IO)
        
        // Test SCP workflow
        val scpResult = workflow.transferFile("/local/source.txt", "/remote/scp.txt", TransferMethod.SCP, workflowContext)
        assert(scpResult.success) { "SCP workflow should succeed" }
        assert(scpResult.method == TransferMethod.SCP) { "Method should be SCP" }
        println("✓ SCP workflow successful")
        
        // Test SFTP workflow
        val sftpResult = workflow.transferFile("/local/source.txt", "/remote/sftp.txt", TransferMethod.SFTP, workflowContext)
        assert(sftpResult.success) { "SFTP workflow should succeed" }
        assert(sftpResult.method == TransferMethod.SFTP) { "Method should be SFTP" }
        println("✓ SFTP workflow successful")
        
        // Test Rsync workflow
        val rsyncResult = workflow.transferFile("/local/source.txt", "/remote/rsync.txt", TransferMethod.RSYNC, workflowContext)
        assert(rsyncResult.success) { "Rsync workflow should succeed" }
        assert(rsyncResult.method == TransferMethod.RSYNC) { "Method should be RSYNC" }
        println("✓ Rsync workflow successful")
        
        // Test directory sync workflow
        val syncResult = workflow.syncDirectory("/local/dir", "/remote/dir", workflowContext)
        assert(syncResult.success) { "Directory sync workflow should succeed" }
        println("✓ Directory sync workflow successful")
        
        // Test transfer with retry
        val retryResult = workflow.transferWithRetry("/local/source.txt", "/remote/retry.txt", TransferMethod.SCP, 3, workflowContext)
        assert(retryResult.success) { "Transfer with retry should succeed" }
        println("✓ Transfer with retry successful")
        
        // Test transfer with progress
        var progressUpdates = 0
        val progressResult = workflow.transferWithProgress("/local/source.txt", "/remote/progress.txt", TransferMethod.SCP, { progress ->
            progressUpdates++
            println("Progress: $progress%")
        }, workflowContext)
        assert(progressResult.success) { "Transfer with progress should succeed" }
        assert(progressUpdates > 0) { "Progress updates should be received" }
        println("✓ Transfer with progress successful")
        
        // Test error handling
        val errorResult = workflow.transferFile("/nonexistent/file.txt", "/remote/error.txt", TransferMethod.SCP, workflowContext)
        assert(!errorResult.success) { "Transfer of nonexistent file should fail" }
        assert(errorResult.errorMessage != null) { "Error message should be provided" }
        println("✓ Error handling successful")
        
        connectionManager.closeConnection(connection, context)
        println("✓ Integrated operations tests passed")
        println()
    }
} 
package com.v2superbikeshed.nexus.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.Messages
import com.v2superbikeshed.nexus.mcp.McpServerLauncher
import kotlinx.coroutines.runBlocking

/**
 * Action to start the MCP server
 */
class StartMcpServerAction : AnAction() {
    
    override fun actionPerformed(e: AnActionEvent) {
        ApplicationManager.getApplication().invokeLater {
            try {
                val launcher = McpServerLauncher.getInstance()
                launcher.startMcpServer()
                
                Messages.showInfoMessage(
                    e.project,
                    "MCP Server started successfully on port 63343",
                    "MCP Server Started"
                )
            } catch (ex: Exception) {
                Messages.showErrorDialog(
                    e.project,
                    "Failed to start MCP server: ${ex.message}",
                    "MCP Server Error"
                )
            }
        }
    }
    
    override fun update(e: AnActionEvent) {
        val launcher = McpServerLauncher.getInstance()
        e.presentation.isEnabled = !launcher.isRunning()
    }
}

/**
 * Action to stop the MCP server
 */
class StopMcpServerAction : AnAction() {
    
    override fun actionPerformed(e: AnActionEvent) {
        ApplicationManager.getApplication().invokeLater {
            try {
                val launcher = McpServerLauncher.getInstance()
                launcher.stopMcpServer()
                
                Messages.showInfoMessage(
                    e.project,
                    "MCP Server stopped successfully",
                    "MCP Server Stopped"
                )
            } catch (ex: Exception) {
                Messages.showErrorDialog(
                    e.project,
                    "Failed to stop MCP server: ${ex.message}",
                    "MCP Server Error"
                )
            }
        }
    }
    
    override fun update(e: AnActionEvent) {
        val launcher = McpServerLauncher.getInstance()
        e.presentation.isEnabled = launcher.isRunning()
    }
}

/**
 * Action to test the MCP server
 */
class TestMcpServerAction : AnAction() {
    
    override fun actionPerformed(e: AnActionEvent) {
        ApplicationManager.getApplication().invokeLater {
            try {
                // Run test in blocking context since it's a quick test
                runBlocking {
                    val testClient = com.v2superbikeshed.nexus.mcp.McpTestClient()
                    testClient.testMcpServer()
                }
                
                Messages.showInfoMessage(
                    e.project,
                    "MCP Server test completed. Check IDE logs for details.",
                    "MCP Server Test"
                )
            } catch (ex: Exception) {
                Messages.showErrorDialog(
                    e.project,
                    "MCP server test failed: ${ex.message}",
                    "MCP Server Test Error"
                )
            }
        }
    }
    
    override fun update(e: AnActionEvent) {
        val launcher = McpServerLauncher.getInstance()
        e.presentation.isEnabled = launcher.isRunning()
    }
}
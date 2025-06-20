import * as vscode from "vscode"
import * as dotenvx from "@dotenvx/dotenvx"
import * as path from "path"

// Load environment variables from .env file
try {
	// Specify path to .env file in the project root directory
	const envPath = path.join(__dirname, "..", ".env")
	dotenvx.config({ path: envPath })
} catch (e) {
	// Silently handle environment loading errors
	console.warn("Failed to load environment variables:", e)
}

import { CloudService } from "@roo-code/cloud"
import { TelemetryService, PostHogTelemetryClient } from "@roo-code/telemetry"

import "./utils/path" // Necessary to have access to String.prototype.toPosix.
import { createOutputChannelLogger, createDualLogger } from "./utils/outputChannelLogger"

import { Package } from "./shared/package"
import { formatLanguage } from "./shared/language"
import { ContextProxy } from "./core/config/ContextProxy"
import { ClineProvider } from "./core/webview/ClineProvider"
import { DIFF_VIEW_URI_SCHEME } from "./integrations/editor/DiffViewProvider"
import { TerminalRegistry } from "./integrations/terminal/TerminalRegistry"
import { McpServerManager } from "./services/mcp/McpServerManager"
import { CodeIndexManager } from "./services/code-index/manager"
import { DGMService } from "./services/dgmService" // Added DGMService import
import { LLMAttentionPortalPanel } from "./panels/LLMAttentionPortalPanel"; // Added LLM Attention Portal Panel import
import { migrateSettings } from "./utils/migrateSettings"
import { API } from "./extension/api"

// Import Bao-Cline telemetry
import { BaoClineTelemetryGlobal } from "./telemetry/BaoClineTelemetry"

import {
	handleUri,
	registerCommands,
	registerCodeActions,
	registerTerminalActions,
	CodeActionProvider,
} from "./activate"
import { initializeI18n } from "./i18n"

/**
 * Built using https://github.com/microsoft/vscode-webview-ui-toolkit
 *
 * Inspired by:
 *  - https://github.com/microsoft/vscode-webview-ui-toolkit-samples/tree/main/default/weather-webview
 *  - https://github.com/microsoft/vscode-webview-ui-toolkit-samples/tree/main/frameworks/hello-world-react-cra
 */

let outputChannel: vscode.OutputChannel
let extensionContext: vscode.ExtensionContext
let baoClineTelemetry: any // Bao-Cline telemetry instance

// This method is called when your extension is activated.
// Your extension is activated the very first time the command is executed.
export async function activate(context: vscode.ExtensionContext) {
	extensionContext = context
	outputChannel = vscode.window.createOutputChannel(Package.outputChannel)
	context.subscriptions.push(outputChannel)
	outputChannel.appendLine(`${Package.name} extension activated - ${JSON.stringify(Package)}`)

	// Initialize Bao-Cline telemetry to track where it's sent to die
	baoClineTelemetry = BaoClineTelemetryGlobal.initialize(Package.name, Package.version)
	baoClineTelemetry.trackExtensionLifecycle('activated')
	outputChannel.appendLine(`Bao-Cline telemetry initialized - tracking death locations`)

	// Migrate old settings to new
	await migrateSettings(context, outputChannel)

	// Initialize telemetry service.
	const telemetryService = TelemetryService.createInstance()

	try {
		telemetryService.register(new PostHogTelemetryClient())
	} catch (error) {
		console.warn("Failed to register PostHogTelemetryClient:", error)
		// Track telemetry failure in Bao-Cline
		baoClineTelemetry.trackAPIFailure(
			`telemetry_init_${Date.now()}`,
			'server_error',
			`Failed to register PostHogTelemetryClient: ${error}`,
			0
		)
	}

	// Create logger for cloud services
	const cloudLogger = createDualLogger(createOutputChannelLogger(outputChannel))

	// Initialize Roo Code Cloud service.
	try {
		await CloudService.createInstance(context, {
			stateChanged: () => ClineProvider.getVisibleInstance()?.postStateToWebview(),
			log: cloudLogger,
		})
		baoClineTelemetry.trackEvent('cloud_service_initialized', {
			status: 'success'
		})
	} catch (error) {
		baoClineTelemetry.trackAPIFailure(
			`cloud_service_${Date.now()}`,
			'server_error',
			`Failed to initialize CloudService: ${error}`,
			0
		)
	}

	// Initialize i18n for internationalization support
	initializeI18n(context.globalState.get("language") ?? formatLanguage(vscode.env.language))

	// Initialize terminal shell execution handlers.
	TerminalRegistry.initialize()

	// Get default commands from configuration.
	const defaultCommands = vscode.workspace.getConfiguration(Package.name).get<string[]>("allowedCommands") || []

	// Initialize global state if not already set.
	if (!context.globalState.get("allowedCommands")) {
		context.globalState.update("allowedCommands", defaultCommands)
	}

	const contextProxy = await ContextProxy.getInstance(context)
	const codeIndexManager = CodeIndexManager.getInstance(context)

	try {
		await codeIndexManager?.initialize(contextProxy)
		baoClineTelemetry.trackEvent('code_index_initialized', {
			status: 'success'
		})
	} catch (error) {
		outputChannel.appendLine(
			`[CodeIndexManager] Error during background CodeIndexManager configuration/indexing: ${error.message || error}`,
		)
		baoClineTelemetry.trackContextFailure(
			`code_index_${Date.now()}`,
			'parse_error',
			`CodeIndexManager initialization failed: ${error.message || error}`
		)
	}

	const provider = new ClineProvider(context, outputChannel, "sidebar", contextProxy, codeIndexManager)
	TelemetryService.instance.setProvider(provider)

	if (codeIndexManager) {
		context.subscriptions.push(codeIndexManager)
	}

	context.subscriptions.push(
		vscode.window.registerWebviewViewProvider(ClineProvider.sideBarId, provider, {
			webviewOptions: { retainContextWhenHidden: true },
		}),
	)

	registerCommands({ context, outputChannel, provider })

	/**
	 * We use the text document content provider API to show the left side for diff
	 * view by creating a virtual document for the original content. This makes it
	 * readonly so users know to edit the right side if they want to keep their changes.
	 *
	 * This API allows you to create readonly documents in VSCode from arbitrary
	 * sources, and works by claiming an uri-scheme for which your provider then
	 * returns text contents. The scheme must be provided when registering a
	 * provider and cannot change afterwards.
	 *
	 * Note how the provider doesn't create uris for virtual documents - its role
	 * is to provide contents given such an uri. In return, content providers are
	 * wired into the open document logic so that providers are always considered.
	 *
	 * https://code.visualstudio.com/api/extension-guides/virtual-documents
	 */
	const diffContentProvider = new (class implements vscode.TextDocumentContentProvider {
		provideTextDocumentContent(uri: vscode.Uri): string {
			return Buffer.from(uri.query, "base64").toString("utf-8")
		}
	})()

	context.subscriptions.push(
		vscode.workspace.registerTextDocumentContentProvider(DIFF_VIEW_URI_SCHEME, diffContentProvider),
	)

	context.subscriptions.push(vscode.window.registerUriHandler({ handleUri }))

	// Register code actions provider.
	context.subscriptions.push(
		vscode.languages.registerCodeActionsProvider({ pattern: "**/*" }, new CodeActionProvider(), {
			providedCodeActionKinds: CodeActionProvider.providedCodeActionKinds,
		}),
	)

	registerCodeActions(context)
	registerTerminalActions(context)

	// Allows other extensions to activate once Roo is ready.
	vscode.commands.executeCommand(`${Package.name}.activationCompleted`)

	// DGM Service Integration with telemetry
	const dgmService = DGMService.getInstance();
	context.subscriptions.push(vscode.commands.registerCommand('bao-cline.startDgmService', () => {
		const requestId = `dgm_start_${Date.now()}`
		baoClineTelemetry.trackRequestStart(
			requestId,
			'Start DGM Service',
			'typescript',
			'DGM Service initialization'
		)
		
		try {
			dgmService.startDgmProcess();
			baoClineTelemetry.trackRequestSuccess(
				requestId,
				Date.now() - parseInt(requestId.split('_')[2]),
				0,
				'DGM Service started successfully'
			)
			vscode.window.showInformationMessage("Attempting to start DGM Service...");
		} catch (error) {
			baoClineTelemetry.trackRequestDeath(
				requestId,
				'dgm_service_failure',
				'Failed to start DGM Service',
				error.message || error.toString()
			)
		}
	}));

	context.subscriptions.push(vscode.commands.registerCommand('bao-cline.triggerDGMEcho', async () => {
		const requestId = `dgm_echo_${Date.now()}`
		
		// Check if dgmClientId is available, if not, dgmService is not fully ready.
		if (!dgmService.isDgmProcessRunning() || !dgmService.dgmClientId) {
			baoClineTelemetry.trackContextFailure(
				requestId,
				'file_not_found',
				'DGM Service not ready or DGM client not connected'
			)
			
			const startChoice = await vscode.window.showWarningMessage(
				'DGM Service not ready or DGM client not connected. Start it now?',
				{ modal: false },
				'Start DGM Service'
			);
			if (startChoice === 'Start DGM Service') {
				await vscode.commands.executeCommand('bao-cline.startDgmService');
				// Wait a bit for connection and ACK. A more robust solution would use an event or promise from DGMService.
				// Increased delay for PoC, and check status again.
				await new Promise(resolve => setTimeout(resolve, 3000));
				if (!dgmService.isDgmProcessRunning() || !dgmService.dgmClientId) {
					baoClineTelemetry.trackRequestDeath(
						requestId,
						'dgm_connection_failure',
						'DGM Service failed to start or connect',
						'Service not ready after startup attempt'
					)
					vscode.window.showErrorMessage('DGM Service failed to start or connect. Please check DGM Service output channel for details.');
					return;
				}
			} else {
				baoClineTelemetry.trackUserInteraction(
					requestId,
					'ignore',
					'User cancelled DGM Echo command'
				)
				vscode.window.showInformationMessage('DGM Echo command cancelled because DGM service is not active.');
				return;
			}
		}
		const inputText = await vscode.window.showInputBox({ prompt: "Enter text to echo via DGM" });
		if (inputText) {
			baoClineTelemetry.trackRequestStart(
				requestId,
				`Echo: ${inputText}`,
				'typescript',
				'DGM Echo command'
			)
			
			try {
				dgmService.sendToUpperEchoCommand(inputText);
				baoClineTelemetry.trackRequestSuccess(
					requestId,
					Date.now() - parseInt(requestId.split('_')[2]),
					inputText.length,
					'Echo command sent successfully'
				)
			} catch (error) {
				baoClineTelemetry.trackRequestDeath(
					requestId,
					'dgm_echo_failure',
					'Failed to send echo command',
					error.message || error.toString()
				)
			}
		} else {
			baoClineTelemetry.trackUserInteraction(
				requestId,
				'ignore',
				'User cancelled echo input'
			)
		}
	}));

	context.subscriptions.push(dgmService.onDgmResponse((response) => {
		baoClineTelemetry.trackEvent('dgm_response_received', {
			originalText: response.original_text,
			echoedText: response.echoed_text,
			responseType: 'echo'
		})
		vscode.window.showInformationMessage(`DGM Echo: ${response.echoed_text} (Original: ${response.original_text})`);
	}));

	// Track file operations
	context.subscriptions.push(
		vscode.workspace.onDidOpenTextDocument((document) => {
			baoClineTelemetry.trackFileOperation(
				'open',
				document.fileName,
				document.languageId,
				document.getText().length
			)
		})
	);

	context.subscriptions.push(
		vscode.workspace.onDidSaveTextDocument((document) => {
			baoClineTelemetry.trackFileOperation(
				'save',
				document.fileName,
				document.languageId,
				document.getText().length
			)
		})
	);

	// Track settings changes
	context.subscriptions.push(
		vscode.workspace.onDidChangeConfiguration((event) => {
			if (event.affectsConfiguration(Package.name)) {
				// Track settings changes for Bao-Cline
				baoClineTelemetry.trackSettingChange(
					'configuration_changed',
					'previous',
					'current'
				)
			}
		})
	);

	// Export telemetry data command
	context.subscriptions.push(
		vscode.commands.registerCommand('bao-cline.exportTelemetry', () => {
			const telemetryData = baoClineTelemetry.exportTelemetryData();
			const stats = baoClineTelemetry.getSessionStatistics();
			const deathStats = baoClineTelemetry.getDeathStatistics();
			
			outputChannel.appendLine('=== Bao-Cline Telemetry Data ===');
			outputChannel.appendLine(`Session ID: ${stats.sessionId}`);
			outputChannel.appendLine(`Total Requests: ${stats.totalRequests}`);
			outputChannel.appendLine(`Success Rate: ${stats.successRate.toFixed(2)}%`);
			outputChannel.appendLine(`Total Deaths: ${deathStats.totalDeaths}`);
			outputChannel.appendLine('Death Locations:');
			Object.entries(deathStats.deathLocations).forEach(([location, count]) => {
				outputChannel.appendLine(`  ${location}: ${count}`);
			});
			outputChannel.appendLine('Recent Deaths:');
			deathStats.recentDeaths.forEach(death => {
				outputChannel.appendLine(`  ${death.timestamp.toISOString()} - ${death.location}: ${death.reason}`);
			});
			
			vscode.window.showInformationMessage('Bao-Cline telemetry data exported to output channel');
		})
	);

	// Track extension activation completion
	baoClineTelemetry.trackEvent('extension_activation_completed', {
		duration: Date.now().toString()
	})

	// LLM Attention Portal Command
	context.subscriptions.push(vscode.commands.registerCommand('bao-cline.showLlmAttentionPortal', () => {
		LLMAttentionPortalPanel.createOrShow(context.extensionUri);
	  }));
	// End LLM Attention Portal Command

	// Implements the `RooCodeAPI` interface.
	const socketPath = process.env.ROO_CODE_IPC_SOCKET_PATH
	const enableLogging = typeof socketPath === "string"

	// Watch the core files and automatically reload the extension host.
	if (process.env.NODE_ENV === "development") {
		const pattern = "**/*.ts"

		const watchPaths = [
			{ path: context.extensionPath, name: "extension" },
			{ path: path.join(context.extensionPath, "../packages/types"), name: "types" },
			{ path: path.join(context.extensionPath, "../packages/telemetry"), name: "telemetry" },
			{ path: path.join(context.extensionPath, "../packages/cloud"), name: "cloud" },
		]

		console.log(
			`♻️♻️♻️ Core auto-reloading is ENABLED. Watching for changes in: ${watchPaths.map(({ name }) => name).join(", ")}`,
		)

		watchPaths.forEach(({ path: watchPath, name }) => {
			const watcher = vscode.workspace.createFileSystemWatcher(new vscode.RelativePattern(watchPath, pattern))

			watcher.onDidChange((uri) => {
				console.log(`♻️ ${name} file changed: ${uri.fsPath}. Reloading host…`)
				vscode.commands.executeCommand("workbench.action.reloadWindow")
			})

			context.subscriptions.push(watcher)
		})
	}

	return new API(outputChannel, provider, socketPath, enableLogging)
}

// This method is called when your extension is deactivated.
export async function deactivate() {
	// Track extension deactivation in Bao-Cline telemetry
	if (baoClineTelemetry) {
		baoClineTelemetry.trackExtensionLifecycle('deactivated', 'normal')
		
		// Export final telemetry data
		const stats = baoClineTelemetry.getSessionStatistics();
		const deathStats = baoClineTelemetry.getDeathStatistics();
		
		outputChannel.appendLine('=== Bao-Cline Final Telemetry ===');
		outputChannel.appendLine(`Session Duration: ${stats.sessionDuration || 'unknown'}`);
		outputChannel.appendLine(`Total Requests: ${stats.totalRequests}`);
		outputChannel.appendLine(`Success Rate: ${stats.successRate.toFixed(2)}%`);
		outputChannel.appendLine(`Total Deaths: ${deathStats.totalDeaths}`);
		outputChannel.appendLine(`Most Common Death Location: ${Object.entries(deathStats.deathLocations).sort((a, b) => b[1] - a[1])[0]?.[0] || 'none'}`);
		
		// Dispose Bao-Cline telemetry
		BaoClineTelemetryGlobal.dispose();
	}
	
	outputChannel.appendLine(`${Package.name} extension deactivated`)
	await McpServerManager.cleanup(extensionContext)
	TelemetryService.instance.shutdown()
	TerminalRegistry.cleanup()
	DGMService.getInstance().dispose() // Added DGMService dispose
}

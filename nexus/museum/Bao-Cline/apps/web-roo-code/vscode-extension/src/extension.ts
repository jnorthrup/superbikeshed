import * as vscode from "vscode"
import axios from "axios"
import { spawn, ChildProcess } from "child_process"
import * as path from "path"

const BAO_CLINE_WEB_URL = "https://github.com/jnorthrup/Bao-Cline"

export function activate(context: vscode.ExtensionContext) {
	console.log("🐱 Bao-Cline extension is now active!")

	// Generate Code Command
	const generateCodeCommand = vscode.commands.registerCommand("bao-cline.generateCode", async () => {
		const editor = vscode.window.activeTextEditor
		if (!editor) {
			vscode.window.showWarningMessage("No active editor found")
			return
		}

		const selection = editor.selection
		const selectedText = editor.document.getText(selection)

		if (!selectedText.trim()) {
			vscode.window.showWarningMessage("Please select some text or place cursor in code")
			return
		}

		try {
			const prompt = await vscode.window.showInputBox({
				prompt: "What would you like to generate or modify?",
				placeHolder: 'e.g., "Add error handling", "Convert to async/await", "Add unit tests"',
			})

			if (!prompt) {
				return
			}

			vscode.window.showInformationMessage("🐱 Generating code with Bao-Cline...")

			const response = await generateCodeWithAPI(selectedText, prompt)

			if (response) {
				const action = await vscode.window.showInformationMessage(
					"Code generated successfully!",
					"Replace Selection",
					"Insert Below",
					"Show in New Tab",
				)

				switch (action) {
					case "Replace Selection":
						await editor.edit((editBuilder) => {
							editBuilder.replace(selection, response)
						})
						break
					case "Insert Below":
						const line = selection.end.line
						const position = new vscode.Position(line + 1, 0)
						await editor.edit((editBuilder) => {
							editBuilder.insert(position, "\n" + response + "\n")
						})
						break
					case "Show in New Tab":
						const doc = await vscode.workspace.openTextDocument({
							content: response,
							language: editor.document.languageId,
						})
						await vscode.window.showTextDocument(doc)
						break
				}
			}
		} catch (error) {
			const errorMessage = error instanceof Error ? error.message : String(error)
			console.error("Bao Cline extension error:", error)
			vscode.window.showErrorMessage(`Failed to generate code: ${errorMessage}`)
		}
	})

	// Aider Command Mode
	const aiderModeCommand = vscode.commands.registerCommand("bao-cline.aiderMode", async () => {
		const workspaceFolder = vscode.workspace.workspaceFolders?.[0]
		if (!workspaceFolder) {
			vscode.window.showErrorMessage("No workspace folder open")
			return
		}

		const prompt = await vscode.window.showInputBox({
			prompt: "Enter aider command (without 'aider' prefix)",
			placeHolder: 'e.g., "Add error handling to main.py", "--help", "--list-models"',
		})

		if (!prompt) {
			return
		}

		// Create terminal and run aider command
		const terminal = vscode.window.createTerminal({
			name: "Bao Cline Aider",
			cwd: workspaceFolder.uri.fsPath,
		})

		terminal.show()

		// Check if we have aider available, if not suggest installation
		try {
			await checkAiderInstallation(terminal, prompt)
		} catch (error) {
			vscode.window.showErrorMessage("Failed to run aider command. Make sure aider is installed.")
		}
	})

	// Open Web App Command
	const openWebAppCommand = vscode.commands.registerCommand("bao-cline.openWebApp", () => {
		vscode.env.openExternal(vscode.Uri.parse(BAO_CLINE_WEB_URL))
	})

	context.subscriptions.push(generateCodeCommand, aiderModeCommand, openWebAppCommand)
}

async function generateCodeWithAPI(code: string, prompt: string): Promise<string | null> {
	try {
		const response = await axios.post(
			`${BAO_CLINE_WEB_URL}/api/generate`,
			{
				code,
				prompt,
				source: "vscode-extension",
			},
			{
				headers: {
					"Content-Type": "application/json",
				},
				timeout: 30000,
			},
		)

		if (response.data && response.data.generatedCode) {
			return response.data.generatedCode
		}

		throw new Error("Invalid response format")
	} catch (error) {
		if (axios.isAxiosError(error)) {
			if (error.code === "ECONNABORTED") {
				throw new Error("Request timed out. Please try again.")
			}
			if (error.response?.status === 404) {
				vscode.window.showWarningMessage("API endpoint not found. Opening Bao Cline web app instead...")
				vscode.env.openExternal(vscode.Uri.parse(BAO_CLINE_WEB_URL))
				return null
			}
			throw new Error(`API Error: ${error.response?.status || "Unknown"}`)
		}
		throw error
	}
}

async function checkAiderInstallation(terminal: vscode.Terminal, command: string) {
	// First check if aider is available
	const checkProcess = spawn("which", ["aider"], { stdio: "pipe" })

	return new Promise<void>((resolve, reject) => {
		checkProcess.on("close", (code) => {
			if (code === 0) {
				// Aider is available, run the command
				terminal.sendText(`aider ${command}`)
				resolve()
			} else {
				// Aider not found, suggest installation
				vscode.window
					.showWarningMessage(
						"Aider not found. Would you like to install it?",
						"Install Aider",
						"Show Installation Instructions",
					)
					.then((selection) => {
						switch (selection) {
							case "Install Aider":
								terminal.sendText("pip install aider-chat")
								break
							case "Show Installation Instructions":
								vscode.env.openExternal(vscode.Uri.parse("https://aider.chat/docs/install.html"))
								break
						}
					})
				reject(new Error("Aider not found"))
			}
		})

		checkProcess.on("error", () => {
			reject(new Error("Failed to check aider installation"))
		})
	})
}

export function deactivate() {
	console.log("🐱 Bao-Cline extension deactivated")
}

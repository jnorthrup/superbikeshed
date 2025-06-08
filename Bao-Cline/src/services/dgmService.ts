import * as vscode from "vscode";
import * as cp from "child_process";
import * as path from "path";
import * as os from "os";
import { IpcServer } from "@roocode/ipc";
import {
    DGMTaskCommandName,
    IpcMessage,
    IpcMessageType,
    IpcOrigin,
    RooCodeEventName,
    dgmToUpperEchoResponsePayloadSchema,
    DGMToUpperEchoResponsePayload,
    TaskEvent
} from "@roocode/types";
import { LLMAttentionPortalPanel } from "../panels/LLMAttentionPortalPanel"; // Added import

// Helper to generate a unique socket path
function getUniqueSocketPath(): string {
  const randomName = Math.random().toString(36).substring(2, 15);
  if (os.platform() === "win32") {
    return `\\\\.\\pipe\\roo-dgm-${randomName}`;
  }
  // On Unix, use /tmp or similar. vscode.workspace.storagePath might be good if it's always available and user-specific
  // For PoC, os.tmpdir() should be fine.
  return path.join(os.tmpdir(), `roo-dgm-${randomName}.sock`);
}

// This type is manually defined based on the expected structure for the event payload
// It should align with dgmToUpperEchoResponsePayloadSchema from @roocode/types
// but is used here for the emitter type.
// The actual validation should happen using the Zod schema when data is received.
export type DGMEchoResponseEventPayload = DGMToUpperEchoResponsePayload;


export class DGMService {
  private static instance: DGMService;
  private dgmProcess: cp.ChildProcess | null = null;
  private ipcServer: IpcServer | null = null;
  public socketPath: string | null = null; // Made public for inspection if needed
  public dgmClientId: string | null = null;
  private outputChannel: vscode.OutputChannel;
  private onDgmResponseEmitter = new vscode.EventEmitter<DGMEchoResponseEventPayload>();
  private activeLogId: string | null = null; // For tracking the current command for logging

  public readonly onDgmResponse = this.onDgmResponseEmitter.event;

  private constructor() {
    this.outputChannel = vscode.window.createOutputChannel("DGM Service");
    this.outputChannel.appendLine("DGMService constructor called.");
  }

  public static getInstance(): DGMService {
    if (!DGMService.instance) {
      DGMService.instance = new DGMService();
    }
    return DGMService.instance;
  }

  public isDgmProcessRunning(): boolean {
    return this.dgmProcess !== null && !this.dgmProcess.killed;
  }

  public startDgmProcess(): void {
    if (this.isDgmProcessRunning()) {
      this.outputChannel.appendLine("DGM process already running.");
      // vscode.window.showInformationMessage("DGM process is already running.");
      return;
    }

    this.socketPath = getUniqueSocketPath();
    this.outputChannel.appendLine(`Generated socket path: ${this.socketPath}`);

    // Path to dgm_runner.py - this needs to be configured or discovered
    // For PoC, assume it's at a fixed relative path from extension root for dev
    // Assumes 'dgm' and 'Bao-Cline' (extension project) are sibling directories.
    // vscode.workspace.workspaceFolders?.[0].uri.fsPath gives the root of the *currently opened workspace*
    // If Bao-Cline is the workspace root, then '..' goes up one level.
    const workspaceRoot = vscode.workspace.workspaceFolders?.[0]?.uri.fsPath;
    if (!workspaceRoot) {
        vscode.window.showErrorMessage("Cannot determine workspace root to find DGM runner.");
        this.outputChannel.appendLine("Workspace root not found. Cannot start DGM runner.");
        return;
    }
    const dgmRunnerPath = path.resolve(workspaceRoot, "..", "dgm", "dgm_runner.py");

    const pythonPath = vscode.workspace.getConfiguration('python').get<string>('defaultInterpreterPath') ||
                       vscode.workspace.getConfiguration('python').get<string>('pythonPath') || // Older setting name
                       (os.platform() === "win32" ? "python" : "python3");


    this.outputChannel.appendLine(`Attempting to start DGM runner: ${pythonPath} "${dgmRunnerPath}" "${this.socketPath}"`);

    this.dgmProcess = cp.spawn(pythonPath, [dgmRunnerPath, this.socketPath], { stdio: ['pipe', 'pipe', 'pipe'], shell: os.platform() === 'win32' });

    this.dgmProcess.stdout?.on("data", (data) => {
      this.outputChannel.appendLine(`DGM stdout: ${data.toString().trim()}`);
    });
    this.dgmProcess.stderr?.on("data", (data) => {
      this.outputChannel.appendLine(`DGM stderr: ${data.toString().trim()}`);
    });
    this.dgmProcess.on("error", (err) => {
      this.outputChannel.appendLine(`Failed to start DGM process: ${err.message}`);
      vscode.window.showErrorMessage(`Failed to start DGM process: ${err.message}. Check DGM Service output channel.`);
      this.dgmProcess = null;
    });
    this.dgmProcess.on("close", (code, signal) => {
      this.outputChannel.appendLine(`DGM process exited with code ${code}, signal ${signal}`);
      this.dgmProcess = null;
      this.ipcServer?.stop();
      this.ipcServer = null;
      this.dgmClientId = null;
      if (this.activeLogId) {
        LLMAttentionPortalPanel.currentPanel?.updateLogEntry(this.activeLogId, {
          status: "failed",
          error: `DGM process exited with code ${code}, signal ${signal}.`
        });
        this.activeLogId = null;
      }
    });

    if (this.dgmProcess) {
      this.outputChannel.appendLine("DGM process potentially started. Setting up IPC server.");
      this.setupIpcServer();
    }
  }

  private setupIpcServer(): void {
    if (!this.socketPath) {
        this.outputChannel.appendLine("Socket path is null, cannot setup IPC server.");
        return;
    }
    this.ipcServer = new IpcServer(this.socketPath, (msg) => this.outputChannel.appendLine(`IPC Server Debug: ${msg}`));

    this.ipcServer.on(IpcMessageType.Connect, (clientId) => {
      this.outputChannel.appendLine(`DGM client connected: ${clientId}`);
      this.dgmClientId = clientId;
      // Server sends ACK automatically in IpcServer implementation.
    });

    this.ipcServer.on(IpcMessageType.Disconnect, (clientId) => {
      this.outputChannel.appendLine(`DGM client disconnected: ${clientId}`);
      if (this.dgmClientId === clientId) {
        this.dgmClientId = null;
        if (this.activeLogId) {
          LLMAttentionPortalPanel.currentPanel?.updateLogEntry(this.activeLogId, {
            status: "failed",
            error: "DGM client disconnected before response.",
          });
          this.activeLogId = null;
        }
      }
    });

    this.ipcServer.on(IpcMessageType.TaskEvent, (clientId, taskEvent: TaskEvent) => {
        this.outputChannel.appendLine(`Received TaskEvent from DGM client ${clientId}: ${JSON.stringify(taskEvent)}`);
        if (taskEvent.eventName === RooCodeEventName.DGMEchoResponse) {
            if (!this.activeLogId) {
                this.outputChannel.appendLine("Received DGMEchoResponse but no activeLogId found.");
                // Optionally, still fire the event for other listeners not related to the portal
                // this.onDgmResponseEmitter.fire(parsedPayload);
                return;
            }
            try {
                const validationResult = dgmToUpperEchoResponsePayloadSchema.safeParse(taskEvent.payload[0]);
                if (validationResult.success) {
                    const responsePayload = validationResult.data;
                    this.outputChannel.appendLine(`Parsed DGM Echo Response Payload: ${JSON.stringify(responsePayload)}`);
                    LLMAttentionPortalPanel.currentPanel?.updateLogEntry(this.activeLogId, {
                        status: "completed",
                        responsePayload: responsePayload,
                    });
                    this.onDgmResponseEmitter.fire(responsePayload);
                } else {
                    this.outputChannel.appendLine(`Failed to parse DGMEchoResponse payload: ${validationResult.error.toString()}`);
                    LLMAttentionPortalPanel.currentPanel?.updateLogEntry(this.activeLogId, {
                        status: "failed",
                        error: "Failed to parse DGMEchoResponse: " + validationResult.error.toString(),
                    });
                    vscode.window.showWarningMessage("Received invalid DGMEchoResponse from DGM.");
                }
            } catch (error) {
                this.outputChannel.appendLine(`Error processing DGMEchoResponse: ${error}`);
                LLMAttentionPortalPanel.currentPanel?.updateLogEntry(this.activeLogId, {
                    status: "failed",
                    error: `Error processing DGMEchoResponse: ${error.message || error}`,
                });
            } finally {
                this.activeLogId = null; // Clear active log ID after processing
            }
        }
    });

    this.ipcServer.listen().catch(err => {
        this.outputChannel.appendLine(`Error starting IPC Server: ${err.message}`);
        vscode.window.showErrorMessage(`IPC Server could not listen: ${err.message}`);
    });
    this.outputChannel.appendLine(`IPC Server setup and attempting to listen on ${this.socketPath}`);
  }

  public sendToUpperEchoCommand(text: string): void {
    if (!this.dgmClientId || !this.ipcServer || !this.isDgmProcessRunning()) {
      vscode.window.showErrorMessage("DGM service not ready or DGM client not connected.");
      this.outputChannel.appendLine(`DGM service not ready for SendToUpperEchoCommand. ClientID: ${this.dgmClientId}, ProcessRunning: ${this.isDgmProcessRunning()}`);
      return;
    }

    const dgmCommandPayload = {
        commandName: DGMTaskCommandName.ToUpperEcho,
        data: { text_to_echo: text },
    };

    const messageToDgm: IpcMessage = {
        type: IpcMessageType.TaskCommand,
        origin: IpcOrigin.Server,
        data: dgmCommandPayload,
    };

    this.outputChannel.appendLine(`Sending DGMToUpperEchoCommand to client ${this.dgmClientId}: ${JSON.stringify(messageToDgm)}`);

    // Log the start of the interaction
    if (LLMAttentionPortalPanel.currentPanel) {
        this.activeLogId = LLMAttentionPortalPanel.currentPanel.addLogEntry({
            command: DGMTaskCommandName.ToUpperEcho,
            requestPayload: dgmCommandPayload.data, // Log the 'data' part of the command
            status: "pending",
        });
    } else {
        this.activeLogId = null; // Panel not open, no log ID
    }

    try {
        this.ipcServer.send(this.dgmClientId, messageToDgm);
    } catch (error) {
        this.outputChannel.appendLine(`Error sending command to DGM client: ${error}`);
        vscode.window.showErrorMessage(`Error sending command to DGM: ${error.message}`);
        if (this.activeLogId && LLMAttentionPortalPanel.currentPanel) {
            LLMAttentionPortalPanel.currentPanel.updateLogEntry(this.activeLogId, {
                status: "failed",
                error: `Failed to send command: ${error.message || error}`,
            });
            this.activeLogId = null; // Clear active log ID as the command failed to send
        }
    }
  }

  public dispose() {
    this.outputChannel.appendLine("Disposing DGMService.");
    if (this.dgmProcess) {
      if (os.platform() === "win32") { // On Windows, taskkill is more reliable for spawned processes
        cp.exec(`taskkill /PID ${this.dgmProcess.pid} /F /T`);
      } else {
        this.dgmProcess.kill("SIGTERM"); // Send SIGTERM first
        // Set a timeout to send SIGKILL if it doesn't terminate
        const killTimeout = setTimeout(() => {
          if (this.dgmProcess && !this.dgmProcess.killed) {
            this.outputChannel.appendLine("DGM process did not terminate with SIGTERM, sending SIGKILL.");
            this.dgmProcess.kill("SIGKILL");
          }
        }, 2000); // 2 seconds to terminate gracefully
        this.dgmProcess.on('close', () => clearTimeout(killTimeout));
      }
      this.dgmProcess = null;
    }
    if (this.ipcServer) {
      this.ipcServer.stop();
      this.ipcServer = null;
    }
    this.socketPath = null;
    this.dgmClientId = null;
    if (this.activeLogId && LLMAttentionPortalPanel.currentPanel) {
        LLMAttentionPortalPanel.currentPanel.updateLogEntry(this.activeLogId, {
            status: "failed",
            error: "DGMService disposed during active command.",
        });
        this.activeLogId = null;
    }
    this.onDgmResponseEmitter.dispose();
    this.outputChannel.dispose();
  }
}

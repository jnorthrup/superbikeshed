// File: Bao-Cline/src/panels/LLMAttentionPortalPanel.ts
import * as vscode from "vscode";
import path from "path";

export interface DGMInteractionLog { // Exporting for use in DGMService if needed for typing
  id: string;
  timestamp: string;
  command: string;
  requestPayload: unknown;
  responsePayload?: unknown;
  error?: string;
  status: "pending" | "completed" | "failed";
}

export interface DGMSummaryMetrics {
  cyclesCompleted: number;
  successfulActions: number;
  failedActions: number;
  totalProcessingTime: number; // e.g., in seconds
  averageProcessingTimePerCycle: number;
}

export class LLMAttentionPortalPanel {
  public static currentPanel: LLMAttentionPortalPanel | undefined;
  private readonly panel: vscode.WebviewPanel;
  private readonly extensionUri: vscode.Uri;
  private disposables: vscode.Disposable[] = [];
  private interactionLogs: DGMInteractionLog[] = [];
  private static nextLogId = 1;

  private constructor(panel: vscode.WebviewPanel, extensionUri: vscode.Uri) {
    this.panel = panel;
    this.extensionUri = extensionUri;

    this.panel.onDidDispose(() => this.dispose(), null, this.disposables);

    this.panel.webview.onDidReceiveMessage(
      (message) => {
        if (message.command === 'ready') {
            this.updateWebviewContent(); // Send initial data once webview is ready
        }
        // Handle other messages from the webview if any (e.g., refresh, clear)
      },
      null,
      this.disposables
    );
    // Initial content set here or after webview signals readiness
    this.updateWebviewContent();
  }

  public static createOrShow(extensionUri: vscode.Uri) {
    const column = vscode.window.activeTextEditor
      ? vscode.window.activeTextEditor.viewColumn
      : undefined;

    if (LLMAttentionPortalPanel.currentPanel) {
      LLMAttentionPortalPanel.currentPanel.panel.reveal(column);
      return;
    }

    const panel = vscode.window.createWebviewPanel(
      "llmAttentionPortal",
      "LLM Attention Portal",
      column || vscode.ViewColumn.One,
      {
        enableScripts: true,
        // Define localResourceRoots more dynamically if webview-ui/dist is not fixed
        localResourceRoots: [
            vscode.Uri.joinPath(extensionUri, "webview-ui", "dist"),
            vscode.Uri.joinPath(extensionUri, "webview-ui", "assets") // If assets are separate
        ],
      }
    );

    LLMAttentionPortalPanel.currentPanel = new LLMAttentionPortalPanel(panel, extensionUri);
  }

  // Method to allow DGMService to add a new log entry, returns the ID of the new log
  public addLogEntry(logData: Partial<DGMInteractionLog> & { command: string; requestPayload: unknown }): string {
    const newLog: DGMInteractionLog = {
      id: (LLMAttentionPortalPanel.nextLogId++).toString(),
      timestamp: new Date().toISOString(),
      status: "pending",
      ...logData,
    };
    this.interactionLogs.unshift(newLog); // Add to the beginning for chronological display (newest first)
    this.updateWebviewContent();
    return newLog.id;
  }

  // Method to allow DGMService to update an existing log entry
  public updateLogEntry(logId: string, updates: Partial<Omit<DGMInteractionLog, "id" | "timestamp" | "command" | "requestPayload">>) {
    const log = this.interactionLogs.find(l => l.id === logId);
    if (log) {
      Object.assign(log, updates);
      this.updateWebviewContent();
    } else {
      console.warn(`LLMAttentionPortalPanel: Log with ID ${logId} not found for update.`);
    }
  }

  // Public method to post updated logs to the webview, if it's listening
  public postMessageToWebview(message: any) {
    if (this.panel && this.panel.webview) {
        this.panel.webview.postMessage(message);
    }
  }

  // Method to update DGM summary metrics in the webview
  public updateMetrics(metrics: DGMSummaryMetrics) {
    this.postMessageToWebview({ type: 'updateDgmMetrics', payload: metrics });
  }

  private updateWebviewContent() {
    if (this.panel && this.panel.webview) {
        this.panel.webview.html = this.getHtmlForWebview(this.panel.webview);
        // Instead of embedding all data in HTML, post it as a message
        // This is better for larger datasets and updates
        this.postMessageToWebview({ type: 'updateLogs', payload: this.interactionLogs });
        this.postMessageToWebview({ type: 'updateDgmMetrics', payload: { cyclesCompleted: 0, successfulActions: 0, failedActions: 0, totalProcessingTime: 0, averageProcessingTimePerCycle: 0 } });
    }
  }

  private getHtmlForWebview(webview: vscode.Webview): string {
    // Note: In a real Vite setup, 'main.js' and 'main.css' might have hashes in their filenames in production builds.
    // This pathing needs to align with the actual output of the Vite build process for the webview-ui.
    const scriptUri = webview.asWebviewUri(
      vscode.Uri.joinPath(this.extensionUri, "webview-ui", "dist", "assets", "main.js") // Common Vite output
    );
    const stylesUri = webview.asWebviewUri(
      vscode.Uri.joinPath(this.extensionUri, "webview-ui", "dist", "assets", "main.css") // Common Vite output
    );
     // CSP to allow loading scripts and styles
    const nonce = getNonce(); // Helper function to generate nonce

    return `<!DOCTYPE html>
      <html lang="en">
      <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src ${webview.cspSource} 'unsafe-inline'; script-src 'nonce-${nonce}'; img-src ${webview.cspSource} data:;">
        <link href="${stylesUri}" rel="stylesheet">
        <title>LLM Attention Portal</title>
      </head>
      <body>
        <div id="root"></div>
        <script nonce="${nonce}">
          // Pass initial data to the webview, or signal readiness
          const vscode = acquireVsCodeApi();
          // Instead of window.initialData, post a message when the script is ready to receive data
          // Or, the React app can send a 'ready' message to the extension.
          // vscode.postMessage({ command: 'ready' }); // React app should do this
        </script>
        <script type="module" nonce="${nonce}" src="${scriptUri}"></script>
      </body>
      </html>`;
  }

  public dispose() {
    LLMAttentionPortalPanel.currentPanel = undefined;
    this.panel.dispose();
    while (this.disposables.length) {
      const x = this.disposables.pop();
      if (x) {
        x.dispose();
      }
    }
  }
}

function getNonce() {
	let text = '';
	const possible = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
	for (let i = 0; i < 32; i++) {
		text += possible.charAt(Math.floor(Math.random() * possible.length));
	}
	return text;
}

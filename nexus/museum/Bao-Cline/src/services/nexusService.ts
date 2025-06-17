import * as vscode from 'vscode';

// Define NexusEditorInsight locally for now, assuming it will be moved to nexusInsightDecorator.ts
export interface NexusEditorInsight {
    lineNumber: number; // Line number (1-based) for the gutter icon
    message: string;    // Short message for the gutter hover, or main part of hover
    detailedMessage?: string; // Optional longer message for the hover details (can be Markdown)
    severity?: 'info' | 'warning' | 'error'; // To style the icon/hover differently
    source?: string; // e.g., "Nexus Analyzer"
}

// Placeholder for the actual interface/type of the Nexus Agent client or bridge
interface INexusAgent {
    getChatViewInsights(documentContext: string): Promise<string[]>;
    getEditorInsights(documentUri: string /* vscode.Uri.toString() */): Promise<NexusEditorInsight[]>;
    // Potentially other methods for configuration, actions, etc.
}

export class NexusService {
    private static instance: NexusService;
    private outputChannel: vscode.OutputChannel;
    private nexusAgent: INexusAgent | null = null; // Placeholder for the actual Nexus agent instance or client

    private _onDidReceiveNexusEditorInsights = new vscode.EventEmitter<[vscode.Uri, NexusEditorInsight[]]>();
    public readonly onDidReceiveNexusEditorInsights: vscode.Event<[vscode.Uri, NexusEditorInsight[]]> = this._onDidReceiveNexusEditorInsights.event;

    private _onDidReceiveChatViewInsights = new vscode.EventEmitter<string[]>();
    public readonly onDidReceiveChatViewInsights: vscode.Event<string[]> = this._onDidReceiveChatViewInsights.event;


    private constructor() {
        this.outputChannel = vscode.window.createOutputChannel("Nexus Service");
        this.outputChannel.appendLine("NexusService constructor called.");
        // In a real scenario, initialization of nexusAgent (connection, loading library) would happen here or in an async init method.
        // For now, we can simulate a dummy agent for testing data flow.
        this.nexusAgent = this.getDummyNexusAgent();
    }

    public static getInstance(): NexusService {
        if (!NexusService.instance) {
            NexusService.instance = new NexusService();
        }
        return NexusService.instance;
    }

    // Simulate initializing or getting the Nexus agent
    private getDummyNexusAgent(): INexusAgent {
        this.outputChannel.appendLine("Using Dummy Nexus Agent.");
        return {
            getChatViewInsights: async (documentContext: string): Promise<string[]> => {
                this.outputChannel.appendLine(`DummyNexusAgent: getChatViewInsights called with context: ${documentContext.substring(0, 100)}...`);
                // Simulate async delay
                await new Promise(resolve => setTimeout(resolve, 500));
                return [
                    "Dummy Insight: Project structure looks good.",
                    `Dummy Insight: Consider using 'async/await' for '${documentContext.length % 10}' operations.`,
                    "Dummy Insight: Remember to add more tests for critical components.",
                ];
            },
            getEditorInsights: async (documentUriString: string): Promise<NexusEditorInsight[]> => {
                this.outputChannel.appendLine(`DummyNexusAgent: getEditorInsights called for URI: ${documentUriString}`);
                // Simulate async delay
                await new Promise(resolve => setTimeout(resolve, 500));
                const insights: NexusEditorInsight[] = [];
                if (documentUriString.endsWith('.ts')) { // Only provide insights for .ts files for dummy
                    insights.push({
                        lineNumber: 5, // Example line number
                        message: "Consider adding a JSDoc comment to this function.",
                        severity: 'info',
                        source: 'Nexus Dummy Analyzer'
                    });
                    insights.push({
                        lineNumber: 10,
                        message: "This complex logic could be simplified.",
                        detailedMessage: "Splitting this function into smaller parts might improve readability and maintainability.",
                        severity: 'warning',
                        source: 'Nexus Dummy Analyzer'
                    });
                }
                return insights;
            }
        };
    }

    // Method to be called by other parts of the extension (e.g., commands, event handlers)
    public async fetchAndDistributeInsights(editor: vscode.TextEditor | undefined): Promise<void> {
        if (!editor) {
            this.outputChannel.appendLine("No active editor to fetch insights for.");
            return;
        }

        if (!this.nexusAgent) {
            this.outputChannel.appendLine("Nexus agent not available.");
            vscode.window.showWarningMessage("Nexus agent is not available.");
            return;
        }

        const document = editor.document;
        this.outputChannel.appendLine(`Fetching insights for: ${document.uri.toString()}`);

        try {
            // Fetch and distribute editor insights
            const editorInsights = await this.nexusAgent.getEditorInsights(document.uri.toString());
            this.outputChannel.appendLine(`Received ${editorInsights.length} editor insights.`);
            this._onDidReceiveNexusEditorInsights.fire([document.uri, editorInsights]);

            // Fetch and distribute chat view insights (using full document text as context for dummy)
            const chatViewInsights = await this.nexusAgent.getChatViewInsights(document.getText());
            this.outputChannel.appendLine(`Received ${chatViewInsights.length} chat view insights.`);
            // To send to ChatView, we'd typically use the WebviewView.webview.postMessage method.
            // This requires access to the ChatView's webview instance.
            // For now, we'll just emit an event. The actual posting will be handled by the part of the code that owns the webview.
            this.outputChannel.appendLine(`NexusService: Emitting _onDidReceiveChatViewInsights. Actual postMessage to ChatView is handled by the extension part owning the panel.`);
            this._onDidReceiveChatViewInsights.fire(chatViewInsights);
            // The previous direct call to postMessage or a similar mechanism from here is removed
            // as this service shouldn't directly know about specific webview panels.

        } catch (error) {
            this.outputChannel.appendLine(`Error fetching insights: ${error}`);
            if (error instanceof Error) {
                vscode.window.showErrorMessage(`Failed to fetch Nexus insights: ${error.message}`);
            } else {
                vscode.window.showErrorMessage(`Failed to fetch Nexus insights: ${String(error)}`);
            }
        }
    }

    public dispose(): void {
        this.outputChannel.appendLine("Disposing NexusService.");
        this._onDidReceiveNexusEditorInsights.dispose();
        this._onDidReceiveChatViewInsights.dispose();
        this.outputChannel.dispose();
        // Any other cleanup for nexusAgent
        // Conceptual integration in extension.ts activate function:
        //
        // // Assuming 'chatPanel' is the vscode.WebviewPanel instance for ChatView
        // // and 'nexusService' is the NexusService.getInstance()
        //
        // // 1. Listen for ChatView readiness
        // chatPanel.webview.onDidReceiveMessage(message => {
        //   if (message.type === 'chatViewReadyForNexusInsights') {
        //     // Trigger initial insight load for the active editor (if any)
        //     nexusService.fetchAndDistributeInsights(vscode.window.activeTextEditor);
        //   }
        // });
        //
        // // 2. Forward insights from NexusService to ChatView webview
        // nexusService.onDidReceiveChatViewInsights(insights => {
        //   if (chatPanel && chatPanel.visible) {
        //     chatPanel.webview.postMessage({
        //       type: 'updateNexusChatInsights',
        //       payload: insights
        //     });
        //   }
        // });
        //
        // // 3. Optionally, re-fetch insights when active editor changes
        // vscode.window.onDidChangeActiveTextEditor(editor => {
        //    if (editor && chatPanel && chatPanel.visible) { // Check if chat panel is visible
        //        nexusService.fetchAndDistributeInsights(editor);
        //    }
        // });
    }
}

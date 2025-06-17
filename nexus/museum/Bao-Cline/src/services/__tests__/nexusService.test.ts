import * as vscode from 'vscode';
import { NexusService, NexusEditorInsight } from '../nexusService'; // Assuming NexusEditorInsight is exported from nexusService.ts for now

// --- Mocks ---
const mockOutputChannel = {
  appendLine: jest.fn(),
  dispose: jest.fn(),
  show: jest.fn(), // Added to satisfy vscode.OutputChannel interface
  clear: jest.fn(), // Added
  hide: jest.fn(), // Added
  name: 'mockNexusOutputChannel', // Added
};

// Individual mocks for emitter disposals to check them separately
const mockEditorInsightsEmitterDispose = jest.fn();
const mockChatInsightsEmitterDispose = jest.fn();

let editorInsightsEmitterInstance: any;
let chatInsightsEmitterInstance: any;

jest.mock('vscode', () => {
  // Store the actual constructor
  const actualVSCode = jest.requireActual('vscode');
  return {
    ...actualVSCode, // Import and retain default behavior for non-mocked parts
    window: {
      createOutputChannel: jest.fn(() => mockOutputChannel),
      showWarningMessage: jest.fn(),
      showErrorMessage: jest.fn(),
      activeTextEditor: undefined, // Default, can be set in tests
    },
    EventEmitter: jest.fn().mockImplementation(() => {
      // This mock will be used for both emitters. We differentiate them by assigning their dispose to specific mocks.
      const newEmitter = {
        event: jest.fn(),
        fire: jest.fn(),
        dispose: jest.fn(), // Generic dispose
      };
      // This is a bit of a hack to distinguish. A better way would be if NexusService took emitter instances in constructor for testing.
      if (!editorInsightsEmitterInstance) {
        editorInsightsEmitterInstance = newEmitter;
        newEmitter.dispose = mockEditorInsightsEmitterDispose;
      } else if (!chatInsightsEmitterInstance) {
        chatInsightsEmitterInstance = newEmitter;
        newEmitter.dispose = mockChatInsightsEmitterDispose;
      }
      return newEmitter;
    }),
    Uri: { // Simple Uri mock
      parse: jest.fn(str => ({ toString: () => str, fsPath: str, with: jest.fn(),toJSON: jest.fn() })),
      file: jest.fn(str => ({ toString: () => str, fsPath: str, with: jest.fn(),toJSON: jest.fn() })),
    },
  };
});


describe('NexusService', () => {
  let nexusServiceInstance: NexusService;
  // Hold onto the dummy agent methods to spy on them
  let dummyAgentGetEditorInsightsSpy: jest.SpyInstance;
  let dummyAgentGetChatViewInsightsSpy: jest.SpyInstance;

  const mockEditor: vscode.TextEditor = {
    document: {
      uri: vscode.Uri.parse('file:///test/document.ts'),
      getText: jest.fn(() => 'This is the document text.'),
      fileName: 'document.ts',
      isUntitled: false,
      languageId: 'typescript',
      version: 1,
      isDirty: false,
      isClosed: false,
      save: jest.fn(),
      eol: 1, // vscode.EndOfLine.LF
      lineCount: 5,
      lineAt: jest.fn(),
      offsetAt: jest.fn(),
      positionAt: jest.fn(),
      validateRange: jest.fn(),
      validatePosition: jest.fn(),
      getWordRangeAtPosition: jest.fn(),
    },
    selection: new vscode.Selection(new vscode.Position(0,0), new vscode.Position(0,0)),
    selections: [],
    visibleRanges: [],
    options: {},
    viewColumn: 1,
    edit: jest.fn(),
    insertSnippet: jest.fn(),
    setDecorations: jest.fn(),
    revealRange: jest.fn(),
    show: jest.fn(),
    hide: jest.fn(),
    dispose: jest.fn()
  } as unknown as vscode.TextEditor;


  beforeEach(() => {
    jest.clearAllMocks();
    // Reset singleton instance for test isolation
    (NexusService as any).instance = undefined;
    editorInsightsEmitterInstance = null; // Reset for vscode.EventEmitter mock
    chatInsightsEmitterInstance = null;   // Reset for vscode.EventEmitter mock
    nexusServiceInstance = NexusService.getInstance();

    // Spy on the methods of the actual dummyAgent instance used by the service.
    // This requires nexusAgent to be accessible, or spy on prototype if it were a class.
    // Since getDummyNexusAgent creates a new object, we access the internal one.
    if (nexusServiceInstance['nexusAgent']) {
      dummyAgentGetEditorInsightsSpy = jest.spyOn(nexusServiceInstance['nexusAgent'], 'getEditorInsights');
      dummyAgentGetChatViewInsightsSpy = jest.spyOn(nexusServiceInstance['nexusAgent'], 'getChatViewInsights');
    }
  });

  afterEach(() => {
    nexusServiceInstance.dispose();
  });

  describe('Initialization', () => {
    test('getInstance returns a singleton instance', () => {
      const instance1 = NexusService.getInstance();
      const instance2 = NexusService.getInstance();
      expect(instance1).toBe(instance2);
    });

    test('constructor initializes the output channel', () => {
      expect(vscode.window.createOutputChannel).toHaveBeenCalledWith('Nexus Service');
      expect(nexusServiceInstance['outputChannel']).toBe(mockOutputChannel);
    });

    test('constructor calls getDummyNexusAgent and populates nexusAgent', () => {
      // getDummyNexusAgent is private, so we check its effect:
      expect(nexusServiceInstance['nexusAgent']).not.toBeNull();
      // We can also check if the output channel logged its use
      expect(mockOutputChannel.appendLine).toHaveBeenCalledWith('Using Dummy Nexus Agent.');
    });
  });

  describe('fetchAndDistributeInsights', () => {
    let onEditorInsightsListener: jest.Mock;
    let onChatInsightsListener: jest.Mock;

    beforeEach(() => {
      onEditorInsightsListener = jest.fn();
      onChatInsightsListener = jest.fn();
      nexusServiceInstance.onDidReceiveNexusEditorInsights(onEditorInsightsListener);
      nexusServiceInstance.onDidReceiveChatViewInsights(onChatInsightsListener);
    });

    test('Successful path: fetches and fires events for both editor and chat insights', async () => {
      await nexusServiceInstance.fetchAndDistributeInsights(mockEditor);

      expect(dummyAgentGetEditorInsightsSpy).toHaveBeenCalledWith(mockEditor.document.uri.toString());
      expect(dummyAgentGetChatViewInsightsSpy).toHaveBeenCalledWith(mockEditor.document.getText());

      expect(onEditorInsightsListener).toHaveBeenCalledTimes(1);
      const dummyEditorInsights = await nexusServiceInstance['nexusAgent']!.getEditorInsights(mockEditor.document.uri.toString()); // Re-fetch for comparison
      expect(onEditorInsightsListener).toHaveBeenCalledWith([mockEditor.document.uri, dummyEditorInsights]);


      expect(onChatInsightsListener).toHaveBeenCalledTimes(1);
      const dummyChatViewInsights = await nexusServiceInstance['nexusAgent']!.getChatViewInsights(mockEditor.document.getText()); // Re-fetch for comparison
      expect(onChatInsightsListener).toHaveBeenCalledWith(dummyChatViewInsights);


      expect(mockOutputChannel.appendLine).toHaveBeenCalledWith(`Fetching insights for: ${mockEditor.document.uri.toString()}`);
      expect(mockOutputChannel.appendLine).toHaveBeenCalledWith(expect.stringContaining('Received') && expect.stringContaining('editor insights'));
      expect(mockOutputChannel.appendLine).toHaveBeenCalledWith(expect.stringContaining('Received') && expect.stringContaining('chat view insights'));
    });

    test('No editor: logs and does not call agent or fire events', async () => {
      await nexusServiceInstance.fetchAndDistributeInsights(undefined);

      expect(mockOutputChannel.appendLine).toHaveBeenCalledWith('No active editor to fetch insights for.');
      expect(dummyAgentGetEditorInsightsSpy).not.toHaveBeenCalled();
      expect(dummyAgentGetChatViewInsightsSpy).not.toHaveBeenCalled();
      expect(onEditorInsightsListener).not.toHaveBeenCalled();
      expect(onChatInsightsListener).not.toHaveBeenCalled();
    });

    test('Nexus Agent not available: logs, shows warning, and does not call agent or fire events', async () => {
      nexusServiceInstance['nexusAgent'] = null;
      await nexusServiceInstance.fetchAndDistributeInsights(mockEditor);

      expect(mockOutputChannel.appendLine).toHaveBeenCalledWith('Nexus agent not available.');
      expect(vscode.window.showWarningMessage).toHaveBeenCalledWith('Nexus agent is not available.');
      expect(dummyAgentGetEditorInsightsSpy).not.toHaveBeenCalled();
      expect(dummyAgentGetChatViewInsightsSpy).not.toHaveBeenCalled();
      expect(onEditorInsightsListener).not.toHaveBeenCalled();
      expect(onChatInsightsListener).not.toHaveBeenCalled();
    });

    test('Error from getEditorInsights: logs, shows error, fires no editor event, but attempts chat insights', async () => {
      const errorMessage = 'Error from getEditorInsights';
      dummyAgentGetEditorInsightsSpy.mockRejectedValueOnce(new Error(errorMessage));

      await nexusServiceInstance.fetchAndDistributeInsights(mockEditor);

      expect(mockOutputChannel.appendLine).toHaveBeenCalledWith(`Error fetching insights: Error: ${errorMessage}`);
      expect(vscode.window.showErrorMessage).toHaveBeenCalledWith(`Failed to fetch Nexus insights: ${errorMessage}`);

      expect(onEditorInsightsListener).not.toHaveBeenCalled(); // Should not fire if error occurs before it

      // Current implementation has a single try/catch, so if getEditorInsights fails,
      // getChatViewInsights and its event will also not be processed.
      expect(dummyAgentGetChatViewInsightsSpy).not.toHaveBeenCalled();
      expect(onChatInsightsListener).not.toHaveBeenCalled();
    });


    test('Error from getChatViewInsights: logs, shows error, fires editor event but no chat event', async () => {
        const errorMessage = 'Error from getChatViewInsights';
        // Ensure getEditorInsights succeeds
        const dummyEditorInsights = [{ lineNumber: 1, message: 'Editor insight', source: 'test' }];
        dummyAgentGetEditorInsightsSpy.mockResolvedValueOnce(dummyEditorInsights);
        dummyAgentGetChatViewInsightsSpy.mockRejectedValueOnce(new Error(errorMessage));

        await nexusServiceInstance.fetchAndDistributeInsights(mockEditor);

        expect(mockOutputChannel.appendLine).toHaveBeenCalledWith(`Error fetching insights: Error: ${errorMessage}`);
        expect(vscode.window.showErrorMessage).toHaveBeenCalledWith(`Failed to fetch Nexus insights: ${errorMessage}`);

        // Editor insights should have been fired successfully
        expect(onEditorInsightsListener).toHaveBeenCalledTimes(1);
        expect(onEditorInsightsListener).toHaveBeenCalledWith([mockEditor.document.uri, dummyEditorInsights]);

        // Chat insights event should not have been fired
        expect(onChatInsightsListener).not.toHaveBeenCalled();
    });
  });

  describe('dispose', () => {
    test('disposes of event emitters and output channel', () => {
      nexusServiceInstance.dispose();

      expect(mockEditorInsightsEmitterDispose).toHaveBeenCalledTimes(1);
      expect(mockChatInsightsEmitterDispose).toHaveBeenCalledTimes(1);
      expect(mockOutputChannel.dispose).toHaveBeenCalledTimes(1);
    });
  });
});

import { DGMService } from '../dgmService';
import { LLMAttentionPortalPanel, DGMSummaryMetrics } from '../../panels/LLMAttentionPortalPanel';
import { IpcServer } from '@roo-code/ipc';
import * as cp from 'child_process';
import * as vscode from 'vscode';
import { IpcMessageType, RooCodeEventName, TaskEvent, dgmToUpperEchoResponsePayloadSchema } from '@roo-code/types';

// --- Mocks ---
const mockOutputChannel = {
  appendLine: jest.fn(),
  dispose: jest.fn(),
  show: jest.fn(),
  clear: jest.fn(),
  hide: jest.fn(),
  name: 'mockOutputChannel',
};

const mockEventEmitter = {
  event: jest.fn(),
  fire: jest.fn(),
  dispose: jest.fn(),
};

jest.mock('vscode', () => ({
  window: {
    createOutputChannel: jest.fn(() => mockOutputChannel),
    showErrorMessage: jest.fn(),
    showWarningMessage: jest.fn(),
    showInformationMessage: jest.fn(),
  },
  EventEmitter: jest.fn(() => ({ ...mockEventEmitter })),
  workspace: {
    getConfiguration: jest.fn(() => ({
      get: jest.fn((key: string) => {
        if (key === 'python.defaultInterpreterPath' || key === 'python.pythonPath') {
          return 'python';
        }
        return undefined;
      }),
    })),
    workspaceFolders: [{ uri: { fsPath: '/mock/workspace' } }],
  },
  Uri: {
    file: jest.fn(path => ({ fsPath: path, scheme: 'file' })),
    joinPath: jest.fn((base, ...paths) => ({ ...base, fsPath: `${base.fsPath}/${paths.join('/')}`})),
  }
}));

const mockLlmAttentionPortalPanel = {
  updateMetrics: jest.fn(),
  addLogEntry: jest.fn(() => 'mockLogId123'),
  updateLogEntry: jest.fn(),
};

jest.mock('../../panels/LLMAttentionPortalPanel', () => ({
  LLMAttentionPortalPanel: {
    currentPanel: mockLlmAttentionPortalPanel, // Initially set, can be cleared in tests
  },
  // Exporting DGMSummaryMetrics for type usage if needed, though it's an interface
  DGMSummaryMetrics: jest.fn(),
}));


let mockChildProcess: Partial<cp.ChildProcess>;
let mockIpcServerInstance: Partial<IpcServer>;
const mockIpcServerListeners = new Map<string, (...args: any[]) => void>();

jest.mock('child_process', () => ({
  spawn: jest.fn(() => mockChildProcess),
}));

jest.mock('@roo-code/ipc', () => ({
  IpcServer: jest.fn().mockImplementation(() => {
    mockIpcServerInstance = {
      on: jest.fn((event, listener) => {
        mockIpcServerListeners.set(event, listener);
      }),
      listen: jest.fn(() => Promise.resolve()),
      send: jest.fn(),
      stop: jest.fn(),
    };
    return mockIpcServerInstance;
  }),
}));

// Utility to simulate IPC client connection
function simulateIpcClientConnect(clientId = 'dgm-client-1') {
  const connectListener = mockIpcServerListeners.get(IpcMessageType.Connect);
  if (connectListener) {
    connectListener(clientId);
  } else {
    throw new Error('IPC Server "Connect" listener not found.');
  }
}

// Utility to simulate IPC client sending a message
function simulateIpcClientMessage(clientId: string, message: TaskEvent) {
    const messageListener = mockIpcServerListeners.get(IpcMessageType.TaskEvent);
    if (messageListener) {
        messageListener(clientId, message);
    } else {
        throw new Error('IPC Server "TaskEvent" listener not found.');
    }
}


describe('DGMService', () => {
  let dgmServiceInstance: DGMService;
  let originalDateNow: () => number;

  beforeEach(() => {
    jest.clearAllMocks();
    mockIpcServerListeners.clear();

    // Ensure currentPanel is set for each test, can be overridden if a test needs it to be null
    Object.defineProperty(LLMAttentionPortalPanel, 'currentPanel', {
        get: jest.fn(() => mockLlmAttentionPortalPanel),
        configurable: true,
    });


    mockChildProcess = {
      pid: 1234,
      stdout: { on: jest.fn() } as any,
      stderr: { on: jest.fn() } as any,
      on: jest.fn((event, listener) => {
        // Store listeners to be called manually
        if (!mockChildProcess.listeners) mockChildProcess.listeners = {};
        mockChildProcess.listeners[event] = listener;
      }) as any,
      kill: jest.fn(),
      killed: false,
    };

    // DGMService is a singleton, need to reset its instance for isolation if DGMService holds state.
    // This requires a way to reset the singleton, or careful test design.
    // For this example, we assume tests can run sequentially on the same instance,
    // or DGMService would need a static reset method for testing.
    // Let's fetch a fresh instance for each test for better isolation.
    DGMService['instance'] = undefined as any; // Reset singleton
    dgmServiceInstance = DGMService.getInstance();

    originalDateNow = Date.now;
  });

  afterEach(() => {
    dgmServiceInstance.dispose(); // Clean up service instance
    Date.now = originalDateNow; // Restore original Date.now
  });

  test('Initialization: metrics should be zero', () => {
    // Access private members for testing - this is generally discouraged but sometimes necessary for unit tests.
    // Alternatively, add a getter method in DGMService for metrics if preferred.
    expect(dgmServiceInstance['cyclesCompleted']).toBe(0);
    expect(dgmServiceInstance['successfulActions']).toBe(0);
    expect(dgmServiceInstance['failedActions']).toBe(0);
    expect(dgmServiceInstance['totalProcessingTimeSeconds']).toBe(0);
    expect(dgmServiceInstance['activeCommandStartTime']).toBeNull();
  });

  describe('Command Cycle Metrics', () => {
    let currentTime = 1000000000000; // Start with a fixed time

    beforeEach(() => {
        currentTime = 1000000000000;
        Date.now = jest.fn(() => currentTime);
        dgmServiceInstance.startDgmProcess(); // Start process and IPC server
        simulateIpcClientConnect('test-client-id');
    });

    test('Successful Command Cycle: updates metrics correctly', async () => {
        dgmServiceInstance.sendToUpperEchoCommand("hello");

        currentTime += 500; // Simulate 500ms processing time

        const mockResponsePayload = { echoed_text: "HELLO" };
        const taskEvent: TaskEvent = {
            eventName: RooCodeEventName.DGMEchoResponse,
            payload: [mockResponsePayload],
        };
        simulateIpcClientMessage('test-client-id', taskEvent);

        expect(dgmServiceInstance['cyclesCompleted']).toBe(1);
        expect(dgmServiceInstance['successfulActions']).toBe(1);
        expect(dgmServiceInstance['failedActions']).toBe(0);
        expect(dgmServiceInstance['totalProcessingTimeSeconds']).toBe(0.5);

        expect(mockLlmAttentionPortalPanel.updateMetrics).toHaveBeenCalledTimes(1);
        const expectedMetrics: DGMSummaryMetrics = {
            cyclesCompleted: 1,
            successfulActions: 1,
            failedActions: 0,
            totalProcessingTime: 0.5,
            averageProcessingTimePerCycle: 0.5,
        };
        expect(mockLlmAttentionPortalPanel.updateMetrics).toHaveBeenCalledWith(expectedMetrics);
        expect(mockLlmAttentionPortalPanel.addLogEntry).toHaveBeenCalled();
        expect(mockLlmAttentionPortalPanel.updateLogEntry).toHaveBeenCalledWith('mockLogId123', expect.objectContaining({ status: 'completed' }));
    });

    test('Failed Command Cycle (Validation Error): updates metrics correctly', async () => {
        dgmServiceInstance.sendToUpperEchoCommand("invalid test");

        currentTime += 300; // Simulate 300ms processing time

        const invalidResponsePayload = { some_unexpected_field: "unexpected value" }; // Does not match schema
         const taskEvent: TaskEvent = {
            eventName: RooCodeEventName.DGMEchoResponse,
            payload: [invalidResponsePayload],
        };
        simulateIpcClientMessage('test-client-id', taskEvent);

        expect(dgmServiceInstance['cyclesCompleted']).toBe(1);
        expect(dgmServiceInstance['successfulActions']).toBe(0);
        expect(dgmServiceInstance['failedActions']).toBe(1);
        expect(dgmServiceInstance['totalProcessingTimeSeconds']).toBe(0.3);

        expect(mockLlmAttentionPortalPanel.updateMetrics).toHaveBeenCalledTimes(1);
        const expectedMetrics: DGMSummaryMetrics = {
            cyclesCompleted: 1,
            successfulActions: 0,
            failedActions: 1,
            totalProcessingTime: 0.3,
            averageProcessingTimePerCycle: 0.3,
        };
        expect(mockLlmAttentionPortalPanel.updateMetrics).toHaveBeenCalledWith(expectedMetrics);
        expect(mockLlmAttentionPortalPanel.addLogEntry).toHaveBeenCalled();
        expect(mockLlmAttentionPortalPanel.updateLogEntry).toHaveBeenCalledWith('mockLogId123', expect.objectContaining({ status: 'failed' }));
    });

    test('Failed Command Cycle (Process Exits Unexpectedly): updates metrics correctly', () => {
        dgmServiceInstance.sendToUpperEchoCommand("test process exit");
        expect(dgmServiceInstance['activeCommandStartTime']).not.toBeNull();
        expect(dgmServiceInstance['activeLogId']).toBe('mockLogId123');

        currentTime += 200; // Simulate 200ms before process crashes

        // Simulate process close
        const closeListener = mockChildProcess.on!.bind(mockChildProcess)('close' as any, 1, null); // code 1, no signal
        if(typeof closeListener === 'function') closeListener(1, null);


        expect(dgmServiceInstance['cyclesCompleted']).toBe(1);
        expect(dgmServiceInstance['successfulActions']).toBe(0);
        expect(dgmServiceInstance['failedActions']).toBe(1);
        expect(dgmServiceInstance['totalProcessingTimeSeconds']).toBe(0.2);
        expect(dgmServiceInstance['activeCommandStartTime']).toBeNull(); // Should be reset
        expect(dgmServiceInstance['activeLogId']).toBeNull(); // Should be reset


        expect(mockLlmAttentionPortalPanel.updateMetrics).toHaveBeenCalledTimes(1);
        const expectedMetrics: DGMSummaryMetrics = {
            cyclesCompleted: 1,
            successfulActions: 0,
            failedActions: 1,
            totalProcessingTime: 0.2,
            averageProcessingTimePerCycle: 0.2,
        };
        expect(mockLlmAttentionPortalPanel.updateMetrics).toHaveBeenCalledWith(expectedMetrics);
        expect(mockLlmAttentionPortalPanel.updateLogEntry).toHaveBeenCalledWith('mockLogId123', expect.objectContaining({ status: 'failed', error: expect.stringContaining('DGM process exited with code 1') }));
    });

    test('Dispose During Active Command: updates metrics correctly', () => {
        dgmServiceInstance.sendToUpperEchoCommand("test dispose");
        expect(dgmServiceInstance['activeCommandStartTime']).not.toBeNull();

        currentTime += 150; // Simulate 150ms before dispose

        dgmServiceInstance.dispose();

        expect(dgmServiceInstance['cyclesCompleted']).toBe(1);
        expect(dgmServiceInstance['successfulActions']).toBe(0);
        expect(dgmServiceInstance['failedActions']).toBe(1);
        expect(dgmServiceInstance['totalProcessingTimeSeconds']).toBe(0.15);

        expect(mockLlmAttentionPortalPanel.updateMetrics).toHaveBeenCalledTimes(1);
        const expectedMetrics: DGMSummaryMetrics = {
            cyclesCompleted: 1,
            successfulActions: 0,
            failedActions: 1,
            totalProcessingTime: 0.15,
            averageProcessingTimePerCycle: 0.15,
        };
        expect(mockLlmAttentionPortalPanel.updateMetrics).toHaveBeenCalledWith(expectedMetrics);
        expect(mockLlmAttentionPortalPanel.updateLogEntry).toHaveBeenCalledWith('mockLogId123', expect.objectContaining({ status: 'failed', error: 'DGMService disposed during active command.' }));
    });

    test('Average Processing Time Calculation: correct after multiple cycles', async () => {
        // Cycle 1
        dgmServiceInstance.sendToUpperEchoCommand("hello 1");
        currentTime += 200; // 0.2s
        simulateIpcClientMessage('test-client-id', { eventName: RooCodeEventName.DGMEchoResponse, payload: [{ echoed_text: "HELLO 1" }] });

        let expectedMetrics: DGMSummaryMetrics = {
            cyclesCompleted: 1, successfulActions: 1, failedActions: 0, totalProcessingTime: 0.2, averageProcessingTimePerCycle: 0.2,
        };
        expect(mockLlmAttentionPortalPanel.updateMetrics).toHaveBeenLastCalledWith(expectedMetrics);

        // Cycle 2
        dgmServiceInstance.sendToUpperEchoCommand("hello 2");
        currentTime += 400; // 0.4s (total time = 0.2 + 0.4 = 0.6s)
        simulateIpcClientMessage('test-client-id', { eventName: RooCodeEventName.DGMEchoResponse, payload: [{ echoed_text: "HELLO 2" }] });

        expectedMetrics = {
            cyclesCompleted: 2, successfulActions: 2, failedActions: 0, totalProcessingTime: 0.6, averageProcessingTimePerCycle: 0.3, // 0.6 / 2
        };
        expect(mockLlmAttentionPortalPanel.updateMetrics).toHaveBeenLastCalledWith(expectedMetrics);
        expect(dgmServiceInstance['totalProcessingTimeSeconds']).toBeCloseTo(0.6);


        // Cycle 3 (failure)
        dgmServiceInstance.sendToUpperEchoCommand("hello 3");
        currentTime += 300; // 0.3s (total time = 0.6 + 0.3 = 0.9s)
        simulateIpcClientMessage('test-client-id', { eventName: RooCodeEventName.DGMEchoResponse, payload: [{ unexpected: "fail" }] });

        expectedMetrics = {
            cyclesCompleted: 3, successfulActions: 2, failedActions: 1, totalProcessingTime: 0.9, averageProcessingTimePerCycle: 0.3, // 0.9 / 3
        };
        expect(mockLlmAttentionPortalPanel.updateMetrics).toHaveBeenLastCalledWith(expectedMetrics);
        expect(dgmServiceInstance['totalProcessingTimeSeconds']).toBeCloseTo(0.9);
    });

     test('Average Processing Time Calculation: zero when no cycles completed', () => {
        // This state is hard to achieve without calling any command, as updateAndSendMetrics is only called after a cycle.
        // However, the logic for DGMSummaryMetrics itself ensures avg = 0 if cyclesCompleted = 0.
        // We can directly test the metrics object creation part if needed, or trust the calculation in updateAndSendMetrics.
        // For an "empty" call to updateAndSendMetrics (if it were public/testable that way):
        dgmServiceInstance['cyclesCompleted'] = 0;
        dgmServiceInstance['totalProcessingTimeSeconds'] = 0;
        dgmServiceInstance['updateAndSendMetrics'](); // Call private method for test

        const expectedMetrics: DGMSummaryMetrics = {
            cyclesCompleted: 0,
            successfulActions: 0,
            failedActions: 0,
            totalProcessingTime: 0,
            averageProcessingTimePerCycle: 0,
        };
        // If currentPanel is null, updateMetrics is not called.
        // To test this specific case properly, we might need to ensure currentPanel is defined.
        if (LLMAttentionPortalPanel.currentPanel) {
            expect(mockLlmAttentionPortalPanel.updateMetrics).toHaveBeenCalledWith(expectedMetrics);
        } else {
            // If currentPanel could be null, then updateMetrics wouldn't be called.
            // This test setup ensures currentPanel is defined, so this path isn't hit here.
        }
    });

  });

  // Further tests could cover:
  // - DGM process failing to start
  // - IPC server failing to listen
  // - DGM client disconnecting abruptly (covered somewhat by process exit)
  // - Multiple sendToUpperEchoCommand calls before responses (though current design might not queue them)
  // - What happens if LLMAttentionPortalPanel.currentPanel is null at various points
});

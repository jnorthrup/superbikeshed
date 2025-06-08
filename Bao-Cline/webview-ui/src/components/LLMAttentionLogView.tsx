import React, { useEffect, useState } from 'react';

interface DGMInteractionLog {
  id: string;
  timestamp: string;
  command: string;
  requestPayload: any;
  responsePayload?: any;
  error?: string;
  status: "pending" | "completed" | "failed";
}

// This component would be rendered by your main App.tsx in the webview-ui
export const LLMAttentionLogView: React.FC = () => {
  const [logs, setLogs] = useState<DGMInteractionLog[]>([]);
  // @ts-ignore
  const vscode = window.acquireVsCodeApi(); // Get VS Code API

  useEffect(() => {
    // Signal to the extension that the webview is ready to receive data
    vscode.postMessage({ command: 'ready' });

    const handleMessage = (event: MessageEvent) => {
      const message = event.data; // The json data that the extension sent
      if (message.type === 'updateLogs') {
        setLogs(message.payload);
      }
    };

    window.addEventListener('message', handleMessage);

    // Cleanup
    return () => {
      window.removeEventListener('message', handleMessage);
    };
  }, [vscode]); // vscode is stable, but good to include if used in effect

  if (!logs.length) {
    return <p>No DGM interactions logged yet. Waiting for data...</p>;
  }

  return (
    <div style={{ padding: '1em' }}>
      <h3>DGM Interaction Logs</h3>
      <button onClick={() => vscode.postMessage({ command: 'refreshLogs' })} style={{ marginBottom: '1em' }}>
        Refresh Logs (Manual)
      </button>
      <ul style={{ listStyleType: 'none', padding: 0 }}>
        {logs.map((log) => (
          <li key={log.id} style={{ marginBottom: '1em', border: '1px solid #555', padding: '0.5em', borderRadius: '4px', backgroundColor: '#2a2a2a' }}>
            <div><strong>ID:</strong> {log.id}</div>
            <div><strong>Timestamp:</strong> {new Date(log.timestamp).toLocaleString()}</div>
            <div><strong>Command:</strong> <span style={{ color: '#87ceeb' }}>{log.command}</span></div>
            <div>
              <strong>Status:</strong>
              <span style={{
                color: log.status === 'completed' ? '#90ee90' : log.status === 'failed' ? '#ffcccb' : '#f0e68c',
                fontWeight: 'bold',
                marginLeft: '0.5em'
              }}>
                {log.status.toUpperCase()}
              </span>
            </div>
            <details open={log.status === 'pending' || log.status === 'failed'}>
              <summary style={{ cursor: 'pointer', marginTop: '0.5em', marginBottom: '0.5em' }}>Toggle Details</summary>
              <div><strong>Request:</strong> <pre style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-all', backgroundColor: '#333', padding: '0.5em', borderRadius: '3px' }}>{JSON.stringify(log.requestPayload, null, 2)}</pre></div>
              {log.responsePayload && (
                <div><strong>Response:</strong> <pre style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-all', backgroundColor: '#333', padding: '0.5em', borderRadius: '3px' }}>{JSON.stringify(log.responsePayload, null, 2)}</pre></div>
              )}
              {log.error && (
                <div><strong>Error:</strong> <pre style={{color: '#ff6347', whiteSpace: 'pre-wrap', wordBreak: 'break-all', backgroundColor: '#333', padding: '0.5em', borderRadius: '3px' }}>{log.error}</pre></div>
              )}
            </details>
          </li>
        ))}
      </ul>
    </div>
  );
};

// Ensure this component is imported and used in your webview-ui's main application file (e.g., App.tsx or main.tsx)
// Example (in App.tsx or similar):
// import { LLMAttentionLogView } from './components/LLMAttentionLogView';
// function App() {
//   // If you have routing or conditional rendering based on webview type:
//   // if (window.location.pathname.includes('llmAttentionPortal')) { // Or some other indicator
//   //   return <LLMAttentionLogView />;
//   // }
//   // return <YourDefaultWebviewView />;
//   return <LLMAttentionLogView />; // Or simply this if it's the only view for this webview build
// }
// export default App;

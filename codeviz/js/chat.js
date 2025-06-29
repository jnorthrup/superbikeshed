// Code Visualizer - Chat Client Logic
import { IRCClient } from './ircClient.js';

console.log("Code Visualizer chat.js loaded.");

document.addEventListener('DOMContentLoaded', () => {
    const messageInput = document.getElementById('message-input');
    const messagesContainer = document.getElementById('messages');
    let ircClientInstance = null;
    const NICKNAME = "CodeVizUser";
    const SERVER = "irc.conceptual.net"; // Placeholder server
    const CHANNEL = "#codeviz-nexus";

    /**
     * Displays a message in the chat UI.
     * @param {string} text - The HTML formatted text of the message.
     * @param {string} [type='normal'] - The type of message ('normal', 'system', 'error').
     */
    function displayMessage(text, type = 'normal') {
        const messageElement = document.createElement('p');
        messageElement.innerHTML = text; // Using innerHTML to allow simple formatting like bold for nicks

        // Add class for styling based on type, instead of direct style manipulation
        messageElement.classList.add('message-type-' + type);
        // Fallback styles if CSS classes aren't defined or to override
        if (type === 'system') {
            messageElement.style.fontStyle = 'italic'; // Kept for explicitness if class not styled yet
            messageElement.style.color = '#888';
        } else if (type === 'error') {
            messageElement.style.color = 'red';
            messageElement.style.fontWeight = 'bold';
        }

        messagesContainer.appendChild(messageElement);
        messagesContainer.scrollTop = messagesContainer.scrollHeight; // Auto-scroll
    }

    /**
     * Formats a message with a timestamp and sender information.
     * @param {string|null} from - The sender of the message. Null for system messages.
     * @param {string} message - The message content.
     * @param {boolean} [isSystem=false] - True if it's a system message.
     * @returns {string} HTML formatted message string.
     */
    function formatMessage(from, message, isSystem = false) {
        const time = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
        if (isSystem) {
            return `<span class="time">[${time}]</span> <span class="system-message-content">* ${message}</span>`;
        }
        // Sanitize 'from' and 'message' if they were to include user-generated HTML.
        // For this conceptual version, assuming they are plain text.
        const safeFrom = from ? from.replace(/</g, "&lt;").replace(/>/g, "&gt;") : "Unknown";
        const safeMessage = message ? message.replace(/</g, "&lt;").replace(/>/g, "&gt;") : "";
        return `<span class="time">[${time}]</span> &lt;strong class="nick">${safeFrom}</strong>&gt; ${safeMessage}`;
    }

    if (messageInput && messagesContainer) {
        // Initialize and connect IRC Client
        ircClientInstance = new IRCClient(SERVER, NICKNAME);

        // Handler for incoming chat messages from the IRC client
        ircClientInstance.on('message', (from, text, channel) => {
            // In a multi-channel client, you'd check if 'channel' is the active one.
            displayMessage(formatMessage(from, text));
        });

        // Handler for system/status messages from the IRC client
        ircClientInstance.on('system', (text) => {
            displayMessage(formatMessage(null, text, true), 'system');
        });

        displayMessage(formatMessage(null, "Attempting to connect to IRC...", true), 'system');

        // Connect to IRC and join the default channel
        ircClientInstance.connect()
            .then(() => {
                // System message for successful connection already handled by ircClient's _systemMessage
                // This is just a UI confirmation that next step is join.
                displayMessage(formatMessage(null, `Preparing to join ${CHANNEL}...`, true), 'system');
                return ircClientInstance.joinChannel(CHANNEL);
            })
            .then(() => {
                // System message for successful join also handled by ircClient
                // UI confirmation:
                displayMessage(formatMessage(null, `Chat ready in ${CHANNEL}.`, true), 'system');
            })
            .catch(error => {
                console.error("IRC Connection or Join Error:", error);
                displayMessage(formatMessage(null, `Error initializing IRC: ${error}`, true), 'error');
            });

        // Event listener for sending messages
        messageInput.addEventListener('keypress', (event) => {
            if (event.key === 'Enter' && messageInput.value.trim() !== '') {
                const messageText = messageInput.value.trim();

                if (ircClientInstance && ircClientInstance.connected) {
                    ircClientInstance.sendMessage(CHANNEL, messageText);
                    // Self-echo is handled by the conceptual IRCClient's _emitMessage.
                } else {
                    displayMessage(formatMessage(null, "Not connected to IRC. Message not sent.", true), 'error');
                }
                messageInput.value = ''; // Clear input field
            }
        });
    } else {
        console.error("Chat UI elements (messageInput or messagesContainer) not found!");
    }
});

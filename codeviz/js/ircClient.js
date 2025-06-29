// Code Visualizer - Conceptual IRC Client Logic
console.log("Code Visualizer ircClient.js loaded.");

/**
 * @file Conceptual IRC Client for simulation purposes.
 * This class mimics basic IRC client functionality without actual network connections.
 * It uses `setTimeout` to simulate asynchronous operations and logs actions to the console.
 */

export class IRCClient {
    /**
     * Creates an instance of IRCClient.
     * @param {string} server - The address of the conceptual IRC server.
     * @param {string} nick - The nickname for the client.
     * @param {object} [options={}] - Conceptual options for connection (unused in this mock).
     */
    constructor(server, nick, options = {}) {
        this.server = server;
        this.nick = nick;
        this.options = options;
        this.connected = false;
        this.channels = new Set();
        /** @type {((from: string, text: string, channel: string|null) => void)|null} */
        this.onMessageCallback = null;
        /** @type {((text: string) => void)|null} */
        this.onSystemMessageCallback = null;

        console.log(`[IRCClient] Initialized for server: ${server}, nick: ${nick}`);
    }

    /**
     * Simulates connecting to the IRC server.
     * @returns {Promise<void>} A promise that resolves when the connection is "established".
     */
    connect() {
        if (this.connected) {
            console.warn("[IRCClient] Already connected.");
            return Promise.resolve();
        }
        this._systemMessage(`Connecting to ${this.server} as ${this.nick}...`);
        return new Promise((resolve) => {
            setTimeout(() => {
                this.connected = true;
                this._systemMessage(`Connected to ${this.server}. Your nick is ${this.nick}.`);
                this._emitMessage("Server", `Welcome to the ${this.server} IRC Network, ${this.nick}!`);
                this._emitMessage("Server", `Mode for user ${this.nick} set to +i`);
                resolve();
            }, 1500); // Simulate connection delay
        });
    }

    /**
     * Simulates disconnecting from the IRC server.
     */
    disconnect() {
        if (!this.connected) {
            console.warn("[IRCClient] Not connected.");
            return;
        }
        this._systemMessage(`Disconnecting from ${this.server}...`);
        this.connected = false;
        this.channels.clear();
        setTimeout(() => {
            this._systemMessage("Disconnected.");
        }, 500);
    }

    /**
     * Simulates joining an IRC channel.
     * @param {string} channel - The channel name to join (e.g., "#mychannel").
     * @returns {Promise<void>} A promise that resolves when the channel is "joined".
     */
    joinChannel(channel) {
        if (!this.connected) {
            this._systemMessage("Error: Not connected. Cannot join channel.");
            return Promise.reject("Not connected");
        }
        if (this.channels.has(channel)) {
            this._systemMessage(`Already in channel ${channel}.`);
            return Promise.resolve();
        }
        this._systemMessage(`Joining channel ${channel}...`);
        return new Promise((resolve) => {
            setTimeout(() => {
                this.channels.add(channel);
                this._systemMessage(`Joined channel ${channel}.`);
                this._emitMessage("Server", `Users in ${channel}: ${this.nick}, User1, User2, Bot`);
                this._emitMessage("Bot", `Welcome ${this.nick} to ${channel}! Type !help for commands.`);
                resolve();
            }, 1000); // Simulate join delay
        });
    }

    /**
     * Simulates parting (leaving) an IRC channel.
     * @param {string} channel - The channel name to part.
     */
    partChannel(channel) {
        if (!this.channels.has(channel)) {
            this._systemMessage(`Not in channel ${channel}.`);
            return;
        }
        this._systemMessage(`Leaving channel ${channel}...`);
        this.channels.delete(channel);
        setTimeout(() => {
            this._systemMessage(`Left channel ${channel}.`);
        }, 500);
    }

    /**
     * Simulates sending a message to a channel or user.
     * @param {string} target - The channel or nickname to send the message to.
     * @param {string} message - The message text.
     */
    sendMessage(target, message) {
        if (!this.connected) {
            this._systemMessage("Error: Not connected. Cannot send message.");
            return;
        }
        // Basic check: if target is not a channel and not in current channels (simplistic PM check)
        if (!target.startsWith("#") && !this.channels.has(target)) {
            console.log(`[IRCClient] Sending PM to ${target}: ${message}`);
        } else if (!this.channels.has(target) && target.startsWith("#")) {
             this._systemMessage(`Error: Not in channel ${target}. Cannot send message.`);
            return;
        }

        console.log(`[IRCClient] Sending to ${target}: ${message}`);
        // Simulate self-echo for channel messages
        if (this.channels.has(target) || target.startsWith("#")) { // Check if it's a channel message
            this._emitMessage(this.nick, message, target);
        }


        // Simulate a bot response for testing
        if (message.toLowerCase().includes("hello bot")) {
            setTimeout(() => {
                this._emitMessage("Bot", `Hello ${this.nick}!`, target);
            }, 600);
        }
    }

    /**
     * Registers a callback for specific event types.
     * @param {'message' | 'system'} eventType - The type of event to listen for.
     * @param {function} callback - The callback function.
     *                              For 'message': (from: string, text: string, channel: string|null) => void.
     *                              For 'system': (text: string) => void.
     */
    on(eventType, callback) {
        if (eventType === 'message') {
            this.onMessageCallback = callback;
        } else if (eventType === 'system') {
            this.onSystemMessageCallback = callback;
        }
    }

    /**
     * Internal helper to simulate emitting a message from the server or other users.
     * @private
     * @param {string} from - The sender of the message.
     * @param {string} text - The message content.
     * @param {string|null} [channel=null] - The channel the message belongs to, or null for general server messages.
     */
    _emitMessage(from, text, channel = null) {
        if (this.onMessageCallback) {
            this.onMessageCallback(from, text, channel || "status"); // Default to "status" if channel is null
        }
    }

    /**
     * Internal helper for system/status messages.
     * @private
     * @param {string} text - The system message text.
     */
    _systemMessage(text) {
        console.log(`[IRCClient][System] ${text}`);
        if (this.onSystemMessageCallback) {
            this.onSystemMessageCallback(text);
        }
    }
}

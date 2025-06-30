// js/core/battleLogger.js

export class BattleLogger {
    constructor() {
        this.logContainer = document.getElementById('battle-log');
        this.maxEntries = 50;
    }

    addEntry(type, message, time = null) {
        const entry = document.createElement('div');
        entry.className = `log-entry ${type}`;
        
        const timeSpan = document.createElement('span');
        timeSpan.className = 'log-time';
        timeSpan.textContent = time || this.formatTime(Date.now());
        
        const messageSpan = document.createElement('span');
        messageSpan.className = 'log-message';
        messageSpan.textContent = message;
        
        entry.appendChild(timeSpan);
        entry.appendChild(messageSpan);
        
        this.logContainer.appendChild(entry);
        
        // Remove old entries
        while (this.logContainer.children.length > this.maxEntries) {
            this.logContainer.removeChild(this.logContainer.firstChild);
        }
        
        // Auto-scroll to bottom
        this.logContainer.scrollTop = this.logContainer.scrollHeight;
    }

    formatTime(timestamp) {
        const seconds = Math.floor(timestamp / 1000) % 60;
        const minutes = Math.floor(timestamp / 60000) % 60;
        return `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
    }
} 
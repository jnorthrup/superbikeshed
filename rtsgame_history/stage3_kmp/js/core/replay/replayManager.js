import { BattleJournal } from './battleJournal.js';

export class ReplayManager {
    constructor(gameContext) {
        this.gameContext = gameContext;
        this.battleJournal = new BattleJournal();
        this.recordingEnabled = false;
    }

    startRecording() {
        this.recordingEnabled = true;
        this.battleJournal.startRecording();
        this.gameContext.battleLogger.addEntry('system', 'Started recording battle');
    }

    stopRecording() {
        if (!this.recordingEnabled) return null;
        
        this.recordingEnabled = false;
        const battleId = this.battleJournal.stopRecording();
        this.gameContext.battleLogger.addEntry('system', 'Stopped recording battle');
        return battleId;
    }

    loadReplay() {
        const input = document.createElement('input');
        input.type = 'file';
        input.accept = '.replay';
        
        input.onchange = (e) => {
            const file = e.target.files[0];
            if (file) {
                const reader = new FileReader();
                reader.onload = (event) => {
                    try {
                        const replay = JSON.parse(event.target.result);
                        this.battleJournal.loadReplay(replay);
                        this.gameContext.battleLogger.addEntry('system', 'Loaded replay');
                    } catch (error) {
                        this.gameContext.battleLogger.addEntry('error', 'Failed to load replay: ' + error.message);
                    }
                };
                reader.readAsText(file);
            }
        };
        
        input.click();
    }

    exportReplay() {
        const replay = this.battleJournal.exportReplay();
        const blob = new Blob([JSON.stringify(replay)], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        
        const a = document.createElement('a');
        a.href = url;
        a.download = `battle-${new Date().toISOString()}.replay`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
        
        this.gameContext.battleLogger.addEntry('system', 'Exported replay');
    }

    recordFrame(gameContext) {
        if (this.recordingEnabled) {
            this.battleJournal.recordFrame(gameContext);
        }
    }

    recordEvent(type, message, position = null) {
        if (this.recordingEnabled) {
            this.battleJournal.recordEvent(type, message, position);
        }
    }

    isRecording() {
        return this.recordingEnabled;
    }
} 
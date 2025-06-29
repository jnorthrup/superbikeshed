"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.ReplayPlayer = void 0;
const battleJournal_js_1 = require("./battleJournal.js");
class ReplayPlayer {
    constructor(gameContext) {
        this.gameContext = gameContext;
        this.battleJournal = new battleJournal_js_1.BattleJournal();
        this.isPlaying = false;
        this.currentTime = 0;
        this.playbackSpeed = 1.0;
        this.lastFrameTime = 0;
    }
    loadReplay(replay) {
        this.battleJournal.loadReplay(replay);
        this.currentTime = 0;
        this.lastFrameTime = 0;
        this.updateGameState();
    }
    startPlayback() {
        if (this.isPlaying)
            return;
        this.isPlaying = true;
        this.lastFrameTime = performance.now();
        this.gameContext.battleLogger.addEntry('system', 'Started replay playback');
    }
    stopPlayback() {
        if (!this.isPlaying)
            return;
        this.isPlaying = false;
        this.gameContext.battleLogger.addEntry('system', 'Stopped replay playback');
    }
    setPlaybackSpeed(speed) {
        this.playbackSpeed = speed;
    }
    seekTo(time) {
        this.currentTime = Math.max(0, Math.min(time, this.battleJournal.getDuration()));
        this.updateGameState();
    }
    update(deltaTime) {
        if (!this.isPlaying)
            return;
        this.currentTime += deltaTime * this.playbackSpeed;
        if (this.currentTime >= this.battleJournal.getDuration()) {
            this.currentTime = this.battleJournal.getDuration();
            this.stopPlayback();
        }
        this.updateGameState();
    }
    updateGameState() {
        const frame = this.battleJournal.getFrameAtTime(this.currentTime);
        if (!frame)
            return;
        // Update game time
        this.gameContext.gameTime = frame.time;
        // Update resources
        this.gameContext.resources = Object.assign({}, frame.resources);
        // Update units
        this.gameContext.units = frame.units.map(unitData => {
            const existingUnit = this.gameContext.units.find(u => u.id === unitData.id);
            if (existingUnit) {
                Object.assign(existingUnit, unitData);
                return existingUnit;
            }
            return Object.assign({}, unitData);
        });
        // Update buildings
        this.gameContext.buildings = frame.buildings.map(buildingData => {
            const existingBuilding = this.gameContext.buildings.find(b => b.id === buildingData.id);
            if (existingBuilding) {
                Object.assign(existingBuilding, buildingData);
                return existingBuilding;
            }
            return Object.assign({}, buildingData);
        });
        // Process events
        const events = this.battleJournal.getEventsAtTime(this.currentTime);
        events.forEach(event => {
            if (event.position) {
                this.gameContext.battleLogger.addEntry(event.type, event.message, event.position);
            }
            else {
                this.gameContext.battleLogger.addEntry(event.type, event.message);
            }
        });
    }
    isPlaying() {
        return this.isPlaying;
    }
    getCurrentTime() {
        return this.currentTime;
    }
    getDuration() {
        return this.battleJournal.getDuration();
    }
}
exports.ReplayPlayer = ReplayPlayer;

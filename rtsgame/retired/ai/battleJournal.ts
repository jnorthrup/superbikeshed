// ####################################################################################################
// #                                                                                                  #
// #   DEPRECATED FILE: This entire battle journal system is deprecated.                              #
// #   The unified battle journal is now located at js/core/battleJournal.js.                         #
// #   All systems should be updated to use the instance exported from js/core/battleJournal.js.      #
// #                                                                                                  #
// ####################################################################################################

// js/ai/battleJournal.js
class BattleJournal {
    constructor() {
        this.events = [];
        this.isRecording = false;
        this.startTime = 0;
        this.duration = 0;
        this.exceptions = [];
        this.lastJournalEntryTime = 0;
    }

    startRecording(seed, durationSeconds, journalAllEvents = false) {
        this.isRecording = true;
        this.startTime = Date.now();
        this.duration = durationSeconds;
        this.seed = seed;
        this.exceptions = [];
        this.journalAllEvents = journalAllEvents;
        this.lastJournalEntryTime = Date.now();
        console.log(`Battle Journal started recording with seed: ${seed} for ${durationSeconds} seconds, journalAllEvents: ${journalAllEvents}`);
    }

    stopRecording() {
        if (this.isRecording) {
            console.log(`Battle Journal stopped recording after ${Math.round((Date.now() - this.startTime) / 1000)} seconds`);
            this.isRecording = false;
            return this.getRecording();
        }
        return null;
    }

    recordEvent(type, message, gameTime, details = {}) {
        try {
            if (!this.isRecording) return;

            // Check if we're still within the recording duration
            if (Date.now() - this.startTime > this.duration * 1000) {
                console.warn('Skipping event recording - recording duration has expired');
                return;
            }

            // Add journaling for all events if configured
            if (this.journalAllEvents || type === 'ERROR' || type === 'WARNING') {
                const event = {
                    type,
                    message,
                    gameTime,
                    timestamp: Date.now(),
                    ...details
                };
                this.events.push(event);
                
                // Log to console for immediate visibility
                const formattedTime = new Date(gameTime).toISOString();
                console.log(`[${formattedTime}] [${type}] ${message}`, details);
            }
        } catch (err) {
            console.error('Error recording event:', err);
            this.exceptions.push({
                type: 'EVENT_RECORDING_ERROR',
                error: err.message,
                timestamp: Date.now(),
                originalEvent: { type, message, gameTime, details }
            });
        }
    }

    logException(error, context = {}) {
        try {
            const errorEvent = {
                type: 'ERROR',
                message: error.message,
                gameTime: Date.now(),
                details: {
                    stack: error.stack,
                    context,
                    timestamp: new Date().toISOString()
                }
            };
            this.events.push(errorEvent);
            console.error('Simulation Exception:', error, context);
        } catch (err) {
            console.error('Error recording exception:', err);
            this.exceptions.push({
                type: 'EXCEPTION_RECORDING_ERROR',
                error: err.message,
                timestamp: Date.now(),
                originalError: error
            });
        }
    }

    getRecording() {
        try {
            if (!this.isRecording) return null;

            const result = {
                seed: this.seed,
                duration: this.duration,
                events: this.events,
                exceptions: this.exceptions,
                endTime: Date.now(),
                recordingDuration: Math.round((Date.now() - this.startTime) / 1000),
                journalStatus: this.journalAllEvents
                    ? 'FULL_SIMULATION_JOURNALING'
                    : 'AI_DECISION_JOURNALING',
            };
            
            // Log journal completion
            console.log(`Journal completed with ${this.events.length} events and ${this.exceptions.length} exceptions`);
            return result;
        } catch (err) {
            console.error('Error generating recording:', err);
            return {
                error: 'Failed to generate recording',
                exception: err.message
            };
        }
    }

    stopRecording() {
        if (this.isRecording) {
            console.log(`Battle Journal stopped recording after ${Math.round((Date.now() - this.startTime) / 1000)} seconds`);
            this.isRecording = false;
            return this.getRecording();
        }
        return null;
    }
}

// Export the singleton instance
const battleJournal = new BattleJournal();
export default battleJournal;
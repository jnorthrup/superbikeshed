// js/core/performanceMonitor.js

export class PerformanceMonitor {
    constructor() {
        this.frameCount = 0;
        this.lastFpsUpdate = performance.now();
        this.frameTimes = [];
        this.maxFrameTimes = 60; // Keep last 60 frame times
    }

    startFrame() {
        this.frameStart = performance.now();
    }

    endFrame() {
        const frameTime = performance.now() - this.frameStart;
        this.frameTimes.push(frameTime);
        
        if (this.frameTimes.length > this.maxFrameTimes) {
            this.frameTimes.shift();
        }
        
        this.frameCount++;
        
        // Update UI every 10 frames for smooth display
        if (this.frameCount % 10 === 0) {
            this.updateUI();
        }
    }

    updateUI() {
        const avgFrameTime = this.frameTimes.reduce((a, b) => a + b, 0) / this.frameTimes.length;
        const fps = 1000 / avgFrameTime;
        
        // Update performance metrics
        document.getElementById('frame-time').textContent = `${avgFrameTime.toFixed(2)}ms`;
        document.getElementById('fps-counter').textContent = Math.round(fps);
        
        // Update cache efficiency based on frame time consistency
        const frameTimeVariance = this.frameTimes.reduce((sum, time) => {
            return sum + Math.pow(time - avgFrameTime, 2);
        }, 0) / this.frameTimes.length;
        const stdDev = Math.sqrt(frameTimeVariance);
        
        let efficiency = 'EXCELLENT';
        if (stdDev > 0.5) efficiency = 'GOOD';
        if (stdDev > 1.0) efficiency = 'FAIR';
        if (stdDev > 2.0) efficiency = 'POOR';
        
        document.getElementById('cache-efficiency').textContent = efficiency;
    }
} 
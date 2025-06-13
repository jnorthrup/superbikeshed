// js/core/camera.js

export class Camera {
    constructor(canvas) {
        this.x = 0;
        this.y = 0;
        this.zoom = 0.5;
        this.canvasWidth = canvas.width;
        this.canvasHeight = canvas.height;
        this.dragging = false;
        this.lastX = 0;
        this.lastY = 0;
    }

    zoomIn() {
        this.zoom = Math.min(this.zoom * 1.2, 2.0);
    }

    zoomOut() {
        this.zoom = Math.max(this.zoom / 1.2, 0.1);
    }

    centerView(worldSize) {
        this.x = worldSize / 2;
        this.y = worldSize / 2;
    }

    handleZoom(e) {
        e.preventDefault();
        const delta = e.deltaY > 0 ? 0.9 : 1.1;
        this.zoom = Math.max(0.1, Math.min(2.0, this.zoom * delta));
    }

    handleMouseDown(e) {
        this.dragging = true;
        this.lastX = e.clientX;
        this.lastY = e.clientY;
    }

    handleMouseMove(e) {
        if (!this.dragging) return;
        
        const dx = (e.clientX - this.lastX) / this.zoom;
        const dy = (e.clientY - this.lastY) / this.zoom;
        
        this.x -= dx;
        this.y -= dy;
        
        this.lastX = e.clientX;
        this.lastY = e.clientY;
    }

    handleMouseUp() {
        this.dragging = false;
    }

    isInView(x, y, size) {
        const screenX = (x - this.x) * this.zoom + this.canvasWidth / 2;
        const screenY = (y - this.y) * this.zoom + this.canvasHeight / 2;
        const screenSize = size * this.zoom;
        
        return (
            screenX + screenSize > 0 &&
            screenX - screenSize < this.canvasWidth &&
            screenY + screenSize > 0 &&
            screenY - screenSize < this.canvasHeight
        );
    }

    worldToScreen(x, y) {
        return {
            x: (x - this.x) * this.zoom + this.canvasWidth / 2,
            y: (y - this.y) * this.zoom + this.canvasHeight / 2
        };
    }

    screenToWorld(x, y) {
        return {
            x: (x - this.canvasWidth / 2) / this.zoom + this.x,
            y: (y - this.canvasHeight / 2) / this.zoom + this.y
        };
    }
} 
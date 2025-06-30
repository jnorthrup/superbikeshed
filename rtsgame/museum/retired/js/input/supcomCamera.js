"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.SupComCamera = void 0;
// js/input/supcomCamera.js - Supreme Commander style camera controls
const gameConstants_js_1 = require("../config/gameConstants.js");
class SupComCamera {
    constructor(gameContext) {
        this.gameContext = gameContext;
        this.camera = gameContext.camera;
        // SupCom-specific camera settings
        this.settings = {
            // Movement
            keyboardMoveSpeed: 2000,
            edgeScrollSpeed: 1500,
            edgeScrollZone: 50, // pixels from edge
            momentum: 0.92, // How much momentum is retained
            maxVelocity: 3000,
            // Zoom
            zoomSpeed: 1.2,
            minZoom: 0.01,
            maxZoom: 100.0,
            strategicZoomThreshold: 0.1, // Below this, switch to strategic view
            // Rotation
            rotationSpeed: 90, // degrees per second
            rotationResetSpeed: 180, // degrees per second for auto-reset
            rotationTimeout: 2000, // ms before auto-reset starts
            maxRotation: 180, // degrees from default
            // 3D Mode
            enable3D: false,
            tiltAngle: 45, // degrees for 3D view
            tiltSpeed: 60, // degrees per second
            // Team orientation
            teamRotation: { blue: 0, red: 180 }, // Default facing directions
            currentTeam: 'blue',
            zoomSmoothingFactor: 0.2,
            panSmoothingFactor: 0.25, // Added pan smoothing factor
        };
        // State tracking
        this.state = {
            targetX: this.camera.x, // Initialize targetX with current camera position
            targetY: this.camera.y, // Initialize targetY with current camera position
            targetZoom: this.camera.zoom, // Initialize targetZoom with current zoom
            isKeyboardPanningActive: false,
            isEdgeScrollingActive: false,
            isDragging: false,
            // isEdgeScrolling: false, // Removed duplicate, isEdgeScrollingActive will be used
            lastMouseX: 0,
            lastMouseY: 0,
            velocityX: 0,
            velocityY: 0,
            velocityZoom: 0,
            rotationVelocity: 0,
            lastRotationTime: 0,
            targetRotation: 0,
            isRotationLocked: false,
            mousePosition: { x: 0, y: 0 },
            keys: {
                w: false, a: false, s: false, d: false,
                q: false, e: false, // rotation
                shift: false, ctrl: false, alt: false
            }
        };
        this.setupEventListeners();
        this.resetToTeamOrientation();
    }
    setupEventListeners() {
        const canvas = this.gameContext.canvas;
        // Mouse events
        canvas.addEventListener('wheel', this.handleWheel.bind(this), { passive: false });
        canvas.addEventListener('mousedown', this.handleMouseDown.bind(this));
        canvas.addEventListener('mousemove', this.handleMouseMove.bind(this));
        canvas.addEventListener('mouseup', this.handleMouseUp.bind(this));
        canvas.addEventListener('mouseleave', this.handleMouseLeave.bind(this));
        // Keyboard events
        document.addEventListener('keydown', this.handleKeyDown.bind(this));
        document.addEventListener('keyup', this.handleKeyUp.bind(this));
        // Prevent context menu on right click
        canvas.addEventListener('contextmenu', (e) => e.preventDefault());
    }
    handleWheel(e) {
        e.preventDefault();
        const rect = this.gameContext.canvas.getBoundingClientRect();
        const mouseX = e.clientX - rect.left;
        const mouseY = e.clientY - rect.top;
        // Calculate zoom factor
        const zoomFactor = e.deltaY < 0 ? this.settings.zoomSpeed : 1 / this.settings.zoomSpeed;
        const newZoom = this.camera.zoom * zoomFactor;
        // Clamp zoom
        const clampedZoom = Math.max(this.settings.minZoom, Math.min(this.settings.maxZoom, newZoom));
        if (clampedZoom !== this.camera.zoom) { // Check against current camera zoom, not targetZoom, to see if a change is warranted
            // Simplified "zoom to cursor" logic:
            // Calculate world point under cursor BEFORE zoom change (using current camera.zoom)
            const worldX = (mouseX - this.camera.canvasWidth / 2) / this.camera.zoom + this.camera.x;
            const worldY = (mouseY - this.camera.canvasHeight / 2) / this.camera.zoom + this.camera.y;
            // Set the target zoom for smooth interpolation in the update loop
            this.state.targetZoom = clampedZoom;
            // Calculate world point under cursor AFTER conceptual instant zoom to targetZoom (for panning adjustment)
            // This uses the targetZoom (clampedZoom) for calculation
            const newWorldX = (mouseX - this.camera.canvasWidth / 2) / clampedZoom + this.camera.x;
            const newWorldY = (mouseY - this.camera.canvasHeight / 2) / clampedZoom + this.camera.y;
            // Adjust camera position to effectively keep the world point under the cursor fixed
            // This adjustment is applied instantly to camera.x/y, the visual zoom will catch up.
            this.camera.x += worldX - newWorldX;
            this.camera.y += worldY - newWorldY;
            // Removed: this.state.velocityZoom = (clampedZoom - this.camera.zoom) * 0.1;
        }
    }
    // Helper method to convert screen coordinates to world coordinates for a given zoom level
    screenToWorld(screenX, screenY, zoomToUse) {
        const canvasWidth = this.camera.canvasWidth || this.gameContext.canvas.width;
        const canvasHeight = this.camera.canvasHeight || this.gameContext.canvas.height;
        return {
            x: (screenX - canvasWidth / 2) / zoomToUse + this.camera.x,
            y: (screenY - canvasHeight / 2) / zoomToUse + this.camera.y
        };
    }
    handleMouseDown(e) {
        if (e.button === 0) { // Left click
            this.state.isDragging = true;
            this.state.lastMouseX = e.clientX;
            this.state.lastMouseY = e.clientY;
            this.state.velocityX = 0;
            this.state.velocityY = 0;
            if (this.gameContext.canvas) {
                this.gameContext.canvas.style.cursor = 'grabbing';
            }
        }
    }
    handleMouseMove(e) {
        this.state.mousePosition.x = e.clientX;
        this.state.mousePosition.y = e.clientY;
        if (this.state.isDragging) {
            const deltaX = e.clientX - this.state.lastMouseX;
            const deltaY = e.clientY - this.state.lastMouseY;
            // Convert screen delta to world delta
            const worldDeltaX = deltaX / this.camera.zoom;
            const worldDeltaY = deltaY / this.camera.zoom;
            // Update target camera position (invert for natural drag feel)
            this.state.targetX -= worldDeltaX;
            this.state.targetY -= worldDeltaY;
            // Store velocity for momentum
            this.state.velocityX = -worldDeltaX * 60; // Convert to per-second
            this.state.velocityY = -worldDeltaY * 60;
            this.state.lastMouseX = e.clientX;
            this.state.lastMouseY = e.clientY;
        }
        else {
            // Edge scrolling
            this.updateEdgeScrolling(e);
        }
    }
    handleMouseUp(e) {
        if (e.button === 0) {
            this.state.isDragging = false;
            if (this.gameContext.canvas) {
                this.gameContext.canvas.style.cursor = 'grab';
            }
        }
    }
    handleMouseLeave() {
        this.state.isDragging = false;
        this.state.isEdgeScrolling = false;
        if (this.gameContext.canvas) {
            this.gameContext.canvas.style.cursor = 'grab';
        }
    }
    handleKeyDown(e) {
        const key = e.code;
        switch (key) {
            case 'KeyW':
                this.state.keys.w = true;
                break;
            case 'KeyA':
                this.state.keys.a = true;
                break;
            case 'KeyS':
                this.state.keys.s = true;
                break;
            case 'KeyD':
                this.state.keys.d = true;
                break;
            case 'KeyQ':
                this.state.keys.q = true;
                this.startRotation();
                break;
            case 'KeyE':
                this.state.keys.e = true;
                this.startRotation();
                break;
        }
        if (e.key === 'Shift')
            this.state.keys.shift = true;
        if (e.key === 'Control')
            this.state.keys.ctrl = true;
        if (e.key === 'Alt') {
            this.state.keys.alt = true;
            this.toggle3DMode();
        }
        // Team orientation reset
        if (e.key === 'Home' || e.key === 'H') {
            this.resetToTeamOrientation();
        }
        // Quick team switch for head-to-head
        if (e.key >= '1' && e.key <= '2') {
            this.settings.currentTeam = e.key === '1' ? 'blue' : 'red';
            this.resetToTeamOrientation();
        }
    }
    handleKeyUp(e) {
        const key = e.code;
        switch (key) {
            case 'KeyW':
                this.state.keys.w = false;
                break;
            case 'KeyA':
                this.state.keys.a = false;
                break;
            case 'KeyS':
                this.state.keys.s = false;
                break;
            case 'KeyD':
                this.state.keys.d = false;
                break;
            case 'KeyQ':
                this.state.keys.q = false;
                this.stopRotation();
                break;
            case 'KeyE':
                this.state.keys.e = false;
                this.stopRotation();
                break;
        }
        if (e.key === 'Shift')
            this.state.keys.shift = false;
        if (e.key === 'Control')
            this.state.keys.ctrl = false;
        if (e.key === 'Alt')
            this.state.keys.alt = false;
    }
    updateEdgeScrolling(e) {
        const rect = this.gameContext.canvas.getBoundingClientRect();
        const x = e.clientX - rect.left;
        const y = e.clientY - rect.top;
        const zone = this.settings.edgeScrollZone;
        let scrollX = 0, scrollY = 0;
        // Left edge
        if (x < zone) {
            scrollX = -1 * (1 - x / zone);
        }
        // Right edge  
        else if (x > rect.width - zone) {
            scrollX = (x - (rect.width - zone)) / zone;
        }
        // Top edge
        if (y < zone) {
            scrollY = -1 * (1 - y / zone);
        }
        // Bottom edge
        else if (y > rect.height - zone) {
            scrollY = (y - (rect.height - zone)) / zone;
        }
        if (scrollX !== 0 || scrollY !== 0) {
            if (!this.state.isEdgeScrollingActive) {
                this.state.velocityX = 0; // Stop momentum
                this.state.velocityY = 0;
                this.state.isEdgeScrollingActive = true;
            }
            const moveAmount = (this.settings.edgeScrollSpeed / this.camera.zoom) * (16 / 1000); // Approximate deltaTime
            this.state.targetX += scrollX * moveAmount;
            this.state.targetY += scrollY * moveAmount;
            this.state.isEdgeScrolling = true; // Keep for compatibility if other logic uses it
        }
        else {
            this.state.isEdgeScrollingActive = false;
            this.state.isEdgeScrolling = false; // Keep for compatibility
        }
    }
    startRotation() {
        this.state.isRotationLocked = true;
        this.state.lastRotationTime = Date.now();
    }
    stopRotation() {
        this.state.isRotationLocked = false;
        // Start auto-reset timer
        setTimeout(() => {
            if (!this.state.isRotationLocked) {
                this.resetToTeamOrientation();
            }
        }, this.settings.rotationTimeout);
    }
    resetToTeamOrientation() {
        const teamRotation = this.settings.teamRotation[this.settings.currentTeam];
        this.state.targetRotation = teamRotation;
        this.camera.targetRotation = teamRotation;
    }
    toggle3DMode() {
        this.settings.enable3D = !this.settings.enable3D;
        console.log(`3D Mode: ${this.settings.enable3D ? 'ON' : 'OFF'}`);
    }
    update(deltaTime) {
        this.updateKeyboardMovement(deltaTime);
        // Momentum is applied to targetX/Y, so it should be calculated before smooth pan.
        // But momentum should only kick in if there's no other active panning.
        this.updateMomentum(deltaTime);
        this.updateSmoothPan(deltaTime);
        if (typeof this.updateSmoothZoom === 'function') {
            this.updateSmoothZoom(deltaTime);
        }
        this.updateRotation(deltaTime);
        this.update3DMode(deltaTime);
        this.clampCameraPosition(); // Clamps actual camera.x/y/zoom
        // After clamping, ensure targetX/Y/Zoom are consistent with the clamped camera values
        // if no other user input is actively changing them. This prevents divergence.
        if (!this.state.isDragging && !this.state.isKeyboardPanningActive && !this.state.isEdgeScrollingActive) {
            this.state.targetX = this.camera.x;
            this.state.targetY = this.camera.y;
        }
        // targetZoom is handled by its own logic (snapping when close)
        this.updateCamera(deltaTime); // Updates renderer camera from this.camera
    }
    updateSmoothPan(deltaTime) {
        const panFactor = this.settings.panSmoothingFactor;
        if (Math.abs(this.camera.x - this.state.targetX) > 0.01) {
            this.camera.x += (this.state.targetX - this.camera.x) * panFactor;
        }
        else {
            this.camera.x = this.state.targetX;
        }
        if (Math.abs(this.camera.y - this.state.targetY) > 0.01) {
            this.camera.y += (this.state.targetY - this.camera.y) * panFactor;
        }
        else {
            this.camera.y = this.state.targetY;
        }
    }
    updateSmoothZoom(deltaTime) {
        if (Math.abs(this.camera.zoom - this.state.targetZoom) > 0.0001) {
            const zoomDiff = this.state.targetZoom - this.camera.zoom;
            const zoomChange = zoomDiff * this.settings.zoomSmoothingFactor;
            // Store world point under cursor before zoom, using current actual zoom
            const mouseWorldPosBefore = this.screenToWorld(this.state.mousePosition.x, this.state.mousePosition.y, this.camera.zoom);
            this.camera.zoom += zoomChange;
            // Adjust camera position to keep mouse cursor over the same world point after this incremental zoom
            const mouseWorldPosAfter = this.screenToWorld(this.state.mousePosition.x, this.state.mousePosition.y, this.camera.zoom);
            this.camera.x += mouseWorldPosBefore.x - mouseWorldPosAfter.x;
            this.camera.y += mouseWorldPosBefore.y - mouseWorldPosAfter.y;
            if (Math.abs(this.camera.zoom - this.state.targetZoom) < 0.0001) {
                this.camera.zoom = this.state.targetZoom;
            }
        }
    }
    updateKeyboardMovement(deltaTime) {
        if (this.state.isDragging) {
            this.state.isKeyboardPanningActive = false; // Ensure flag is off if dragging
            return; // Don't interfere with drag
        }
        if (this.state.keys.w || this.state.keys.a || this.state.keys.s || this.state.keys.d) {
            if (!this.state.isKeyboardPanningActive) {
                this.state.velocityX = 0; // Stop momentum when keyboard panning starts
                this.state.velocityY = 0;
                this.state.isKeyboardPanningActive = true;
            }
        }
        else {
            this.state.isKeyboardPanningActive = false;
        }
        const speed = this.settings.keyboardMoveSpeed / this.camera.zoom;
        const moveSpeed = speed * deltaTime;
        // Apply modifier keys
        const multiplier = this.state.keys.shift ? 3 : this.state.keys.ctrl ? 0.3 : 1;
        const adjustedSpeed = moveSpeed * multiplier;
        if (this.state.keys.w)
            this.state.targetY -= adjustedSpeed;
        if (this.state.keys.s)
            this.state.targetY += adjustedSpeed;
        if (this.state.keys.a)
            this.state.targetX -= adjustedSpeed;
        if (this.state.keys.d)
            this.state.targetX += adjustedSpeed;
        // Rotation
        if (this.state.keys.q) {
            this.state.rotationVelocity = -this.settings.rotationSpeed;
            this.state.lastRotationTime = Date.now();
        }
        if (this.state.keys.e) {
            this.state.rotationVelocity = this.settings.rotationSpeed;
            this.state.lastRotationTime = Date.now();
        }
    }
    updateMomentum(deltaTime) {
        const isActivelyPanning = this.state.isDragging || this.state.isKeyboardPanningActive || this.state.isEdgeScrollingActive;
        // If there's active input or camera is not yet at its target, momentum should not apply or should be reset.
        if (isActivelyPanning) {
            // If user takes over with active panning, kill existing momentum.
            // Exception: Dragging sets its own velocity which then becomes momentum.
            if (!this.state.isDragging) {
                this.state.velocityX = 0;
                this.state.velocityY = 0;
            }
            return;
        }
        // Check if camera has (practically) reached its smooth pan target.
        // Only apply momentum if it has.
        const isAtTargetX = Math.abs(this.camera.x - this.state.targetX) < 0.1; // Increased threshold
        const isAtTargetY = Math.abs(this.camera.y - this.state.targetY) < 0.1; // Increased threshold
        if (isAtTargetX && isAtTargetY) {
            if (Math.abs(this.state.velocityX) > 0.1 || Math.abs(this.state.velocityY) > 0.1) {
                // Apply momentum to the targetX/Y, which camera.x/y will then smoothly follow.
                this.state.targetX += this.state.velocityX * deltaTime;
                this.state.targetY += this.state.velocityY * deltaTime;
            }
        }
        // Decay momentum regardless of whether it was applied this frame,
        // unless dragging is actively setting new velocity.
        if (!this.state.isDragging) {
            this.state.velocityX *= this.settings.momentum;
            this.state.velocityY *= this.settings.momentum;
        }
        this.state.velocityY *= this.settings.momentum;
        // Stop very small velocities
        if (Math.abs(this.state.velocityX) < 1)
            this.state.velocityX = 0;
        if (Math.abs(this.state.velocityY) < 1)
            this.state.velocityY = 0;
    }
    updateRotation(deltaTime) {
        if (this.state.isRotationLocked) {
            // Manual rotation
            this.camera.rotation += this.state.rotationVelocity * deltaTime;
            // Clamp rotation
            const teamDefault = this.settings.teamRotation[this.settings.currentTeam];
            const maxRotation = teamDefault + this.settings.maxRotation;
            const minRotation = teamDefault - this.settings.maxRotation;
            this.camera.rotation = Math.max(minRotation, Math.min(maxRotation, this.camera.rotation));
        }
        else {
            // Auto-reset to team orientation
            const teamDefault = this.settings.teamRotation[this.settings.currentTeam];
            const rotationDiff = teamDefault - this.camera.rotation;
            if (Math.abs(rotationDiff) > 1) {
                const resetSpeed = this.settings.rotationResetSpeed * deltaTime;
                if (rotationDiff > 0) {
                    this.camera.rotation += Math.min(resetSpeed, rotationDiff);
                }
                else {
                    this.camera.rotation += Math.max(-resetSpeed, rotationDiff);
                }
            }
            else {
                this.camera.rotation = teamDefault;
            }
        }
        // Normalize rotation
        while (this.camera.rotation < 0)
            this.camera.rotation += 360;
        while (this.camera.rotation >= 360)
            this.camera.rotation -= 360;
        // Decay rotation velocity
        this.state.rotationVelocity *= 0.9;
    }
    updateCamera(deltaTime) {
        // Update Three.js camera if available
        if (this.gameContext.renderer && this.gameContext.renderer.updateCamera) {
            this.gameContext.renderer.updateCamera(this.camera);
        }
    }
    update3DMode(deltaTime) {
        if (!this.camera.angle && !this.camera.targetAngle) {
            this.camera.angle = 0;
            this.camera.targetAngle = 0;
        }
        const targetAngle = this.settings.enable3D ? this.settings.tiltAngle : 0;
        if (Math.abs(this.camera.targetAngle - targetAngle) > 0.1) {
            this.camera.targetAngle = targetAngle;
        }
        // Smooth angle transition
        const angleDiff = this.camera.targetAngle - this.camera.angle;
        if (Math.abs(angleDiff) > 0.1) {
            const angleSpeed = this.settings.tiltSpeed * deltaTime;
            if (angleDiff > 0) {
                this.camera.angle += Math.min(angleSpeed, angleDiff);
            }
            else {
                this.camera.angle += Math.max(-angleSpeed, angleDiff);
            }
        }
    }
    clampCameraPosition() {
        // Keep camera within world bounds with some margin
        const margin = gameConstants_js_1.WORLD_SIZE * 0.2;
        this.camera.x = Math.max(-margin, Math.min(gameConstants_js_1.WORLD_SIZE + margin, this.camera.x));
        this.camera.y = Math.max(-margin, Math.min(gameConstants_js_1.WORLD_SIZE + margin, this.camera.y));
        // Clamp zoom
        this.camera.zoom = Math.max(this.settings.minZoom, Math.min(this.settings.maxZoom, this.camera.zoom));
    }
    // Focus camera on specific position
    focusOn(x, y, zoom = null) {
        this.camera.x = x; // Snap current camera position
        this.camera.y = y;
        this.state.targetX = x; // Set target for smooth panning
        this.state.targetY = y;
        if (zoom !== null) {
            this.camera.zoom = zoom; // Snap current zoom
            if (this.state.targetZoom !== undefined) { // Check if targetZoom exists
                this.state.targetZoom = zoom; // Set target for smooth zooming
            }
        }
        // Clear momentum
        this.state.velocityX = 0;
        this.state.velocityY = 0;
    }
    // Switch team perspective
    switchTeam(team) {
        if (this.settings.teamRotation[team] !== undefined) {
            this.settings.currentTeam = team;
            this.resetToTeamOrientation();
        }
    }
    // Get current camera state for UI display
    getCameraInfo() {
        return {
            position: { x: Math.round(this.camera.x), y: Math.round(this.camera.y) },
            zoom: this.camera.zoom.toFixed(2),
            rotation: Math.round(this.camera.rotation),
            team: this.settings.currentTeam,
            mode3D: this.settings.enable3D,
            isStrategic: this.camera.zoom < this.settings.strategicZoomThreshold
        };
    }
}
exports.SupComCamera = SupComCamera;

// js/input/supcomCamera.js - Supreme Commander style camera controls
import { WORLD_SIZE } from '../config/gameConstants.js';

export class SupComCamera {
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
            currentTeam: 'blue'
        };
        
        // State tracking
        this.state = {
            isDragging: false,
            isEdgeScrolling: false,
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
        
        if (clampedZoom !== this.camera.zoom) {
            // Zoom to cursor position
            const worldX = (mouseX - this.camera.canvasWidth / 2) / this.camera.zoom + this.camera.x;
            const worldY = (mouseY - this.camera.canvasHeight / 2) / this.camera.zoom + this.camera.y;
            
            this.camera.zoom = clampedZoom;
            
            // Adjust camera position to keep mouse cursor over same world point
            const newWorldX = (mouseX - this.camera.canvasWidth / 2) / this.camera.zoom + this.camera.x;
            const newWorldY = (mouseY - this.camera.canvasHeight / 2) / this.camera.zoom + this.camera.y;
            
            this.camera.x += worldX - newWorldX;
            this.camera.y += worldY - newWorldY;
            
            // Add zoom velocity for smooth follow-up
            this.state.velocityZoom = (clampedZoom - this.camera.zoom) * 0.1;
        }
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
            
            // Move camera (invert for natural drag feel)
            this.camera.x -= worldDeltaX;
            this.camera.y -= worldDeltaY;
            
            // Store velocity for momentum
            this.state.velocityX = -worldDeltaX * 60; // Convert to per-second
            this.state.velocityY = -worldDeltaY * 60;
            
            this.state.lastMouseX = e.clientX;
            this.state.lastMouseY = e.clientY;
        } else {
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
            case 'KeyW': this.state.keys.w = true; break;
            case 'KeyA': this.state.keys.a = true; break;
            case 'KeyS': this.state.keys.s = true; break;
            case 'KeyD': this.state.keys.d = true; break;
            case 'KeyQ': this.state.keys.q = true; this.startRotation(); break;
            case 'KeyE': this.state.keys.e = true; this.startRotation(); break;
        }
        
        if (e.key === 'Shift') this.state.keys.shift = true;
        if (e.key === 'Control') this.state.keys.ctrl = true;
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
            case 'KeyW': this.state.keys.w = false; break;
            case 'KeyA': this.state.keys.a = false; break;
            case 'KeyS': this.state.keys.s = false; break;
            case 'KeyD': this.state.keys.d = false; break;
            case 'KeyQ': this.state.keys.q = false; this.stopRotation(); break;
            case 'KeyE': this.state.keys.e = false; this.stopRotation(); break;
        }
        
        if (e.key === 'Shift') this.state.keys.shift = false;
        if (e.key === 'Control') this.state.keys.ctrl = false;
        if (e.key === 'Alt') this.state.keys.alt = false;
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
            this.state.isEdgeScrolling = true;
            const speed = this.settings.edgeScrollSpeed / this.camera.zoom;
            this.state.velocityX = scrollX * speed;
            this.state.velocityY = scrollY * speed;
        } else {
            this.state.isEdgeScrolling = false;
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
        this.updateMomentum(deltaTime);
        this.updateRotation(deltaTime);
        this.updateCamera(deltaTime);
        this.update3DMode(deltaTime);
        this.clampCameraPosition();
    }
    
    updateKeyboardMovement(deltaTime) {
        if (this.state.isDragging) return; // Don't interfere with drag
        
        const speed = this.settings.keyboardMoveSpeed / this.camera.zoom;
        const moveSpeed = speed * deltaTime;
        
        // Apply modifier keys
        const multiplier = this.state.keys.shift ? 3 : this.state.keys.ctrl ? 0.3 : 1;
        const adjustedSpeed = moveSpeed * multiplier;
        
        if (this.state.keys.w) this.camera.y -= adjustedSpeed;
        if (this.state.keys.s) this.camera.y += adjustedSpeed;
        if (this.state.keys.a) this.camera.x -= adjustedSpeed;
        if (this.state.keys.d) this.camera.x += adjustedSpeed;
        
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
        if (this.state.isDragging || this.state.isEdgeScrolling) return;
        
        // Apply momentum
        this.camera.x += this.state.velocityX * deltaTime;
        this.camera.y += this.state.velocityY * deltaTime;
        
        // Decay momentum
        this.state.velocityX *= this.settings.momentum;
        this.state.velocityY *= this.settings.momentum;
        
        // Stop very small velocities
        if (Math.abs(this.state.velocityX) < 1) this.state.velocityX = 0;
        if (Math.abs(this.state.velocityY) < 1) this.state.velocityY = 0;
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
        } else {
            // Auto-reset to team orientation
            const teamDefault = this.settings.teamRotation[this.settings.currentTeam];
            const rotationDiff = teamDefault - this.camera.rotation;
            
            if (Math.abs(rotationDiff) > 1) {
                const resetSpeed = this.settings.rotationResetSpeed * deltaTime;
                if (rotationDiff > 0) {
                    this.camera.rotation += Math.min(resetSpeed, rotationDiff);
                } else {
                    this.camera.rotation += Math.max(-resetSpeed, rotationDiff);
                }
            } else {
                this.camera.rotation = teamDefault;
            }
        }
        
        // Normalize rotation
        while (this.camera.rotation < 0) this.camera.rotation += 360;
        while (this.camera.rotation >= 360) this.camera.rotation -= 360;
        
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
            } else {
                this.camera.angle += Math.max(-angleSpeed, angleDiff);
            }
        }
    }
    
    clampCameraPosition() {
        // Keep camera within world bounds with some margin
        const margin = WORLD_SIZE * 0.2;
        this.camera.x = Math.max(-margin, Math.min(WORLD_SIZE + margin, this.camera.x));
        this.camera.y = Math.max(-margin, Math.min(WORLD_SIZE + margin, this.camera.y));
        
        // Clamp zoom
        this.camera.zoom = Math.max(this.settings.minZoom, Math.min(this.settings.maxZoom, this.camera.zoom));
    }
    
    // Focus camera on specific position
    focusOn(x, y, zoom = null) {
        this.camera.x = x;
        this.camera.y = y;
        if (zoom !== null) {
            this.camera.zoom = zoom;
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
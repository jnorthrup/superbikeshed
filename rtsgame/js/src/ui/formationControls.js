"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.FormationControls = void 0;
const commandConfig_js_1 = require("../config/commandConfig.js");
class FormationControls {
    constructor(canvas, ctx, formationSystem) {
        this.canvas = canvas;
        this.ctx = ctx;
        this.formationSystem = formationSystem;
        this.visible = false;
        this.selectedUnits = new Set();
        this.formationShapes = ['LINE', 'COLUMN', 'WEDGE', 'CIRCLE'];
        this.currentShape = 'LINE';
        this.buttonSize = 40;
        this.padding = 10;
        this.isDragging = false;
        this.dragStart = { x: 0, y: 0 };
    }
    toggle() {
        this.visible = !this.visible;
    }
    setSelectedUnits(units) {
        this.selectedUnits = new Set(units);
    }
    render() {
        if (!this.visible || this.selectedUnits.size === 0)
            return;
        const ctx = this.ctx;
        const startX = this.canvas.width - (this.buttonSize + this.padding) * this.formationShapes.length;
        const startY = this.padding;
        // Draw background
        ctx.fillStyle = 'rgba(0, 0, 0, 0.8)';
        ctx.fillRect(startX - this.padding, startY - this.padding, (this.buttonSize + this.padding) * this.formationShapes.length + this.padding * 2, this.buttonSize + this.padding * 2);
        // Draw formation shape buttons
        this.formationShapes.forEach((shape, index) => {
            const x = startX + index * (this.buttonSize + this.padding);
            const y = startY;
            // Draw button background
            ctx.fillStyle = shape === this.currentShape ? '#4CAF50' : '#333333';
            ctx.fillRect(x, y, this.buttonSize, this.buttonSize);
            // Draw shape icon
            ctx.strokeStyle = '#ffffff';
            ctx.lineWidth = 2;
            this.drawFormationIcon(ctx, x, y, shape);
            // Draw shape name
            ctx.fillStyle = '#ffffff';
            ctx.font = '12px Arial';
            ctx.textAlign = 'center';
            ctx.fillText(shape, x + this.buttonSize / 2, y + this.buttonSize + 15);
        });
        // Draw formation options if a formation is active
        const leader = this.getSelectedLeader();
        if (leader && leader.formation) {
            this.drawFormationOptions(leader.formation);
        }
    }
    drawFormationIcon(ctx, x, y, shape) {
        const centerX = x + this.buttonSize / 2;
        const centerY = y + this.buttonSize / 2;
        const size = this.buttonSize * 0.6;
        ctx.beginPath();
        switch (shape) {
            case 'LINE':
                // Draw horizontal line with dots
                ctx.moveTo(centerX - size / 2, centerY);
                ctx.lineTo(centerX + size / 2, centerY);
                ctx.stroke();
                // Draw dots
                for (let i = -1; i <= 1; i++) {
                    ctx.beginPath();
                    ctx.arc(centerX + i * size / 2, centerY, 2, 0, Math.PI * 2);
                    ctx.fill();
                }
                break;
            case 'COLUMN':
                // Draw vertical line with dots
                ctx.moveTo(centerX, centerY - size / 2);
                ctx.lineTo(centerX, centerY + size / 2);
                ctx.stroke();
                // Draw dots
                for (let i = -1; i <= 1; i++) {
                    ctx.beginPath();
                    ctx.arc(centerX, centerY + i * size / 2, 2, 0, Math.PI * 2);
                    ctx.fill();
                }
                break;
            case 'WEDGE':
                // Draw V shape
                ctx.moveTo(centerX, centerY - size / 2);
                ctx.lineTo(centerX - size / 2, centerY + size / 2);
                ctx.lineTo(centerX + size / 2, centerY + size / 2);
                ctx.closePath();
                ctx.stroke();
                // Draw dots
                ctx.beginPath();
                ctx.arc(centerX, centerY - size / 2, 2, 0, Math.PI * 2);
                ctx.arc(centerX - size / 2, centerY + size / 2, 2, 0, Math.PI * 2);
                ctx.arc(centerX + size / 2, centerY + size / 2, 2, 0, Math.PI * 2);
                ctx.fill();
                break;
            case 'CIRCLE':
                // Draw circle with dots
                ctx.beginPath();
                ctx.arc(centerX, centerY, size / 2, 0, Math.PI * 2);
                ctx.stroke();
                // Draw dots
                for (let i = 0; i < 4; i++) {
                    const angle = i * Math.PI / 2;
                    ctx.beginPath();
                    ctx.arc(centerX + Math.cos(angle) * size / 2, centerY + Math.sin(angle) * size / 2, 2, 0, Math.PI * 2);
                    ctx.fill();
                }
                break;
        }
    }
    drawFormationOptions(formation) {
        const ctx = this.ctx;
        const startX = this.padding;
        const startY = this.canvas.height - 150;
        // Draw options panel
        ctx.fillStyle = 'rgba(0, 0, 0, 0.8)';
        ctx.fillRect(startX, startY, 200, 140);
        // Draw title
        ctx.fillStyle = '#ffffff';
        ctx.font = 'bold 14px Arial';
        ctx.textAlign = 'left';
        ctx.fillText('Formation Options', startX + 10, startY + 25);
        // Draw spacing slider
        this.drawSlider(ctx, startX + 10, startY + 40, 180, 'Spacing', formation.options.spacing, commandConfig_js_1.COMMAND_CONFIG.COMMAND_RANGES.INDIVIDUAL, commandConfig_js_1.COMMAND_CONFIG.COMMAND_RANGES.STRATEGIC);
        // Draw width slider
        this.drawSlider(ctx, startX + 10, startY + 80, 180, 'Width', formation.options.width, 1, 10);
        // Draw depth slider
        this.drawSlider(ctx, startX + 10, startY + 120, 180, 'Depth', formation.options.depth, 1, 5);
    }
    drawSlider(ctx, x, y, width, label, value, min, max) {
        // Draw label
        ctx.fillStyle = '#ffffff';
        ctx.font = '12px Arial';
        ctx.fillText(`${label}: ${value.toFixed(1)}`, x, y);
        // Draw slider track
        ctx.fillStyle = '#333333';
        ctx.fillRect(x, y + 5, width, 10);
        // Draw slider handle
        const handleX = x + (value - min) / (max - min) * width;
        ctx.fillStyle = '#4CAF50';
        ctx.beginPath();
        ctx.arc(handleX, y + 10, 8, 0, Math.PI * 2);
        ctx.fill();
    }
    handleClick(x, y) {
        if (!this.visible)
            return false;
        // Check formation shape buttons
        const startX = this.canvas.width - (this.buttonSize + this.padding) * this.formationShapes.length;
        const startY = this.padding;
        for (let i = 0; i < this.formationShapes.length; i++) {
            const buttonX = startX + i * (this.buttonSize + this.padding);
            const buttonY = startY;
            if (x >= buttonX && x <= buttonX + this.buttonSize &&
                y >= buttonY && y <= buttonY + this.buttonSize) {
                this.handleFormationShapeClick(this.formationShapes[i]);
                return true;
            }
        }
        // Check formation options
        const leader = this.getSelectedLeader();
        if (leader && leader.formation) {
            const optionsStartX = this.padding;
            const optionsStartY = this.canvas.height - 150;
            if (x >= optionsStartX && x <= optionsStartX + 200 &&
                y >= optionsStartY && y <= optionsStartY + 140) {
                this.handleFormationOptionsClick(x, y, leader.formation);
                return true;
            }
        }
        return false;
    }
    handleFormationShapeClick(shape) {
        this.currentShape = shape;
        // Create or update formation
        const leader = this.getSelectedLeader();
        if (leader) {
            if (leader.formation) {
                this.formationSystem.changeFormationShape(leader.formation, shape);
            }
            else {
                const formation = this.formationSystem.createFormation(leader, shape);
                for (const unit of this.selectedUnits) {
                    if (unit !== leader) {
                        this.formationSystem.addFollower(formation, unit);
                    }
                }
            }
        }
    }
    handleFormationOptionsClick(x, y, formation) {
        const optionsStartX = this.padding;
        const optionsStartY = this.canvas.height - 150;
        // Check spacing slider
        if (y >= optionsStartY + 35 && y <= optionsStartY + 55) {
            const value = this.calculateSliderValue(x, optionsStartX + 10, optionsStartX + 190, commandConfig_js_1.COMMAND_CONFIG.COMMAND_RANGES.INDIVIDUAL, commandConfig_js_1.COMMAND_CONFIG.COMMAND_RANGES.STRATEGIC);
            formation.options.spacing = value;
        }
        // Check width slider
        if (y >= optionsStartY + 75 && y <= optionsStartY + 95) {
            const value = this.calculateSliderValue(x, optionsStartX + 10, optionsStartX + 190, 1, 10);
            formation.options.width = Math.round(value);
        }
        // Check depth slider
        if (y >= optionsStartY + 115 && y <= optionsStartY + 135) {
            const value = this.calculateSliderValue(x, optionsStartX + 10, optionsStartX + 190, 1, 5);
            formation.options.depth = Math.round(value);
        }
    }
    calculateSliderValue(x, minX, maxX, minValue, maxValue) {
        const ratio = (x - minX) / (maxX - minX);
        return minValue + ratio * (maxValue - minValue);
    }
    getSelectedLeader() {
        // Find the unit with highest command authority
        let leader = null;
        let maxAuthority = -1;
        for (const unit of this.selectedUnits) {
            if (unit.effectiveAuthority > maxAuthority) {
                maxAuthority = unit.effectiveAuthority;
                leader = unit;
            }
        }
        return leader;
    }
}
exports.FormationControls = FormationControls;

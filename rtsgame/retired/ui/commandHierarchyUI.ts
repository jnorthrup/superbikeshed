import { COMMAND_CONFIG } from '../config/commandConfig.js';

export class CommandHierarchyUI {
    constructor(canvas, ctx) {
        this.canvas = canvas;
        this.ctx = ctx;
        this.visible = false;
        this.selectedUnit = null;
    }

    toggle() {
        this.visible = !this.visible;
    }

    setSelectedUnit(unit) {
        this.selectedUnit = unit;
    }

    render() {
        if (!this.visible || !this.selectedUnit) return;

        const ctx = this.ctx;
        const unit = this.selectedUnit;

        // Draw command hierarchy info panel
        this.drawInfoPanel(unit);
        
        // Draw authority visualization
        this.drawAuthorityVisualization(unit);
        
        // Draw veterancy status
        this.drawVeterancyStatus(unit);
    }

    drawInfoPanel(unit) {
        const ctx = this.ctx;
        const padding = 10;
        const lineHeight = 20;
        let y = 50;

        // Panel background
        ctx.fillStyle = 'rgba(0, 0, 0, 0.8)';
        ctx.fillRect(10, 10, 300, 200);

        // Title
        ctx.fillStyle = '#ffffff';
        ctx.font = 'bold 16px Arial';
        ctx.fillText('Command Hierarchy', 20, y);
        y += lineHeight * 2;

        // Unit info
        ctx.font = '14px Arial';
        ctx.fillText(`Rank: ${unit.militaryRank}`, 20, y);
        y += lineHeight;
        ctx.fillText(`Authority: ${unit.effectiveAuthority.toFixed(1)}`, 20, y);
        y += lineHeight;
        ctx.fillText(`Veterancy: ${unit.veterancyLevel}`, 20, y);
        y += lineHeight;
        ctx.fillText(`Command Fitness: ${unit.commandFitness}`, 20, y);
    }

    drawAuthorityVisualization(unit) {
        const ctx = this.ctx;
        const centerX = 160;
        const centerY = 250;
        const radius = 50;

        // Draw authority circle
        ctx.beginPath();
        ctx.arc(centerX, centerY, radius, 0, Math.PI * 2);
        ctx.strokeStyle = '#444444';
        ctx.lineWidth = 2;
        ctx.stroke();

        // Draw authority segments
        const segments = [
            { value: unit.baseAuthority, color: '#4CAF50', label: 'Base' },
            { value: unit.healthAuthorityModifier, color: '#2196F3', label: 'Health' },
            { value: unit.veterancyAuthorityModifier, color: '#FFC107', label: 'Veterancy' },
            { value: unit.contextAuthorityModifier, color: '#9C27B0', label: 'Context' },
            { value: unit.computroniumAuthorityModifier, color: '#F44336', label: 'Core' }
        ];

        let startAngle = 0;
        segments.forEach(segment => {
            const angle = (segment.value / unit.effectiveAuthority) * Math.PI * 2;
            
            ctx.beginPath();
            ctx.moveTo(centerX, centerY);
            ctx.arc(centerX, centerY, radius, startAngle, startAngle + angle);
            ctx.closePath();
            
            ctx.fillStyle = segment.color;
            ctx.fill();
            
            // Draw label
            const labelAngle = startAngle + angle / 2;
            const labelX = centerX + Math.cos(labelAngle) * (radius + 20);
            const labelY = centerY + Math.sin(labelAngle) * (radius + 20);
            
            ctx.fillStyle = '#ffffff';
            ctx.font = '12px Arial';
            ctx.fillText(segment.label, labelX - 15, labelY + 5);
            
            startAngle += angle;
        });
    }

    drawVeterancyStatus(unit) {
        const ctx = this.ctx;
        const x = 20;
        let y = 350;

        // Veterancy progress bar
        const progress = unit.combatExperience / COMMAND_CONFIG.VETERANCY_THRESHOLDS[unit.veterancyLevel];
        const barWidth = 200;
        const barHeight = 20;

        // Background
        ctx.fillStyle = '#333333';
        ctx.fillRect(x, y, barWidth, barHeight);

        // Progress
        ctx.fillStyle = '#4CAF50';
        ctx.fillRect(x, y, barWidth * progress, barHeight);

        // Border
        ctx.strokeStyle = '#ffffff';
        ctx.strokeRect(x, y, barWidth, barHeight);

        // Text
        ctx.fillStyle = '#ffffff';
        ctx.font = '14px Arial';
        ctx.fillText(`Veterancy Progress: ${(progress * 100).toFixed(1)}%`, x, y - 5);

        // Benefits
        y += 40;
        ctx.fillText('Veterancy Benefits:', x, y);
        y += 20;
        if (unit.canPromoteSubordinates) {
            ctx.fillText('✓ Can Promote Subordinates', x, y);
            y += 20;
        }
        if (unit.provideMoraleBonus) {
            ctx.fillText('✓ Provides Morale Bonus', x, y);
        }
    }
} 
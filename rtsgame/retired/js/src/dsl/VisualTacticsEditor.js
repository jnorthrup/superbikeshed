"use strict";
/**
 * Visual Tactics Editor - Blockly-style interface for TacticsDSL
 *
 * Features:
 * - Lightning-fast hotkey-driven block placement
 * - Real-time command preview
 * - APM optimization suggestions
 * - Macro recording with visual feedback
 * - Context-sensitive block recommendations
 */
Object.defineProperty(exports, "__esModule", { value: true });
exports.VisualTacticsEditor = void 0;
const TacticsDSL_js_1 = require("./TacticsDSL.js");
class VisualTacticsEditor {
    constructor(canvasElement, gameState) {
        this.canvas = canvasElement;
        this.ctx = this.canvas.getContext('2d');
        this.gameState = gameState;
        this.composer = new TacticsDSL_js_1.APMComposer();
        this.layout = new TacticsDSL_js_1.BlockLayout();
        // Editor state
        this.selectedBlocks = new Set();
        this.dragging = null;
        this.connectionPreview = null;
        this.hotKeyMode = false;
        this.quickCommandMode = false;
        this.apmCounter = new APMCounter();
        // Visual elements
        this.blocks = new Map();
        this.connections = [];
        this.palette = this.createVisualPalette();
        this.minimap = new TacticsMinimap();
        // Performance optimization
        this.dirtyRegions = new Set();
        this.lastFrameTime = 0;
        this.targetFPS = 60;
        this.setupEventListeners();
        this.setupHotkeys();
        this.startRenderLoop();
    }
    createVisualPalette() {
        const paletteConfig = this.layout.palette;
        const palette = [];
        let y = 10;
        for (const [category, config] of Object.entries(paletteConfig)) {
            palette.push({
                category,
                x: 10,
                y,
                width: 200,
                height: 30,
                color: config.color,
                icon: config.icon,
                expanded: false,
                blocks: config.blocks.map((blockType, index) => ({
                    type: blockType,
                    x: 220,
                    y: y + (index * 35),
                    width: 180,
                    height: 30,
                    visible: false,
                    hotkey: this.getBlockHotkey(blockType)
                }))
            });
            y += 40;
        }
        return palette;
    }
    getBlockHotkey(blockType) {
        const hotkeyMap = {
            'SELECT': 'S',
            'SELECT_ALL': 'Ctrl+A',
            'MOVE': 'M',
            'ATTACK': 'A',
            'BUILD': 'B',
            'WHEN': 'W',
            'IF_ELSE': 'I',
            'DELAY': 'D',
            'PARALLEL': 'Ctrl+P',
            'SEQUENCE': 'Ctrl+Q'
        };
        return hotkeyMap[blockType] || blockType[0];
    }
    setupEventListeners() {
        // Mouse events for drag & drop
        this.canvas.addEventListener('mousedown', this.onMouseDown.bind(this));
        this.canvas.addEventListener('mousemove', this.onMouseMove.bind(this));
        this.canvas.addEventListener('mouseup', this.onMouseUp.bind(this));
        this.canvas.addEventListener('wheel', this.onWheel.bind(this));
        // Touch events for mobile APM
        this.canvas.addEventListener('touchstart', this.onTouchStart.bind(this));
        this.canvas.addEventListener('touchmove', this.onTouchMove.bind(this));
        this.canvas.addEventListener('touchend', this.onTouchEnd.bind(this));
        // Context menu for quick actions
        this.canvas.addEventListener('contextmenu', this.onContextMenu.bind(this));
    }
    setupHotkeys() {
        document.addEventListener('keydown', (e) => {
            this.apmCounter.recordAction();
            // Toggle hotkey mode
            if (e.key === '`') {
                this.hotKeyMode = !this.hotKeyMode;
                this.showHotkeyOverlay(this.hotKeyMode);
                return;
            }
            // Quick command mode
            if (e.key === 'Tab') {
                e.preventDefault();
                this.quickCommandMode = !this.quickCommandMode;
                this.showQuickCommandPanel(this.quickCommandMode);
                return;
            }
            if (this.hotKeyMode) {
                this.handleHotkeyAction(e);
            }
            else {
                this.handleNormalKeyAction(e);
            }
        });
    }
    handleHotkeyAction(event) {
        const key = this.getKeyCombo(event);
        // Speed placement hotkeys
        const speedPlacements = {
            'S': () => this.placeBlockAtCursor('SELECT'),
            'M': () => this.placeBlockAtCursor('MOVE'),
            'A': () => this.placeBlockAtCursor('ATTACK'),
            'B': () => this.placeBlockAtCursor('BUILD'),
            'W': () => this.placeBlockAtCursor('WHEN'),
            'D': () => this.placeBlockAtCursor('DELAY'),
            'Ctrl+P': () => this.placeBlockAtCursor('PARALLEL'),
            'Ctrl+Q': () => this.placeBlockAtCursor('SEQUENCE')
        };
        if (speedPlacements[key]) {
            speedPlacements[key]();
            this.apmCounter.recordAction();
        }
        // Block modification hotkeys
        if (this.selectedBlocks.size > 0) {
            const modifications = {
                'Delete': () => this.deleteSelectedBlocks(),
                'Ctrl+C': () => this.copySelectedBlocks(),
                'Ctrl+V': () => this.pasteBlocks(),
                'Ctrl+D': () => this.duplicateSelectedBlocks(),
                'Ctrl+G': () => this.groupSelectedBlocks(),
                'Ctrl+U': () => this.ungroupSelectedBlocks()
            };
            if (modifications[key]) {
                modifications[key]();
                this.apmCounter.recordAction();
            }
        }
    }
    placeBlockAtCursor(blockType) {
        const mousePos = this.getMousePosition();
        const block = this.createBlock(blockType, mousePos.x, mousePos.y);
        this.blocks.set(block.id, block);
        this.selectedBlocks.clear();
        this.selectedBlocks.add(block.id);
        this.markDirty(block);
        // Auto-connect if cursor is near a connection point
        this.attemptAutoConnection(block);
    }
    attemptAutoConnection(newBlock) {
        const connectionDistance = 50;
        for (const [id, existingBlock] of this.blocks) {
            if (id === newBlock.id)
                continue;
            const distance = Math.sqrt(Math.pow(newBlock.x - existingBlock.x, 2) +
                Math.pow(newBlock.y - existingBlock.y, 2));
            if (distance < connectionDistance) {
                this.createConnection(existingBlock.id, newBlock.id);
                break;
            }
        }
    }
    createBlock(type, x, y) {
        const blockConfig = this.getBlockConfig(type);
        return {
            id: `block_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
            type,
            x: this.layout.snapToGrid(x, y).x,
            y: this.layout.snapToGrid(x, y).y,
            width: blockConfig.width,
            height: blockConfig.height,
            color: blockConfig.color,
            icon: blockConfig.icon,
            text: blockConfig.text,
            hotkey: blockConfig.hotkey,
            params: {},
            connections: {
                inputs: [],
                outputs: []
            },
            animations: {
                pulse: 0,
                glow: 0,
                bounce: 0
            }
        };
    }
    getBlockConfig(type) {
        const configs = {
            'SELECT': {
                width: 120, height: 40, color: '#4CAF50',
                icon: '👆', text: 'Select Units', hotkey: 'S'
            },
            'MOVE': {
                width: 100, height: 40, color: '#2196F3',
                icon: '🚀', text: 'Move', hotkey: 'M'
            },
            'ATTACK': {
                width: 100, height: 40, color: '#F44336',
                icon: '⚔️', text: 'Attack', hotkey: 'A'
            },
            'BUILD': {
                width: 100, height: 40, color: '#FF9800',
                icon: '🏭', text: 'Build', hotkey: 'B'
            },
            'WHEN': {
                width: 120, height: 40, color: '#9C27B0',
                icon: '🧠', text: 'When', hotkey: 'W'
            },
            'DELAY': {
                width: 100, height: 40, color: '#607D8B',
                icon: '⏱️', text: 'Delay', hotkey: 'D'
            },
            'PARALLEL': {
                width: 130, height: 40, color: '#795548',
                icon: '⚡', text: 'Parallel', hotkey: 'Ctrl+P'
            },
            'SEQUENCE': {
                width: 130, height: 40, color: '#795548',
                icon: '📋', text: 'Sequence', hotkey: 'Ctrl+Q'
            }
        };
        return configs[type] || configs['SELECT'];
    }
    // Rendering system with performance optimization
    startRenderLoop() {
        const render = (timestamp) => {
            const deltaTime = timestamp - this.lastFrameTime;
            if (deltaTime >= 1000 / this.targetFPS) {
                this.update(deltaTime);
                this.render();
                this.lastFrameTime = timestamp;
            }
            requestAnimationFrame(render);
        };
        requestAnimationFrame(render);
    }
    render() {
        // Clear only dirty regions for performance
        if (this.dirtyRegions.size === 0)
            return;
        this.ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
        // Render background grid
        this.renderGrid();
        // Render palette
        this.renderPalette();
        // Render connections first (behind blocks)
        this.renderConnections();
        // Render blocks
        for (const block of this.blocks.values()) {
            this.renderBlock(block);
        }
        // Render selection indicators
        this.renderSelections();
        // Render overlays
        if (this.hotKeyMode)
            this.renderHotkeyOverlay();
        if (this.quickCommandMode)
            this.renderQuickCommandPanel();
        // Render APM counter
        this.renderAPMCounter();
        // Render minimap
        this.minimap.render(this.ctx, this.blocks, this.connections);
        this.dirtyRegions.clear();
    }
    renderBlock(block) {
        const ctx = this.ctx;
        const { x, y, width, height, color, icon, text, hotkey } = block;
        // Apply animations
        const pulse = Math.sin(block.animations.pulse) * 2;
        const glow = block.animations.glow;
        // Shadow
        ctx.shadowColor = 'rgba(0, 0, 0, 0.3)';
        ctx.shadowBlur = 5 + glow;
        ctx.shadowOffsetX = 2;
        ctx.shadowOffsetY = 2;
        // Main block
        ctx.fillStyle = color;
        ctx.roundRect(x - pulse, y - pulse, width + pulse * 2, height + pulse * 2, 8);
        ctx.fill();
        // Selection indicator
        if (this.selectedBlocks.has(block.id)) {
            ctx.strokeStyle = '#FFD700';
            ctx.lineWidth = 3;
            ctx.stroke();
        }
        // Reset shadow
        ctx.shadowColor = 'transparent';
        // Icon
        ctx.font = '16px Arial';
        ctx.fillStyle = 'white';
        ctx.fillText(icon, x + 8, y + 25);
        // Text
        ctx.font = '12px Arial';
        ctx.fillText(text, x + 30, y + 25);
        // Hotkey indicator
        if (this.hotKeyMode && hotkey) {
            ctx.font = '10px Arial';
            ctx.fillStyle = '#FFD700';
            ctx.fillText(hotkey, x + width - 30, y + 12);
        }
        // Connection points
        this.renderConnectionPoints(block);
    }
    renderConnectionPoints(block) {
        const ctx = this.ctx;
        const pointSize = 6;
        // Input point (top)
        if (block.type !== 'SELECT') {
            ctx.fillStyle = '#333';
            ctx.beginPath();
            ctx.arc(block.x + block.width / 2, block.y, pointSize, 0, Math.PI * 2);
            ctx.fill();
        }
        // Output point (bottom)
        ctx.fillStyle = '#333';
        ctx.beginPath();
        ctx.arc(block.x + block.width / 2, block.y + block.height, pointSize, 0, Math.PI * 2);
        ctx.fill();
    }
    renderConnections() {
        const ctx = this.ctx;
        for (const connection of this.connections) {
            const fromBlock = this.blocks.get(connection.from);
            const toBlock = this.blocks.get(connection.to);
            if (!fromBlock || !toBlock)
                continue;
            const fromX = fromBlock.x + fromBlock.width / 2;
            const fromY = fromBlock.y + fromBlock.height;
            const toX = toBlock.x + toBlock.width / 2;
            const toY = toBlock.y;
            // Bezier curve for smooth connections
            const controlOffset = Math.abs(toY - fromY) / 2;
            ctx.strokeStyle = '#666';
            ctx.lineWidth = 3;
            ctx.beginPath();
            ctx.moveTo(fromX, fromY);
            ctx.bezierCurveTo(fromX, fromY + controlOffset, toX, toY - controlOffset, toX, toY);
            ctx.stroke();
            // Arrow head
            this.renderArrowHead(ctx, toX, toY, Math.PI / 2);
        }
    }
    renderArrowHead(ctx, x, y, angle) {
        const size = 8;
        ctx.save();
        ctx.translate(x, y);
        ctx.rotate(angle);
        ctx.fillStyle = '#666';
        ctx.beginPath();
        ctx.moveTo(0, 0);
        ctx.lineTo(-size, -size);
        ctx.lineTo(-size, size);
        ctx.closePath();
        ctx.fill();
        ctx.restore();
    }
    renderAPMCounter() {
        const ctx = this.ctx;
        const apm = this.apmCounter.getCurrentAPM();
        const efficiency = this.apmCounter.getEfficiency();
        // APM display
        ctx.fillStyle = 'rgba(0, 0, 0, 0.8)';
        ctx.fillRect(this.canvas.width - 200, 10, 180, 60);
        ctx.fillStyle = '#00FF00';
        ctx.font = 'bold 16px Arial';
        ctx.fillText(`APM: ${apm}`, this.canvas.width - 190, 30);
        ctx.fillStyle = efficiency > 0.8 ? '#00FF00' : efficiency > 0.5 ? '#FFFF00' : '#FF0000';
        ctx.font = '12px Arial';
        ctx.fillText(`Efficiency: ${(efficiency * 100).toFixed(1)}%`, this.canvas.width - 190, 50);
        // APM graph
        this.renderAPMGraph();
    }
    renderAPMGraph() {
        const ctx = this.ctx;
        const history = this.apmCounter.getHistory();
        const graphX = this.canvas.width - 190;
        const graphY = 80;
        const graphWidth = 160;
        const graphHeight = 40;
        ctx.strokeStyle = '#333';
        ctx.strokeRect(graphX, graphY, graphWidth, graphHeight);
        ctx.strokeStyle = '#00FF00';
        ctx.lineWidth = 2;
        ctx.beginPath();
        history.forEach((apm, index) => {
            const x = graphX + (index / history.length) * graphWidth;
            const y = graphY + graphHeight - (apm / 300) * graphHeight; // Max 300 APM
            if (index === 0) {
                ctx.moveTo(x, y);
            }
            else {
                ctx.lineTo(x, y);
            }
        });
        ctx.stroke();
    }
    // Quick command panel for speed
    renderQuickCommandPanel() {
        const ctx = this.ctx;
        const panelX = this.canvas.width / 2 - 200;
        const panelY = 50;
        const panelWidth = 400;
        const panelHeight = 300;
        // Panel background
        ctx.fillStyle = 'rgba(0, 0, 0, 0.9)';
        ctx.roundRect(panelX, panelY, panelWidth, panelHeight, 10);
        ctx.fill();
        // Title
        ctx.fillStyle = '#FFD700';
        ctx.font = 'bold 18px Arial';
        ctx.fillText('Quick Commands', panelX + 20, panelY + 30);
        // Quick command buttons
        const commands = [
            { text: 'Select Army (Q)', key: 'Q', action: 'select_army' },
            { text: 'Attack Move (A)', key: 'A', action: 'attack_move' },
            { text: 'Retreat (R)', key: 'R', action: 'retreat' },
            { text: 'Build Workers (W)', key: 'W', action: 'build_workers' },
            { text: 'Patrol (P)', key: 'P', action: 'patrol' },
            { text: 'Formation (F)', key: 'F', action: 'formation' }
        ];
        commands.forEach((cmd, index) => {
            const btnX = panelX + 20 + (index % 2) * 180;
            const btnY = panelY + 60 + Math.floor(index / 2) * 40;
            ctx.fillStyle = '#444';
            ctx.roundRect(btnX, btnY, 160, 30, 5);
            ctx.fill();
            ctx.fillStyle = '#FFF';
            ctx.font = '12px Arial';
            ctx.fillText(cmd.text, btnX + 10, btnY + 20);
        });
    }
    // Mouse and touch event handlers
    onMouseDown(event) {
        const pos = this.getMousePosition(event);
        this.apmCounter.recordAction();
        // Check if clicking on a block
        const clickedBlock = this.getBlockAt(pos.x, pos.y);
        if (clickedBlock) {
            this.handleBlockClick(clickedBlock, event);
            return;
        }
        // Check if clicking on palette
        const paletteItem = this.getPaletteItemAt(pos.x, pos.y);
        if (paletteItem) {
            this.handlePaletteClick(paletteItem);
            return;
        }
        // Start selection rectangle
        this.startSelectionRectangle(pos);
    }
    handleBlockClick(block, event) {
        if (event.ctrlKey) {
            // Multi-select
            if (this.selectedBlocks.has(block.id)) {
                this.selectedBlocks.delete(block.id);
            }
            else {
                this.selectedBlocks.add(block.id);
            }
        }
        else {
            // Single select
            this.selectedBlocks.clear();
            this.selectedBlocks.add(block.id);
        }
        // Start dragging
        this.dragging = {
            blocks: Array.from(this.selectedBlocks),
            startX: event.clientX,
            startY: event.clientY,
            originalPositions: new Map()
        };
        // Store original positions
        for (const blockId of this.selectedBlocks) {
            const blockData = this.blocks.get(blockId);
            this.dragging.originalPositions.set(blockId, { x: blockData.x, y: blockData.y });
        }
        this.markAllDirty();
    }
    createConnection(fromBlockId, toBlockId) {
        // Prevent duplicate connections
        const existing = this.connections.find(conn => conn.from === fromBlockId && conn.to === toBlockId);
        if (!existing) {
            this.connections.push({
                id: `conn_${Date.now()}`,
                from: fromBlockId,
                to: toBlockId,
                type: 'sequence'
            });
            // Update block connection data
            const fromBlock = this.blocks.get(fromBlockId);
            const toBlock = this.blocks.get(toBlockId);
            if (fromBlock)
                fromBlock.connections.outputs.push(toBlockId);
            if (toBlock)
                toBlock.connections.inputs.push(fromBlockId);
            this.markAllDirty();
        }
    }
    // Utility methods
    getMousePosition(event) {
        const rect = this.canvas.getBoundingClientRect();
        return {
            x: event.clientX - rect.left,
            y: event.clientY - rect.top
        };
    }
    getBlockAt(x, y) {
        for (const block of this.blocks.values()) {
            if (x >= block.x && x <= block.x + block.width &&
                y >= block.y && y <= block.y + block.height) {
                return block;
            }
        }
        return null;
    }
    markDirty(block) {
        this.dirtyRegions.add(block.id);
    }
    markAllDirty() {
        this.dirtyRegions.add('all');
    }
    getKeyCombo(event) {
        const parts = [];
        if (event.ctrlKey)
            parts.push('Ctrl');
        if (event.shiftKey)
            parts.push('Shift');
        if (event.altKey)
            parts.push('Alt');
        parts.push(event.key);
        return parts.join('+');
    }
    // Export compiled program
    exportProgram() {
        const expressions = [];
        // Find root blocks (no inputs)
        const rootBlocks = Array.from(this.blocks.values()).filter(block => block.connections.inputs.length === 0);
        for (const rootBlock of rootBlocks) {
            expressions.push(this.blockToExpression(rootBlock));
        }
        return {
            expressions,
            metadata: {
                blocksCount: this.blocks.size,
                connectionsCount: this.connections.length,
                estimatedAPM: this.estimateExecutionAPM(expressions),
                complexity: this.calculateComplexity(expressions)
            }
        };
    }
    blockToExpression(block) {
        // Convert visual block back to TacticsDSL expression
        // This would be implemented based on block type and parameters
        return new TacticsDSL_js_1.CommandBlocks[block.type]();
    }
    estimateExecutionAPM(expressions) {
        // Estimate APM required to execute this program manually
        return expressions.length * 3; // Rough estimate
    }
    calculateComplexity(expressions) {
        // Calculate program complexity score
        let complexity = 0;
        expressions.forEach(expr => {
            complexity += 1; // Base complexity
            complexity += expr.children ? expr.children.length : 0;
        });
        return complexity;
    }
}
exports.VisualTacticsEditor = VisualTacticsEditor;
// APM Counter for performance tracking
class APMCounter {
    constructor() {
        this.actions = [];
        this.startTime = Date.now();
        this.history = [];
        this.maxHistoryLength = 60; // 1 minute of 1-second samples
    }
    recordAction() {
        this.actions.push(Date.now());
        // Remove actions older than 1 minute
        const cutoff = Date.now() - 60000;
        this.actions = this.actions.filter(time => time > cutoff);
    }
    getCurrentAPM() {
        return this.actions.length;
    }
    getEfficiency() {
        // Calculate efficiency based on action patterns
        if (this.actions.length < 2)
            return 1.0;
        const intervals = [];
        for (let i = 1; i < this.actions.length; i++) {
            intervals.push(this.actions[i] - this.actions[i - 1]);
        }
        const avgInterval = intervals.reduce((a, b) => a + b, 0) / intervals.length;
        const variance = intervals.reduce((acc, interval) => acc + Math.pow(interval - avgInterval, 2), 0) / intervals.length;
        // Lower variance = higher efficiency
        return Math.max(0, 1 - (Math.sqrt(variance) / 1000));
    }
    getHistory() {
        return this.history;
    }
    updateHistory() {
        this.history.push(this.getCurrentAPM());
        if (this.history.length > this.maxHistoryLength) {
            this.history.shift();
        }
    }
}
// Minimap for program overview
class TacticsMinimap {
    constructor() {
        this.width = 150;
        this.height = 100;
        this.x = 10;
        this.y = 10;
    }
    render(ctx, blocks, connections) {
        // Minimap background
        ctx.fillStyle = 'rgba(0, 0, 0, 0.7)';
        ctx.roundRect(this.x, this.y, this.width, this.height, 5);
        ctx.fill();
        // Scale factor
        const canvasWidth = ctx.canvas.width;
        const canvasHeight = ctx.canvas.height;
        const scaleX = this.width / canvasWidth;
        const scaleY = this.height / canvasHeight;
        // Render blocks as dots
        for (const block of blocks.values()) {
            const x = this.x + block.x * scaleX;
            const y = this.y + block.y * scaleY;
            ctx.fillStyle = block.color;
            ctx.beginPath();
            ctx.arc(x, y, 2, 0, Math.PI * 2);
            ctx.fill();
        }
        // Render connections as lines
        ctx.strokeStyle = '#666';
        ctx.lineWidth = 1;
        for (const connection of connections) {
            const fromBlock = blocks.get(connection.from);
            const toBlock = blocks.get(connection.to);
            if (fromBlock && toBlock) {
                ctx.beginPath();
                ctx.moveTo(this.x + fromBlock.x * scaleX, this.y + fromBlock.y * scaleY);
                ctx.lineTo(this.x + toBlock.x * scaleX, this.y + toBlock.y * scaleY);
                ctx.stroke();
            }
        }
    }
}
// Add CanvasRenderingContext2D.roundRect polyfill if needed
if (!CanvasRenderingContext2D.prototype.roundRect) {
    CanvasRenderingContext2D.prototype.roundRect = function (x, y, width, height, radius) {
        this.beginPath();
        this.moveTo(x + radius, y);
        this.lineTo(x + width - radius, y);
        this.quadraticCurveTo(x + width, y, x + width, y + radius);
        this.lineTo(x + width, y + height - radius);
        this.quadraticCurveTo(x + width, y + height, x + width - radius, y + height);
        this.lineTo(x + radius, y + height);
        this.quadraticCurveTo(x, y + height, x, y + height - radius);
        this.lineTo(x, y + radius);
        this.quadraticCurveTo(x, y, x + radius, y);
        this.closePath();
    };
}

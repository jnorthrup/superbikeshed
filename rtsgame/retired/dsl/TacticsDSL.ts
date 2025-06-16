/**
 * TacticsDSL - High-APM Visual Command Language
 * 
 * A visual programming DSL that supports lightning-fast RTS command composition
 * with Blockly-style drag-drop blocks but optimized for speed and queuing.
 * 
 * Features:
 * - Hotkey-driven block placement
 * - Command queuing and batching
 * - Macro recording and replay
 * - Context-aware autocomplete
 * - Temporal scheduling
 */

// Core DSL Grammar Classes
export class TacticsExpression {
    constructor(type, params = {}) {
        this.type = type;
        this.params = params;
        this.children = [];
        this.id = this.generateId();
        this.timestamp = Date.now();
        this.hotkey = null;
        this.priority = 0;
    }

    generateId() {
        return `expr_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
    }

    chain(nextExpr) {
        this.children.push(nextExpr);
        return this;
    }

    withHotkey(key) {
        this.hotkey = key;
        return this;
    }

    withPriority(priority) {
        this.priority = priority;
        return this;
    }
}

// Command Building Blocks
export class CommandBlocks {
    // === SELECTION BLOCKS ===
    static select(filter) {
        return new TacticsExpression('SELECT', { filter })
            .withHotkey('S');
    }

    static selectAll(unitType) {
        return new TacticsExpression('SELECT_ALL', { unitType })
            .withHotkey('Ctrl+A');
    }

    static selectNearby(radius = 100) {
        return new TacticsExpression('SELECT_NEARBY', { radius })
            .withHotkey('Shift+S');
    }

    static addToSelection(filter) {
        return new TacticsExpression('ADD_SELECTION', { filter })
            .withHotkey('Shift+Click');
    }

    static removeFromSelection(filter) {
        return new TacticsExpression('REMOVE_SELECTION', { filter })
            .withHotkey('Ctrl+Click');
    }

    // === MOVEMENT BLOCKS ===
    static moveTo(target) {
        return new TacticsExpression('MOVE', { target })
            .withHotkey('M');
    }

    static attackMove(target) {
        return new TacticsExpression('ATTACK_MOVE', { target })
            .withHotkey('A');
    }

    static patrol(waypoints) {
        return new TacticsExpression('PATROL', { waypoints })
            .withHotkey('P');
    }

    static formation(type, spacing = 'normal') {
        return new TacticsExpression('FORMATION', { type, spacing })
            .withHotkey('F');
    }

    static retreat(safeZone) {
        return new TacticsExpression('RETREAT', { safeZone })
            .withHotkey('R');
    }

    // === COMBAT BLOCKS ===
    static attack(target) {
        return new TacticsExpression('ATTACK', { target })
            .withHotkey('Ctrl+A');
    }

    static focusFire(target) {
        return new TacticsExpression('FOCUS_FIRE', { target })
            .withHotkey('Shift+A');
    }

    static holdPosition() {
        return new TacticsExpression('HOLD_POSITION')
            .withHotkey('H');
    }

    static setStance(stance) {
        return new TacticsExpression('SET_STANCE', { stance })
            .withHotkey('Ctrl+S');
    }

    // === PRODUCTION BLOCKS ===
    static build(unitType, count = 1) {
        return new TacticsExpression('BUILD', { unitType, count })
            .withHotkey('B');
    }

    static buildQueue(units) {
        return new TacticsExpression('BUILD_QUEUE', { units })
            .withHotkey('Shift+B');
    }

    static research(tech) {
        return new TacticsExpression('RESEARCH', { tech })
            .withHotkey('T');
    }

    static upgrade(upgradeType) {
        return new TacticsExpression('UPGRADE', { upgradeType })
            .withHotkey('U');
    }

    // === CONDITIONAL BLOCKS ===
    static when(condition) {
        return new TacticsExpression('WHEN', { condition })
            .withHotkey('W');
    }

    static ifElse(condition, thenAction, elseAction) {
        const expr = new TacticsExpression('IF_ELSE', { condition });
        expr.children = [thenAction, elseAction];
        return expr.withHotkey('I');
    }

    static repeat(times, action) {
        const expr = new TacticsExpression('REPEAT', { times });
        expr.children = [action];
        return expr.withHotkey('Ctrl+R');
    }

    static until(condition, action) {
        const expr = new TacticsExpression('UNTIL', { condition });
        expr.children = [action];
        return expr.withHotkey('Ctrl+U');
    }

    // === TIMING BLOCKS ===
    static delay(milliseconds) {
        return new TacticsExpression('DELAY', { milliseconds })
            .withHotkey('D');
    }

    static schedule(time, action) {
        const expr = new TacticsExpression('SCHEDULE', { time });
        expr.children = [action];
        return expr.withHotkey('Ctrl+T');
    }

    static parallel(...actions) {
        const expr = new TacticsExpression('PARALLEL');
        expr.children = actions;
        return expr.withHotkey('Ctrl+P');
    }

    static sequence(...actions) {
        const expr = new TacticsExpression('SEQUENCE');
        expr.children = actions;
        return expr.withHotkey('Ctrl+Q');
    }

    // === MACRO BLOCKS ===
    static macro(name, actions) {
        const expr = new TacticsExpression('MACRO', { name });
        expr.children = actions;
        return expr.withHotkey('Ctrl+M');
    }

    static callMacro(name, params = {}) {
        return new TacticsExpression('CALL_MACRO', { name, params })
            .withHotkey('Shift+M');
    }

    // === RESOURCE BLOCKS ===
    static allocateResources(resources) {
        return new TacticsExpression('ALLOCATE_RESOURCES', { resources })
            .withHotkey('Shift+R');
    }

    static prioritizeProduction(priority) {
        return new TacticsExpression('PRIORITIZE_PRODUCTION', { priority })
            .withHotkey('Ctrl+1-9');
    }
}

// Visual Block Layout System
export class BlockLayout {
    constructor() {
        this.blocks = new Map();
        this.connections = new Map();
        this.palette = this.createPalette();
        this.workspace = [];
        this.cursor = { x: 0, y: 0 };
        this.snapGrid = 20;
    }

    createPalette() {
        return {
            SELECTION: {
                color: '#4CAF50',
                icon: '👆',
                blocks: ['SELECT', 'SELECT_ALL', 'SELECT_NEARBY', 'ADD_SELECTION', 'REMOVE_SELECTION']
            },
            MOVEMENT: {
                color: '#2196F3',
                icon: '🚀',
                blocks: ['MOVE', 'ATTACK_MOVE', 'PATROL', 'FORMATION', 'RETREAT']
            },
            COMBAT: {
                color: '#F44336',
                icon: '⚔️',
                blocks: ['ATTACK', 'FOCUS_FIRE', 'HOLD_POSITION', 'SET_STANCE']
            },
            PRODUCTION: {
                color: '#FF9800',
                icon: '🏭',
                blocks: ['BUILD', 'BUILD_QUEUE', 'RESEARCH', 'UPGRADE']
            },
            LOGIC: {
                color: '#9C27B0',
                icon: '🧠',
                blocks: ['WHEN', 'IF_ELSE', 'REPEAT', 'UNTIL']
            },
            TIMING: {
                color: '#607D8B',
                icon: '⏱️',
                blocks: ['DELAY', 'SCHEDULE', 'PARALLEL', 'SEQUENCE']
            },
            MACRO: {
                color: '#795548',
                icon: '📜',
                blocks: ['MACRO', 'CALL_MACRO']
            },
            RESOURCES: {
                color: '#FFC107',
                icon: '💰',
                blocks: ['ALLOCATE_RESOURCES', 'PRIORITIZE_PRODUCTION']
            }
        };
    }

    // Snap block to grid for clean layout
    snapToGrid(x, y) {
        return {
            x: Math.round(x / this.snapGrid) * this.snapGrid,
            y: Math.round(y / this.snapGrid) * this.snapGrid
        };
    }

    // Visual block representation
    renderBlock(expression, x, y) {
        const blockType = this.getBlockType(expression.type);
        const { color, icon } = this.palette[blockType];
        
        return {
            id: expression.id,
            type: expression.type,
            x: this.snapToGrid(x, y).x,
            y: this.snapToGrid(x, y).y,
            width: this.calculateBlockWidth(expression),
            height: 40,
            color,
            icon,
            text: this.generateBlockText(expression),
            hotkey: expression.hotkey,
            connections: {
                input: expression.type !== 'SELECT' ? 1 : 0,
                output: 1
            },
            children: expression.children.map((child, i) => 
                this.renderBlock(child, x + 20, y + 60 + (i * 50))
            )
        };
    }

    getBlockType(expressionType) {
        for (const [category, data] of Object.entries(this.palette)) {
            if (data.blocks.includes(expressionType)) {
                return category;
            }
        }
        return 'LOGIC'; // Default
    }

    calculateBlockWidth(expression) {
        const baseWidth = 120;
        const textLength = this.generateBlockText(expression).length;
        return Math.max(baseWidth, textLength * 8 + 40);
    }

    generateBlockText(expression) {
        switch (expression.type) {
            case 'SELECT':
                return `Select ${expression.params.filter || 'units'}`;
            case 'MOVE':
                return `Move to ${expression.params.target || 'location'}`;
            case 'ATTACK':
                return `Attack ${expression.params.target || 'target'}`;
            case 'BUILD':
                return `Build ${expression.params.count || 1}x ${expression.params.unitType || 'unit'}`;
            case 'WHEN':
                return `When ${expression.params.condition || 'condition'}`;
            case 'DELAY':
                return `Wait ${expression.params.milliseconds || 0}ms`;
            default:
                return expression.type.replace(/_/g, ' ').toLowerCase();
        }
    }
}

// High-Speed Command Composer
export class APMComposer {
    constructor() {
        this.commandQueue = [];
        this.macros = new Map();
        this.hotkeys = new Map();
        this.recordingMacro = null;
        this.layout = new BlockLayout();
        this.setupHotkeys();
    }

    setupHotkeys() {
        // Quick selection hotkeys
        this.hotkeys.set('q', () => this.quickSelect('army'));
        this.hotkeys.set('w', () => this.quickSelect('workers'));
        this.hotkeys.set('e', () => this.quickSelect('engineers'));
        this.hotkeys.set('r', () => this.quickSelect('factories'));

        // Quick action hotkeys  
        this.hotkeys.set('a', () => this.quickAction('attack'));
        this.hotkeys.set('s', () => this.quickAction('stop'));
        this.hotkeys.set('d', () => this.quickAction('patrol'));
        this.hotkeys.set('f', () => this.quickAction('formation'));

        // Number keys for control groups
        for (let i = 1; i <= 9; i++) {
            this.hotkeys.set(i.toString(), () => this.selectControlGroup(i));
            this.hotkeys.set(`ctrl+${i}`, () => this.assignControlGroup(i));
        }
    }

    // Build command chain fluently
    startChain() {
        return new CommandChain(this);
    }

    // Queue command for execution
    queue(expression, delay = 0) {
        this.commandQueue.push({
            expression,
            executeAt: Date.now() + delay,
            queued: Date.now()
        });
        return this;
    }

    // Execute queued commands
    processQueue(gameState) {
        const now = Date.now();
        const ready = this.commandQueue.filter(cmd => cmd.executeAt <= now);
        
        ready.forEach(cmd => {
            this.executeExpression(cmd.expression, gameState);
        });

        this.commandQueue = this.commandQueue.filter(cmd => cmd.executeAt > now);
        return ready.length;
    }

    // Macro recording
    startRecording(name) {
        this.recordingMacro = {
            name,
            commands: [],
            startTime: Date.now()
        };
    }

    stopRecording() {
        if (this.recordingMacro) {
            this.macros.set(this.recordingMacro.name, this.recordingMacro.commands);
            this.recordingMacro = null;
        }
    }

    // Execute macro
    playMacro(name, params = {}) {
        const macro = this.macros.get(name);
        if (macro) {
            macro.forEach((cmd, index) => {
                this.queue(cmd, index * 50); // 50ms between macro commands
            });
        }
    }

    // Quick builders for common patterns
    quickSelect(type) {
        switch (type) {
            case 'army':
                return this.queue(CommandBlocks.selectAll('combat'));
            case 'workers':
                return this.queue(CommandBlocks.selectAll('worker'));
            case 'engineers':
                return this.queue(CommandBlocks.selectAll('engineer'));
            case 'factories':
                return this.queue(CommandBlocks.selectAll('factory'));
        }
    }

    quickAction(action) {
        const selected = this.getSelected();
        if (!selected.length) return;

        switch (action) {
            case 'attack':
                return this.queue(CommandBlocks.attackMove('mouse_position'));
            case 'stop':
                return this.queue(CommandBlocks.holdPosition());
            case 'patrol':
                return this.queue(CommandBlocks.patrol(['current', 'mouse_position']));
            case 'formation':
                return this.queue(CommandBlocks.formation('wedge'));
        }
    }

    // Visual programming interface
    createVisualProgram() {
        return {
            // Drag & drop from palette
            dragFromPalette: (blockType, x, y) => {
                const expression = this.createExpressionFromType(blockType);
                return this.layout.renderBlock(expression, x, y);
            },

            // Connect blocks
            connectBlocks: (sourceId, targetId) => {
                this.layout.connections.set(sourceId, targetId);
            },

            // Generate code from visual layout
            generateCode: () => {
                return this.layout.workspace.map(block => 
                    this.blockToExpression(block)
                );
            },

            // Execute visual program
            execute: (gameState) => {
                const expressions = this.generateCode();
                expressions.forEach(expr => this.queue(expr));
                return this.processQueue(gameState);
            }
        };
    }

    // Convert block type to expression
    createExpressionFromType(blockType) {
        const creators = {
            'SELECT': () => CommandBlocks.select('units'),
            'MOVE': () => CommandBlocks.moveTo('target'),
            'ATTACK': () => CommandBlocks.attack('enemy'),
            'BUILD': () => CommandBlocks.build('tank'),
            'WHEN': () => CommandBlocks.when('enemy_nearby'),
            'DELAY': () => CommandBlocks.delay(1000),
            'PARALLEL': () => CommandBlocks.parallel(),
            'SEQUENCE': () => CommandBlocks.sequence()
        };

        return creators[blockType] ? creators[blockType]() : new TacticsExpression(blockType);
    }

    // Placeholder methods that would connect to game state
    getSelected() { return []; }
    selectControlGroup(group) { /* Implementation */ }
    assignControlGroup(group) { /* Implementation */ }
    executeExpression(expr, gameState) { /* Implementation */ }
    blockToExpression(block) { return new TacticsExpression(block.type); }
}

// Fluent command building interface
export class CommandChain {
    constructor(composer) {
        this.composer = composer;
        this.expressions = [];
    }

    select(filter) {
        this.expressions.push(CommandBlocks.select(filter));
        return this;
    }

    moveTo(target) {
        this.expressions.push(CommandBlocks.moveTo(target));
        return this;
    }

    attack(target) {
        this.expressions.push(CommandBlocks.attack(target));
        return this;
    }

    build(unitType, count = 1) {
        this.expressions.push(CommandBlocks.build(unitType, count));
        return this;
    }

    when(condition) {
        this.expressions.push(CommandBlocks.when(condition));
        return this;
    }

    delay(ms) {
        this.expressions.push(CommandBlocks.delay(ms));
        return this;
    }

    parallel() {
        const parallelBlock = CommandBlocks.parallel(...this.expressions);
        this.expressions = [parallelBlock];
        return this;
    }

    sequence() {
        const sequenceBlock = CommandBlocks.sequence(...this.expressions);
        this.expressions = [sequenceBlock];
        return this;
    }

    // Execute the chain
    execute(delay = 0) {
        this.expressions.forEach((expr, index) => {
            this.composer.queue(expr, delay + (index * 100));
        });
        return this.composer;
    }

    // Save as macro
    saveAsMacro(name) {
        this.composer.macros.set(name, this.expressions);
        return this;
    }
}

// Example usage demonstrating high APM potential:
export function createExamplePrograms() {
    const composer = new APMComposer();

    // Example 1: Quick army management
    const armyManagement = composer.startChain()
        .select('army')
        .when('enemy_nearby')
        .attack('nearest_enemy')
        .when('health_low')
        .moveTo('safe_zone')
        .saveAsMacro('army_micro');

    // Example 2: Economy management
    const economyManagement = composer.startChain()
        .select('workers')
        .when('resources_low')
        .moveTo('resource_nodes')
        .select('factories')
        .build('worker', 5)
        .saveAsMacro('eco_boost');

    // Example 3: Complex battle coordination
    const battleCoordination = composer.startChain()
        .parallel()
        .select('artillery')
        .attack('enemy_base')
        .select('infantry')
        .moveTo('flanking_position')
        .select('air_units')
        .attack('enemy_air')
        .saveAsMacro('combined_assault');

    return { composer, armyManagement, economyManagement, battleCoordination };
}
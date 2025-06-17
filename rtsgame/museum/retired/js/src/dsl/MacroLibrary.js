"use strict";
/**
 * Macro Library - Pre-built high-APM command sequences
 *
 * This library contains professionally optimized macro sequences that would
 * normally require 200+ APM to execute manually, but can be triggered with
 * a single hotkey through the TacticsDSL system.
 */
Object.defineProperty(exports, "__esModule", { value: true });
exports.AdaptiveMacroSystem = exports.MacroLibrary = void 0;
exports.demonstrateMacroLibrary = demonstrateMacroLibrary;
const TacticsDSL_js_1 = require("./TacticsDSL.js");
class MacroLibrary {
    constructor(composer) {
        this.composer = composer;
        this.macros = new Map();
        this.createStandardMacros();
        this.createAdvancedMacros();
        this.createProMacros();
    }
    // Standard macros for intermediate players (50-100 APM equivalent)
    createStandardMacros() {
        // Economy Rush - Build workers and expand resource collection
        this.register('ECONOMY_RUSH', [
            TacticsDSL_js_1.CommandBlocks.selectAll('worker'),
            TacticsDSL_js_1.CommandBlocks.when('resources_above_threshold'),
            TacticsDSL_js_1.CommandBlocks.build('worker', 5),
            TacticsDSL_js_1.CommandBlocks.selectAll('factory'),
            TacticsDSL_js_1.CommandBlocks.prioritizeProduction('high'),
            TacticsDSL_js_1.CommandBlocks.select('idle_workers'),
            TacticsDSL_js_1.CommandBlocks.moveTo('nearest_resources')
        ], 'Ctrl+E');
        // Basic Army Control - Select and micro army units
        this.register('ARMY_MICRO', [
            TacticsDSL_js_1.CommandBlocks.selectAll('combat'),
            TacticsDSL_js_1.CommandBlocks.formation('wedge'),
            TacticsDSL_js_1.CommandBlocks.when('enemy_in_range'),
            TacticsDSL_js_1.CommandBlocks.focusFire('highest_threat'),
            TacticsDSL_js_1.CommandBlocks.when('health_below_30'),
            TacticsDSL_js_1.CommandBlocks.retreat('rally_point')
        ], 'Ctrl+1');
        // Quick Defense - Rapid defensive response
        this.register('QUICK_DEFENSE', [
            TacticsDSL_js_1.CommandBlocks.selectAll('defensive_units'),
            TacticsDSL_js_1.CommandBlocks.holdPosition(),
            TacticsDSL_js_1.CommandBlocks.select('mobile_units'),
            TacticsDSL_js_1.CommandBlocks.moveTo('threat_location'),
            TacticsDSL_js_1.CommandBlocks.attack('nearest_enemy'),
            TacticsDSL_js_1.CommandBlocks.parallel(TacticsDSL_js_1.CommandBlocks.selectAll('factory').chain(TacticsDSL_js_1.CommandBlocks.build('defensive_unit', 3)), TacticsDSL_js_1.CommandBlocks.selectAll('worker').chain(TacticsDSL_js_1.CommandBlocks.retreat('safe_zone')))
        ], 'Ctrl+D');
        // Resource Optimization - Maximize resource efficiency
        this.register('RESOURCE_OPTIMIZATION', [
            TacticsDSL_js_1.CommandBlocks.selectAll('worker'),
            TacticsDSL_js_1.CommandBlocks.when('resource_depleted'),
            TacticsDSL_js_1.CommandBlocks.moveTo('alternate_resource'),
            TacticsDSL_js_1.CommandBlocks.selectAll('factory'),
            TacticsDSL_js_1.CommandBlocks.when('resources_sufficient'),
            TacticsDSL_js_1.CommandBlocks.build('worker', 2),
            TacticsDSL_js_1.CommandBlocks.allocateResources({ metal: 0.4, energy: 0.6 })
        ], 'Ctrl+R');
    }
    // Advanced macros for experienced players (100-200 APM equivalent)
    createAdvancedMacros() {
        // Multi-Front Assault - Coordinate attacks on multiple fronts
        this.register('MULTI_FRONT_ASSAULT', [
            TacticsDSL_js_1.CommandBlocks.parallel(
            // Front 1: Main army
            TacticsDSL_js_1.CommandBlocks.sequence(TacticsDSL_js_1.CommandBlocks.select('army_group_1'), TacticsDSL_js_1.CommandBlocks.formation('line'), TacticsDSL_js_1.CommandBlocks.attackMove('enemy_main_base'), TacticsDSL_js_1.CommandBlocks.when('resistance_heavy'), TacticsDSL_js_1.CommandBlocks.focusFire('priority_targets')), 
            // Front 2: Flanking force
            TacticsDSL_js_1.CommandBlocks.sequence(TacticsDSL_js_1.CommandBlocks.select('army_group_2'), TacticsDSL_js_1.CommandBlocks.moveTo('flanking_position'), TacticsDSL_js_1.CommandBlocks.delay(5000), // 5 second delay
            TacticsDSL_js_1.CommandBlocks.attackMove('enemy_rear')), 
            // Support: Air cover
            TacticsDSL_js_1.CommandBlocks.sequence(TacticsDSL_js_1.CommandBlocks.select('air_units'), TacticsDSL_js_1.CommandBlocks.patrol(['front_1', 'front_2']), TacticsDSL_js_1.CommandBlocks.attack('enemy_air')))
        ], 'Ctrl+Shift+A');
        // Advanced Economy Management - Complex resource balancing
        this.register('ADVANCED_ECONOMY', [
            TacticsDSL_js_1.CommandBlocks.parallel(
            // Worker management
            TacticsDSL_js_1.CommandBlocks.sequence(TacticsDSL_js_1.CommandBlocks.selectAll('worker'), TacticsDSL_js_1.CommandBlocks.when('efficiency_below_80'), TacticsDSL_js_1.CommandBlocks.moveTo('optimal_resource_spots')), 
            // Production balancing
            TacticsDSL_js_1.CommandBlocks.sequence(TacticsDSL_js_1.CommandBlocks.selectAll('factory'), TacticsDSL_js_1.CommandBlocks.when('metal_surplus'), TacticsDSL_js_1.CommandBlocks.build('metal_consuming_unit'), TacticsDSL_js_1.CommandBlocks.when('energy_surplus'), TacticsDSL_js_1.CommandBlocks.build('energy_consuming_unit')), 
            // Expansion
            TacticsDSL_js_1.CommandBlocks.sequence(TacticsDSL_js_1.CommandBlocks.select('constructor'), TacticsDSL_js_1.CommandBlocks.when('resources_above_2000'), TacticsDSL_js_1.CommandBlocks.moveTo('expansion_site'), TacticsDSL_js_1.CommandBlocks.build('resource_extractor')))
        ], 'Ctrl+Shift+E');
        // Tactical Retreat with Counterattack
        this.register('TACTICAL_RETREAT', [
            TacticsDSL_js_1.CommandBlocks.sequence(
            // Phase 1: Retreat
            TacticsDSL_js_1.CommandBlocks.selectAll('damaged_units'), TacticsDSL_js_1.CommandBlocks.retreat('repair_station'), TacticsDSL_js_1.CommandBlocks.selectAll('healthy_units'), TacticsDSL_js_1.CommandBlocks.formation('defensive'), TacticsDSL_js_1.CommandBlocks.holdPosition(), 
            // Phase 2: Reinforce
            TacticsDSL_js_1.CommandBlocks.delay(3000), TacticsDSL_js_1.CommandBlocks.selectAll('factory'), TacticsDSL_js_1.CommandBlocks.buildQueue(['tank', 'tank', 'artillery']), 
            // Phase 3: Counterattack
            TacticsDSL_js_1.CommandBlocks.when('reinforcements_ready'), TacticsDSL_js_1.CommandBlocks.selectAll('combat'), TacticsDSL_js_1.CommandBlocks.formation('spearhead'), TacticsDSL_js_1.CommandBlocks.attackMove('enemy_weakness'))
        ], 'Ctrl+Shift+R');
        // Supply Line Management - Advanced logistics
        this.register('SUPPLY_LINES', [
            TacticsDSL_js_1.CommandBlocks.parallel(
            // Transport management
            TacticsDSL_js_1.CommandBlocks.sequence(TacticsDSL_js_1.CommandBlocks.select('transport_units'), TacticsDSL_js_1.CommandBlocks.when('cargo_available'), TacticsDSL_js_1.CommandBlocks.moveTo('pickup_point'), TacticsDSL_js_1.CommandBlocks.moveTo('dropoff_point'), TacticsDSL_js_1.CommandBlocks.repeat(99, TacticsDSL_js_1.CommandBlocks.callMacro('transport_cycle'))), 
            // Supply protection
            TacticsDSL_js_1.CommandBlocks.sequence(TacticsDSL_js_1.CommandBlocks.select('escort_units'), TacticsDSL_js_1.CommandBlocks.patrol(['supply_route_1', 'supply_route_2']), TacticsDSL_js_1.CommandBlocks.when('enemy_detected'), TacticsDSL_js_1.CommandBlocks.attack('threats')))
        ], 'Ctrl+Shift+S');
    }
    // Pro-level macros for elite players (200+ APM equivalent)
    createProMacros() {
        // Perfect Storm - Ultimate combined arms assault
        this.register('PERFECT_STORM', [
            TacticsDSL_js_1.CommandBlocks.parallel(
            // Artillery barrage preparation
            TacticsDSL_js_1.CommandBlocks.sequence(TacticsDSL_js_1.CommandBlocks.select('artillery'), TacticsDSL_js_1.CommandBlocks.moveTo('bombardment_position'), TacticsDSL_js_1.CommandBlocks.attack('enemy_defenses'), TacticsDSL_js_1.CommandBlocks.delay(2000)), 
            // Air superiority
            TacticsDSL_js_1.CommandBlocks.sequence(TacticsDSL_js_1.CommandBlocks.select('fighters'), TacticsDSL_js_1.CommandBlocks.attack('enemy_air'), TacticsDSL_js_1.CommandBlocks.select('bombers'), TacticsDSL_js_1.CommandBlocks.delay(1000), TacticsDSL_js_1.CommandBlocks.attack('enemy_aa')), 
            // Ground assault waves
            TacticsDSL_js_1.CommandBlocks.sequence(
            // Wave 1: Fast units
            TacticsDSL_js_1.CommandBlocks.select('fast_attack'), TacticsDSL_js_1.CommandBlocks.attackMove('enemy_flanks'), TacticsDSL_js_1.CommandBlocks.delay(3000), 
            // Wave 2: Main force
            TacticsDSL_js_1.CommandBlocks.select('main_army'), TacticsDSL_js_1.CommandBlocks.formation('wedge'), TacticsDSL_js_1.CommandBlocks.attackMove('enemy_center'), TacticsDSL_js_1.CommandBlocks.delay(2000), 
            // Wave 3: Heavy support
            TacticsDSL_js_1.CommandBlocks.select('heavy_units'), TacticsDSL_js_1.CommandBlocks.attackMove('enemy_strongpoints')), 
            // Economic protection
            TacticsDSL_js_1.CommandBlocks.sequence(TacticsDSL_js_1.CommandBlocks.selectAll('worker'), TacticsDSL_js_1.CommandBlocks.retreat('fortified_base'), TacticsDSL_js_1.CommandBlocks.selectAll('factory'), TacticsDSL_js_1.CommandBlocks.buildQueue(['defender', 'defender', 'defender'])))
        ], 'Ctrl+Alt+Shift+A');
        // Economic Singularity - Ultimate economy optimization
        this.register('ECONOMIC_SINGULARITY', [
            TacticsDSL_js_1.CommandBlocks.parallel(
            // Resource extraction optimization
            TacticsDSL_js_1.CommandBlocks.sequence(TacticsDSL_js_1.CommandBlocks.selectAll('worker'), TacticsDSL_js_1.CommandBlocks.when('resource_efficiency_below_95'), TacticsDSL_js_1.CommandBlocks.moveTo('optimal_spots'), TacticsDSL_js_1.CommandBlocks.build('efficiency_upgrade')), 
            // Production line optimization
            TacticsDSL_js_1.CommandBlocks.sequence(TacticsDSL_js_1.CommandBlocks.selectAll('factory'), TacticsDSL_js_1.CommandBlocks.prioritizeProduction('dynamic'), TacticsDSL_js_1.CommandBlocks.when('queue_empty'), TacticsDSL_js_1.CommandBlocks.buildQueue(['worker', 'factory', 'worker']), TacticsDSL_js_1.CommandBlocks.upgrade('production_efficiency')), 
            // Advanced expansion
            TacticsDSL_js_1.CommandBlocks.sequence(TacticsDSL_js_1.CommandBlocks.select('advanced_constructor'), TacticsDSL_js_1.CommandBlocks.moveTo('prime_expansion'), TacticsDSL_js_1.CommandBlocks.build('mega_factory'), TacticsDSL_js_1.CommandBlocks.build('resource_processor'), TacticsDSL_js_1.CommandBlocks.build('defense_grid')), 
            // Market manipulation
            TacticsDSL_js_1.CommandBlocks.sequence(TacticsDSL_js_1.CommandBlocks.when('metal_price_low'), TacticsDSL_js_1.CommandBlocks.allocateResources({ metal: 0.8, energy: 0.2 }), TacticsDSL_js_1.CommandBlocks.when('energy_price_low'), TacticsDSL_js_1.CommandBlocks.allocateResources({ metal: 0.2, energy: 0.8 })))
        ], 'Ctrl+Alt+Shift+E');
        // Hyperspace Maneuver - Advanced tactical positioning
        this.register('HYPERSPACE_MANEUVER', [
            TacticsDSL_js_1.CommandBlocks.sequence(
            // Phase 1: Deception
            TacticsDSL_js_1.CommandBlocks.select('decoy_units'), TacticsDSL_js_1.CommandBlocks.attackMove('obvious_attack_route'), TacticsDSL_js_1.CommandBlocks.select('main_force'), TacticsDSL_js_1.CommandBlocks.moveTo('hidden_staging_area'), 
            // Phase 2: Positioning
            TacticsDSL_js_1.CommandBlocks.parallel(TacticsDSL_js_1.CommandBlocks.sequence(TacticsDSL_js_1.CommandBlocks.select('stealth_units'), TacticsDSL_js_1.CommandBlocks.moveTo('enemy_rear'), TacticsDSL_js_1.CommandBlocks.holdPosition()), TacticsDSL_js_1.CommandBlocks.sequence(TacticsDSL_js_1.CommandBlocks.select('mobile_artillery'), TacticsDSL_js_1.CommandBlocks.moveTo('support_position'), TacticsDSL_js_1.CommandBlocks.attack('enemy_support'))), 
            // Phase 3: Synchronized strike
            TacticsDSL_js_1.CommandBlocks.delay(5000), TacticsDSL_js_1.CommandBlocks.parallel(TacticsDSL_js_1.CommandBlocks.sequence(TacticsDSL_js_1.CommandBlocks.select('stealth_units'), TacticsDSL_js_1.CommandBlocks.attack('enemy_command')), TacticsDSL_js_1.CommandBlocks.sequence(TacticsDSL_js_1.CommandBlocks.select('main_force'), TacticsDSL_js_1.CommandBlocks.formation('hammer'), TacticsDSL_js_1.CommandBlocks.attackMove('enemy_weak_point')), TacticsDSL_js_1.CommandBlocks.sequence(TacticsDSL_js_1.CommandBlocks.select('air_support'), TacticsDSL_js_1.CommandBlocks.attack('enemy_reinforcements'))))
        ], 'Ctrl+Alt+Shift+H');
        // Matrix Protocol - Ultimate micro-management
        this.register('MATRIX_PROTOCOL', [
            TacticsDSL_js_1.CommandBlocks.parallel(
            // Individual unit micro for each unit type
            TacticsDSL_js_1.CommandBlocks.sequence(TacticsDSL_js_1.CommandBlocks.select('unit_type_1'), TacticsDSL_js_1.CommandBlocks.repeat(10, TacticsDSL_js_1.CommandBlocks.sequence(TacticsDSL_js_1.CommandBlocks.when('enemy_targeting'), TacticsDSL_js_1.CommandBlocks.moveTo('evasion_point'), TacticsDSL_js_1.CommandBlocks.delay(200), TacticsDSL_js_1.CommandBlocks.attack('opportunity_target'), TacticsDSL_js_1.CommandBlocks.delay(300)))), TacticsDSL_js_1.CommandBlocks.sequence(TacticsDSL_js_1.CommandBlocks.select('unit_type_2'), TacticsDSL_js_1.CommandBlocks.repeat(10, TacticsDSL_js_1.CommandBlocks.sequence(TacticsDSL_js_1.CommandBlocks.when('cooldown_ready'), TacticsDSL_js_1.CommandBlocks.attack('priority_target'), TacticsDSL_js_1.CommandBlocks.moveTo('safe_position'), TacticsDSL_js_1.CommandBlocks.delay(500)))), 
            // Ability micro
            TacticsDSL_js_1.CommandBlocks.sequence(TacticsDSL_js_1.CommandBlocks.select('ability_units'), TacticsDSL_js_1.CommandBlocks.when('ability_ready'), TacticsDSL_js_1.CommandBlocks.sequence(TacticsDSL_js_1.CommandBlocks.moveTo('optimal_position'), TacticsDSL_js_1.CommandBlocks.delay(100), TacticsDSL_js_1.CommandBlocks.attack('clustered_enemies'))))
        ], 'Ctrl+Alt+Shift+M');
    }
    // Register a macro with the composer
    register(name, commandSequence, hotkey) {
        // Convert command sequence to executable macro
        const macro = TacticsDSL_js_1.CommandBlocks.sequence(...commandSequence);
        this.macros.set(name, {
            macro,
            hotkey,
            commands: commandSequence,
            estimatedAPM: this.calculateAPM(commandSequence),
            complexity: this.calculateComplexity(commandSequence),
            description: this.generateDescription(name, commandSequence)
        });
        // Register with composer
        this.composer.macros.set(name, commandSequence);
        // Set up hotkey if provided
        if (hotkey) {
            this.composer.hotkeys.set(hotkey.toLowerCase(), () => {
                this.execute(name);
            });
        }
    }
    // Execute a macro by name
    execute(name, params = {}) {
        const macroData = this.macros.get(name);
        if (macroData) {
            // Apply parameters to the macro
            const parameterizedMacro = this.applyParameters(macroData.macro, params);
            this.composer.queue(parameterizedMacro);
            console.log(`🚀 Executing macro: ${name} (${macroData.estimatedAPM} APM equivalent)`);
            return true;
        }
        return false;
    }
    // Apply parameters to customize macro execution
    applyParameters(macro, params) {
        // Clone the macro and modify parameters
        const customizedMacro = JSON.parse(JSON.stringify(macro));
        // Apply parameter overrides
        this.applyParametersRecursive(customizedMacro, params);
        return customizedMacro;
    }
    applyParametersRecursive(node, params) {
        if (node.params) {
            Object.assign(node.params, params);
        }
        if (node.children) {
            node.children.forEach(child => this.applyParametersRecursive(child, params));
        }
    }
    // Calculate equivalent APM for a command sequence
    calculateAPM(commandSequence) {
        let apm = 0;
        commandSequence.forEach(command => {
            // Base APM cost per command type
            const baseCosts = {
                'SELECT': 2,
                'MOVE': 3,
                'ATTACK': 4,
                'BUILD': 5,
                'WHEN': 1,
                'PARALLEL': 8,
                'SEQUENCE': 2,
                'DELAY': 0,
                'REPEAT': 10
            };
            apm += baseCosts[command.type] || 3;
            // Add complexity for nested commands
            if (command.children) {
                apm += this.calculateAPM(command.children) * 0.5;
            }
        });
        return Math.round(apm);
    }
    // Calculate complexity score
    calculateComplexity(commandSequence) {
        let complexity = commandSequence.length;
        commandSequence.forEach(command => {
            if (command.type === 'PARALLEL')
                complexity += 5;
            if (command.type === 'WHEN')
                complexity += 3;
            if (command.type === 'REPEAT')
                complexity += 4;
            if (command.children)
                complexity += this.calculateComplexity(command.children);
        });
        return complexity;
    }
    // Generate human-readable description
    generateDescription(name, commandSequence) {
        const descriptions = {
            'ECONOMY_RUSH': 'Rapidly expand economy with optimized worker production and resource allocation',
            'ARMY_MICRO': 'Advanced army micromanagement with formation control and tactical retreats',
            'QUICK_DEFENSE': 'Instant defensive response with unit positioning and emergency production',
            'MULTI_FRONT_ASSAULT': 'Coordinate simultaneous attacks on multiple fronts with perfect timing',
            'PERFECT_STORM': 'Ultimate combined arms assault with air, ground, and artillery coordination',
            'ECONOMIC_SINGULARITY': 'Achieve perfect economic efficiency through optimal resource management',
            'HYPERSPACE_MANEUVER': 'Advanced tactical positioning with deception and synchronized strikes',
            'MATRIX_PROTOCOL': 'Ultimate micro-management with individual unit control and ability timing'
        };
        return descriptions[name] || `Advanced macro sequence with ${commandSequence.length} commands`;
    }
    // Get all available macros
    getAllMacros() {
        return Array.from(this.macros.entries()).map(([name, data]) => ({
            name,
            hotkey: data.hotkey,
            estimatedAPM: data.estimatedAPM,
            complexity: data.complexity,
            description: data.description
        }));
    }
    // Get macros by difficulty level
    getMacrosByDifficulty(level) {
        const allMacros = this.getAllMacros();
        switch (level) {
            case 'standard':
                return allMacros.filter(m => m.estimatedAPM <= 100);
            case 'advanced':
                return allMacros.filter(m => m.estimatedAPM > 100 && m.estimatedAPM <= 200);
            case 'pro':
                return allMacros.filter(m => m.estimatedAPM > 200);
            default:
                return allMacros;
        }
    }
    // Create custom macro from visual blocks
    createCustomMacro(name, blockSequence, hotkey) {
        const commands = blockSequence.map(block => this.blockToCommand(block));
        this.register(name, commands, hotkey);
    }
    blockToCommand(block) {
        // Convert visual block to command
        const constructors = {
            'SELECT': TacticsDSL_js_1.CommandBlocks.select,
            'MOVE': TacticsDSL_js_1.CommandBlocks.moveTo,
            'ATTACK': TacticsDSL_js_1.CommandBlocks.attack,
            'BUILD': TacticsDSL_js_1.CommandBlocks.build,
            'WHEN': TacticsDSL_js_1.CommandBlocks.when,
            'DELAY': TacticsDSL_js_1.CommandBlocks.delay,
            'PARALLEL': TacticsDSL_js_1.CommandBlocks.parallel,
            'SEQUENCE': TacticsDSL_js_1.CommandBlocks.sequence
        };
        const constructor = constructors[block.type];
        return constructor ? constructor(block.params) : TacticsDSL_js_1.CommandBlocks.select('default');
    }
    // Export macro library for sharing
    exportLibrary() {
        const exported = {};
        for (const [name, data] of this.macros) {
            exported[name] = {
                commands: data.commands,
                hotkey: data.hotkey,
                estimatedAPM: data.estimatedAPM,
                complexity: data.complexity,
                description: data.description
            };
        }
        return JSON.stringify(exported, null, 2);
    }
    // Import macro library
    importLibrary(jsonData) {
        try {
            const imported = JSON.parse(jsonData);
            for (const [name, data] of Object.entries(imported)) {
                this.register(name, data.commands, data.hotkey);
            }
            return true;
        }
        catch (error) {
            console.error('Failed to import macro library:', error);
            return false;
        }
    }
}
exports.MacroLibrary = MacroLibrary;
// Example usage and demonstration
function demonstrateMacroLibrary() {
    const composer = new TacticsDSL_js_1.APMComposer();
    const library = new MacroLibrary(composer);
    console.log('🎯 Macro Library Loaded!');
    console.log('Available macros:');
    library.getAllMacros().forEach(macro => {
        console.log(`  ${macro.name} (${macro.hotkey}): ${macro.estimatedAPM} APM - ${macro.description}`);
    });
    // Demonstrate macro execution
    console.log('\n🚀 Executing PERFECT_STORM macro...');
    library.execute('PERFECT_STORM', { target: 'enemy_main_base' });
    console.log('\n📊 Macro Statistics:');
    console.log(`Standard macros: ${library.getMacrosByDifficulty('standard').length}`);
    console.log(`Advanced macros: ${library.getMacrosByDifficulty('advanced').length}`);
    console.log(`Pro-level macros: ${library.getMacrosByDifficulty('pro').length}`);
    return library;
}
// Adaptive macro system that learns from player behavior
class AdaptiveMacroSystem {
    constructor(library) {
        this.library = library;
        this.playerStats = {
            apmHistory: [],
            preferredMacros: new Map(),
            situationalUsage: new Map(),
            successRates: new Map()
        };
        this.recommendations = [];
    }
    // Track macro usage and success
    recordMacroUsage(macroName, situation, success) {
        // Update usage frequency
        const current = this.playerStats.preferredMacros.get(macroName) || 0;
        this.playerStats.preferredMacros.set(macroName, current + 1);
        // Update situational usage
        const situationKey = `${macroName}_${situation}`;
        const situationalCount = this.playerStats.situationalUsage.get(situationKey) || 0;
        this.playerStats.situationalUsage.set(situationKey, situationalCount + 1);
        // Update success rate
        const successData = this.playerStats.successRates.get(macroName) || { total: 0, success: 0 };
        successData.total++;
        if (success)
            successData.success++;
        this.playerStats.successRates.set(macroName, successData);
    }
    // Get personalized macro recommendations
    getRecommendations(currentSituation, playerAPM) {
        const recommendations = [];
        // Filter macros by player skill level
        const suitableMacros = this.library.getAllMacros().filter(macro => {
            const estimatedAPM = macro.estimatedAPM;
            return estimatedAPM <= playerAPM * 1.2; // Allow 20% stretch
        });
        // Rank by situational relevance and success rate
        suitableMacros.forEach(macro => {
            const successRate = this.getSuccessRate(macro.name);
            const situationalRelevance = this.getSituationalRelevance(macro.name, currentSituation);
            const usageFrequency = this.playerStats.preferredMacros.get(macro.name) || 0;
            const score = (successRate * 0.4) + (situationalRelevance * 0.4) + (usageFrequency * 0.2);
            recommendations.push(Object.assign(Object.assign({}, macro), { score,
                successRate,
                situationalRelevance }));
        });
        return recommendations.sort((a, b) => b.score - a.score).slice(0, 5);
    }
    getSuccessRate(macroName) {
        const data = this.playerStats.successRates.get(macroName);
        return data ? data.success / data.total : 0.5; // Default 50% for new macros
    }
    getSituationalRelevance(macroName, situation) {
        const situationKey = `${macroName}_${situation}`;
        const usage = this.playerStats.situationalUsage.get(situationKey) || 0;
        const totalUsage = this.playerStats.preferredMacros.get(macroName) || 1;
        return usage / totalUsage;
    }
    // Generate practice recommendations
    generatePracticeProgram(targetAPM) {
        const currentLevel = this.estimatePlayerLevel();
        const targetLevel = this.apmToLevel(targetAPM);
        const practiceSteps = [];
        // Progressive skill building
        for (let level = currentLevel; level <= targetLevel; level++) {
            const macrosForLevel = this.getMacrosForLevel(level);
            practiceSteps.push({
                level,
                targetAPM: this.levelToAPM(level),
                recommendedMacros: macrosForLevel,
                practiceTime: this.calculatePracticeTime(level),
                focusAreas: this.getFocusAreas(level)
            });
        }
        return practiceSteps;
    }
    estimatePlayerLevel() {
        const avgAPM = this.playerStats.apmHistory.reduce((a, b) => a + b, 0) / this.playerStats.apmHistory.length || 50;
        return this.apmToLevel(avgAPM);
    }
    apmToLevel(apm) {
        if (apm < 100)
            return 1; // Beginner
        if (apm < 150)
            return 2; // Intermediate
        if (apm < 200)
            return 3; // Advanced
        if (apm < 300)
            return 4; // Expert
        return 5; // Master
    }
    levelToAPM(level) {
        const apmTargets = [0, 100, 150, 200, 300, 400];
        return apmTargets[level] || 100;
    }
    getMacrosForLevel(level) {
        const difficulties = ['standard', 'standard', 'advanced', 'advanced', 'pro'];
        return this.library.getMacrosByDifficulty(difficulties[level - 1] || 'standard');
    }
    calculatePracticeTime(level) {
        return Math.max(30, level * 15); // 30-75 minutes per level
    }
    getFocusAreas(level) {
        const areas = [
            [],
            ['Basic hotkeys', 'Simple macro execution'],
            ['Multi-tasking', 'Economic management'],
            ['Army micro', 'Complex macro chaining'],
            ['Perfect timing', 'Adaptive strategies'],
            ['Flawless execution', 'Innovation']
        ];
        return areas[level] || [];
    }
}
exports.AdaptiveMacroSystem = AdaptiveMacroSystem;

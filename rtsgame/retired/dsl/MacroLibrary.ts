/**
 * Macro Library - Pre-built high-APM command sequences
 * 
 * This library contains professionally optimized macro sequences that would
 * normally require 200+ APM to execute manually, but can be triggered with
 * a single hotkey through the TacticsDSL system.
 */

import { CommandBlocks, APMComposer } from './TacticsDSL.js';

export class MacroLibrary {
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
            CommandBlocks.selectAll('worker'),
            CommandBlocks.when('resources_above_threshold'),
            CommandBlocks.build('worker', 5),
            CommandBlocks.selectAll('factory'),
            CommandBlocks.prioritizeProduction('high'),
            CommandBlocks.select('idle_workers'),
            CommandBlocks.moveTo('nearest_resources')
        ], 'Ctrl+E');

        // Basic Army Control - Select and micro army units
        this.register('ARMY_MICRO', [
            CommandBlocks.selectAll('combat'),
            CommandBlocks.formation('wedge'),
            CommandBlocks.when('enemy_in_range'),
            CommandBlocks.focusFire('highest_threat'),
            CommandBlocks.when('health_below_30'),
            CommandBlocks.retreat('rally_point')
        ], 'Ctrl+1');

        // Quick Defense - Rapid defensive response
        this.register('QUICK_DEFENSE', [
            CommandBlocks.selectAll('defensive_units'),
            CommandBlocks.holdPosition(),
            CommandBlocks.select('mobile_units'),
            CommandBlocks.moveTo('threat_location'),
            CommandBlocks.attack('nearest_enemy'),
            CommandBlocks.parallel(
                CommandBlocks.selectAll('factory').chain(CommandBlocks.build('defensive_unit', 3)),
                CommandBlocks.selectAll('worker').chain(CommandBlocks.retreat('safe_zone'))
            )
        ], 'Ctrl+D');

        // Resource Optimization - Maximize resource efficiency
        this.register('RESOURCE_OPTIMIZATION', [
            CommandBlocks.selectAll('worker'),
            CommandBlocks.when('resource_depleted'),
            CommandBlocks.moveTo('alternate_resource'),
            CommandBlocks.selectAll('factory'),
            CommandBlocks.when('resources_sufficient'),
            CommandBlocks.build('worker', 2),
            CommandBlocks.allocateResources({ metal: 0.4, energy: 0.6 })
        ], 'Ctrl+R');
    }

    // Advanced macros for experienced players (100-200 APM equivalent)
    createAdvancedMacros() {
        // Multi-Front Assault - Coordinate attacks on multiple fronts
        this.register('MULTI_FRONT_ASSAULT', [
            CommandBlocks.parallel(
                // Front 1: Main army
                CommandBlocks.sequence(
                    CommandBlocks.select('army_group_1'),
                    CommandBlocks.formation('line'),
                    CommandBlocks.attackMove('enemy_main_base'),
                    CommandBlocks.when('resistance_heavy'),
                    CommandBlocks.focusFire('priority_targets')
                ),
                // Front 2: Flanking force
                CommandBlocks.sequence(
                    CommandBlocks.select('army_group_2'),
                    CommandBlocks.moveTo('flanking_position'),
                    CommandBlocks.delay(5000), // 5 second delay
                    CommandBlocks.attackMove('enemy_rear')
                ),
                // Support: Air cover
                CommandBlocks.sequence(
                    CommandBlocks.select('air_units'),
                    CommandBlocks.patrol(['front_1', 'front_2']),
                    CommandBlocks.attack('enemy_air')
                )
            )
        ], 'Ctrl+Shift+A');

        // Advanced Economy Management - Complex resource balancing
        this.register('ADVANCED_ECONOMY', [
            CommandBlocks.parallel(
                // Worker management
                CommandBlocks.sequence(
                    CommandBlocks.selectAll('worker'),
                    CommandBlocks.when('efficiency_below_80'),
                    CommandBlocks.moveTo('optimal_resource_spots')
                ),
                // Production balancing
                CommandBlocks.sequence(
                    CommandBlocks.selectAll('factory'),
                    CommandBlocks.when('metal_surplus'),
                    CommandBlocks.build('metal_consuming_unit'),
                    CommandBlocks.when('energy_surplus'),
                    CommandBlocks.build('energy_consuming_unit')
                ),
                // Expansion
                CommandBlocks.sequence(
                    CommandBlocks.select('constructor'),
                    CommandBlocks.when('resources_above_2000'),
                    CommandBlocks.moveTo('expansion_site'),
                    CommandBlocks.build('resource_extractor')
                )
            )
        ], 'Ctrl+Shift+E');

        // Tactical Retreat with Counterattack
        this.register('TACTICAL_RETREAT', [
            CommandBlocks.sequence(
                // Phase 1: Retreat
                CommandBlocks.selectAll('damaged_units'),
                CommandBlocks.retreat('repair_station'),
                CommandBlocks.selectAll('healthy_units'),
                CommandBlocks.formation('defensive'),
                CommandBlocks.holdPosition(),
                // Phase 2: Reinforce
                CommandBlocks.delay(3000),
                CommandBlocks.selectAll('factory'),
                CommandBlocks.buildQueue(['tank', 'tank', 'artillery']),
                // Phase 3: Counterattack
                CommandBlocks.when('reinforcements_ready'),
                CommandBlocks.selectAll('combat'),
                CommandBlocks.formation('spearhead'),
                CommandBlocks.attackMove('enemy_weakness')
            )
        ], 'Ctrl+Shift+R');

        // Supply Line Management - Advanced logistics
        this.register('SUPPLY_LINES', [
            CommandBlocks.parallel(
                // Transport management
                CommandBlocks.sequence(
                    CommandBlocks.select('transport_units'),
                    CommandBlocks.when('cargo_available'),
                    CommandBlocks.moveTo('pickup_point'),
                    CommandBlocks.moveTo('dropoff_point'),
                    CommandBlocks.repeat(99, CommandBlocks.callMacro('transport_cycle'))
                ),
                // Supply protection
                CommandBlocks.sequence(
                    CommandBlocks.select('escort_units'),
                    CommandBlocks.patrol(['supply_route_1', 'supply_route_2']),
                    CommandBlocks.when('enemy_detected'),
                    CommandBlocks.attack('threats')
                )
            )
        ], 'Ctrl+Shift+S');
    }

    // Pro-level macros for elite players (200+ APM equivalent)
    createProMacros() {
        // Perfect Storm - Ultimate combined arms assault
        this.register('PERFECT_STORM', [
            CommandBlocks.parallel(
                // Artillery barrage preparation
                CommandBlocks.sequence(
                    CommandBlocks.select('artillery'),
                    CommandBlocks.moveTo('bombardment_position'),
                    CommandBlocks.attack('enemy_defenses'),
                    CommandBlocks.delay(2000)
                ),
                // Air superiority
                CommandBlocks.sequence(
                    CommandBlocks.select('fighters'),
                    CommandBlocks.attack('enemy_air'),
                    CommandBlocks.select('bombers'),
                    CommandBlocks.delay(1000),
                    CommandBlocks.attack('enemy_aa')
                ),
                // Ground assault waves
                CommandBlocks.sequence(
                    // Wave 1: Fast units
                    CommandBlocks.select('fast_attack'),
                    CommandBlocks.attackMove('enemy_flanks'),
                    CommandBlocks.delay(3000),
                    // Wave 2: Main force
                    CommandBlocks.select('main_army'),
                    CommandBlocks.formation('wedge'),
                    CommandBlocks.attackMove('enemy_center'),
                    CommandBlocks.delay(2000),
                    // Wave 3: Heavy support
                    CommandBlocks.select('heavy_units'),
                    CommandBlocks.attackMove('enemy_strongpoints')
                ),
                // Economic protection
                CommandBlocks.sequence(
                    CommandBlocks.selectAll('worker'),
                    CommandBlocks.retreat('fortified_base'),
                    CommandBlocks.selectAll('factory'),
                    CommandBlocks.buildQueue(['defender', 'defender', 'defender'])
                )
            )
        ], 'Ctrl+Alt+Shift+A');

        // Economic Singularity - Ultimate economy optimization
        this.register('ECONOMIC_SINGULARITY', [
            CommandBlocks.parallel(
                // Resource extraction optimization
                CommandBlocks.sequence(
                    CommandBlocks.selectAll('worker'),
                    CommandBlocks.when('resource_efficiency_below_95'),
                    CommandBlocks.moveTo('optimal_spots'),
                    CommandBlocks.build('efficiency_upgrade')
                ),
                // Production line optimization
                CommandBlocks.sequence(
                    CommandBlocks.selectAll('factory'),
                    CommandBlocks.prioritizeProduction('dynamic'),
                    CommandBlocks.when('queue_empty'),
                    CommandBlocks.buildQueue(['worker', 'factory', 'worker']),
                    CommandBlocks.upgrade('production_efficiency')
                ),
                // Advanced expansion
                CommandBlocks.sequence(
                    CommandBlocks.select('advanced_constructor'),
                    CommandBlocks.moveTo('prime_expansion'),
                    CommandBlocks.build('mega_factory'),
                    CommandBlocks.build('resource_processor'),
                    CommandBlocks.build('defense_grid')
                ),
                // Market manipulation
                CommandBlocks.sequence(
                    CommandBlocks.when('metal_price_low'),
                    CommandBlocks.allocateResources({ metal: 0.8, energy: 0.2 }),
                    CommandBlocks.when('energy_price_low'),
                    CommandBlocks.allocateResources({ metal: 0.2, energy: 0.8 })
                )
            )
        ], 'Ctrl+Alt+Shift+E');

        // Hyperspace Maneuver - Advanced tactical positioning
        this.register('HYPERSPACE_MANEUVER', [
            CommandBlocks.sequence(
                // Phase 1: Deception
                CommandBlocks.select('decoy_units'),
                CommandBlocks.attackMove('obvious_attack_route'),
                CommandBlocks.select('main_force'),
                CommandBlocks.moveTo('hidden_staging_area'),
                // Phase 2: Positioning
                CommandBlocks.parallel(
                    CommandBlocks.sequence(
                        CommandBlocks.select('stealth_units'),
                        CommandBlocks.moveTo('enemy_rear'),
                        CommandBlocks.holdPosition()
                    ),
                    CommandBlocks.sequence(
                        CommandBlocks.select('mobile_artillery'),
                        CommandBlocks.moveTo('support_position'),
                        CommandBlocks.attack('enemy_support')
                    )
                ),
                // Phase 3: Synchronized strike
                CommandBlocks.delay(5000),
                CommandBlocks.parallel(
                    CommandBlocks.sequence(
                        CommandBlocks.select('stealth_units'),
                        CommandBlocks.attack('enemy_command')
                    ),
                    CommandBlocks.sequence(
                        CommandBlocks.select('main_force'),
                        CommandBlocks.formation('hammer'),
                        CommandBlocks.attackMove('enemy_weak_point')
                    ),
                    CommandBlocks.sequence(
                        CommandBlocks.select('air_support'),
                        CommandBlocks.attack('enemy_reinforcements')
                    )
                )
            )
        ], 'Ctrl+Alt+Shift+H');

        // Matrix Protocol - Ultimate micro-management
        this.register('MATRIX_PROTOCOL', [
            CommandBlocks.parallel(
                // Individual unit micro for each unit type
                CommandBlocks.sequence(
                    CommandBlocks.select('unit_type_1'),
                    CommandBlocks.repeat(10, 
                        CommandBlocks.sequence(
                            CommandBlocks.when('enemy_targeting'),
                            CommandBlocks.moveTo('evasion_point'),
                            CommandBlocks.delay(200),
                            CommandBlocks.attack('opportunity_target'),
                            CommandBlocks.delay(300)
                        )
                    )
                ),
                CommandBlocks.sequence(
                    CommandBlocks.select('unit_type_2'),
                    CommandBlocks.repeat(10,
                        CommandBlocks.sequence(
                            CommandBlocks.when('cooldown_ready'),
                            CommandBlocks.attack('priority_target'),
                            CommandBlocks.moveTo('safe_position'),
                            CommandBlocks.delay(500)
                        )
                    )
                ),
                // Ability micro
                CommandBlocks.sequence(
                    CommandBlocks.select('ability_units'),
                    CommandBlocks.when('ability_ready'),
                    CommandBlocks.sequence(
                        CommandBlocks.moveTo('optimal_position'),
                        CommandBlocks.delay(100),
                        CommandBlocks.attack('clustered_enemies')
                    )
                )
            )
        ], 'Ctrl+Alt+Shift+M');
    }

    // Register a macro with the composer
    register(name, commandSequence, hotkey) {
        // Convert command sequence to executable macro
        const macro = CommandBlocks.sequence(...commandSequence);
        
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
            if (command.type === 'PARALLEL') complexity += 5;
            if (command.type === 'WHEN') complexity += 3;
            if (command.type === 'REPEAT') complexity += 4;
            if (command.children) complexity += this.calculateComplexity(command.children);
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
            'SELECT': CommandBlocks.select,
            'MOVE': CommandBlocks.moveTo,
            'ATTACK': CommandBlocks.attack,
            'BUILD': CommandBlocks.build,
            'WHEN': CommandBlocks.when,
            'DELAY': CommandBlocks.delay,
            'PARALLEL': CommandBlocks.parallel,
            'SEQUENCE': CommandBlocks.sequence
        };
        
        const constructor = constructors[block.type];
        return constructor ? constructor(block.params) : CommandBlocks.select('default');
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
        } catch (error) {
            console.error('Failed to import macro library:', error);
            return false;
        }
    }
}

// Example usage and demonstration
export function demonstrateMacroLibrary() {
    const composer = new APMComposer();
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
export class AdaptiveMacroSystem {
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
        if (success) successData.success++;
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
            
            recommendations.push({
                ...macro,
                score,
                successRate,
                situationalRelevance
            });
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
        if (apm < 100) return 1; // Beginner
        if (apm < 150) return 2; // Intermediate
        if (apm < 200) return 3; // Advanced
        if (apm < 300) return 4; // Expert
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
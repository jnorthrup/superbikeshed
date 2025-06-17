// js/ui/commandStatusRenderer.js
// Command hierarchy status renderer for border layout windows

import { BorderLayoutContainer, BorderRegion, TextComponent } from './borderLayout.js';
import { CommandHierarchy } from '../ai/commandHierarchy.js';

export class CommandStatusRenderer {
    constructor(team, gameContext) {
        this.team = team;
        this.gameContext = gameContext;
        this.window = null;
        this.updateInterval = 1000; // Update every second
        this.lastUpdate = 0;
        
        // Initialize command hierarchy if it doesn't exist
        if (!gameContext.commandHierarchies) {
            gameContext.commandHierarchies = new Map();
        }
        
        if (!gameContext.commandHierarchies.has(team)) {
            console.log(`Creating command hierarchy for team: ${team}`);
            gameContext.commandHierarchies.set(team, new CommandHierarchy(team));
        }
        
        this.hierarchy = gameContext.commandHierarchies.get(team);
        console.log(`Command hierarchy for ${team} has ${this.hierarchy.commandGroups.length} groups`);
    }

    createWindow(x, y) {
        this.window = new BorderLayoutContainer(x, y, 500, 600, {
            title: `${this.team.toUpperCase()} Command & Control - ${this.team === 'blue' ? 'FRIENDLY' : 'ENEMY'} FORCES`,
            backgroundColor: this.team === 'blue' ? 'rgba(20, 25, 45, 0.95)' : 'rgba(45, 20, 20, 0.95)',
            borderColor: this.team === 'blue' ? '#4488ff' : '#ff4444',
            resizable: true,
            movable: true,
            closable: true
        });

        this.setupLayout();
        this.updateContent();
        
        return this.window;
    }

    setupLayout() {
        // Header - Command Summary
        this.addCommandSummary();
        
        // West - Rank Distribution
        this.addRankDistribution();
        
        // Center - Mission Groups Detail
        this.addMissionGroups();
        
        // East - Unit Status
        this.addUnitStatus();
        
        // South - Activity Log
        this.addActivityLog();
    }

    addCommandSummary() {
        const summary = this.getCommandSummary();
        this.window.addComponent(
            new TextComponent(summary, {
                backgroundColor: this.team === 'blue' ? 'rgba(40, 50, 80, 0.9)' : 'rgba(80, 40, 40, 0.9)',
                alignment: 'center',
                fontSize: 11,
                color: this.team === 'blue' ? '#ddddff' : '#ffdddd',
                padding: 8
            }),
            BorderRegion.NORTH
        );
        this.window.setRegionSize(BorderRegion.NORTH, 60);
    }

    addRankDistribution() {
        const rankInfo = this.getRankDistribution();
        this.window.addComponent(
            new TextComponent(rankInfo, {
                backgroundColor: this.team === 'blue' ? 'rgba(50, 40, 70, 0.9)' : 'rgba(70, 40, 50, 0.9)',
                fontSize: 9,
                color: this.team === 'blue' ? '#ddddff' : '#ffdddd',
                padding: 6
            }),
            BorderRegion.WEST
        );
        this.window.setRegionSize(BorderRegion.WEST, 140);
    }

    addMissionGroups() {
        const groupInfo = this.getMissionGroupsInfo();
        this.window.addComponent(
            new TextComponent(groupInfo, {
                backgroundColor: this.team === 'blue' ? 'rgba(30, 40, 60, 0.9)' : 'rgba(60, 30, 30, 0.9)',
                fontSize: 9,
                color: this.team === 'blue' ? '#ccddff' : '#ffcccc',
                padding: 8
            }),
            BorderRegion.CENTER
        );
    }

    addUnitStatus() {
        const unitStatus = this.getUnitStatusInfo();
        this.window.addComponent(
            new TextComponent(unitStatus, {
                backgroundColor: this.team === 'blue' ? 'rgba(40, 60, 50, 0.9)' : 'rgba(60, 40, 30, 0.9)',
                fontSize: 9,
                color: this.team === 'blue' ? '#ddffdd' : '#ffddcc',
                padding: 6
            }),
            BorderRegion.EAST
        );
        this.window.setRegionSize(BorderRegion.EAST, 120);
    }

    addActivityLog() {
        const activity = this.getActivityLog();
        this.window.addComponent(
            new TextComponent(activity, {
                backgroundColor: 'rgba(50, 50, 30, 0.9)',
                fontSize: 8,
                color: '#ffffcc',
                padding: 6
            }),
            BorderRegion.SOUTH
        );
        this.window.setRegionSize(BorderRegion.SOUTH, 80);
    }

    getCommandSummary() {
        const units = this.gameContext.units.filter(u => u.team === this.team);
        const totalUnits = units.length;
        const commandGroups = this.hierarchy.commandGroups.length;
        const unassigned = this.hierarchy.unassignedUnits.length;
        const efficiency = this.hierarchy.getDistribution().efficiency;

        const teamIcon = this.team === 'blue' ? '🔵' : '🔴';
        const statusIcon = this.team === 'blue' ? '🛡️' : '⚔️';
        
        return `${teamIcon} ${this.team.toUpperCase()} TACTICAL COMMAND ${statusIcon}\n\n` +
               `Total Forces: ${totalUnits} units\n` +
               `Command Groups: ${commandGroups} active\n` +
               `Unassigned: ${unassigned} units\n` +
               `Operational Efficiency: ${efficiency}\n` +
               `Team: ${this.team === 'blue' ? 'FRIENDLY' : 'HOSTILE'}`;
    }

    getRankDistribution() {
        const units = this.gameContext.units.filter(u => u.team === this.team);
        const ranks = {
            'GENERAL': 0,
            'COLONEL': 0,
            'MAJOR': 0,
            'CAPTAIN': 0,
            'LIEUTENANT': 0,
            'SERGEANT': 0,
            'PRIVATE': 0
        };

        units.forEach(unit => {
            const rank = unit.militaryRank || 'PRIVATE';
            if (ranks[rank] !== undefined) {
                ranks[rank]++;
            }
        });

        let rankText = "RANK DISTRIBUTION:\n\n";
        
        Object.entries(ranks).forEach(([rank, count]) => {
            if (count > 0) {
                const stars = '★'.repeat(this.getRankStars(rank));
                rankText += `${stars} ${rank}\n${count} units\n\n`;
            }
        });

        return rankText;
    }

    getRankStars(rank) {
        const starMap = {
            'GENERAL': 5,
            'COLONEL': 4,
            'MAJOR': 3,
            'CAPTAIN': 2,
            'LIEUTENANT': 1,
            'SERGEANT': 1,
            'PRIVATE': 0
        };
        return starMap[rank] || 0;
    }

    getMissionGroupsInfo() {
        const groups = this.hierarchy.commandGroups;
        let groupText = "ACTIVE COMMAND GROUPS:\n\n";

        if (groups.length === 0) {
            groupText += "No active command groups.\nUnits operating independently.";
            return groupText;
        }

        groups.forEach((group, index) => {
            const info = group.getInfo();
            const missionIcon = this.getMissionIcon(info.mission);
            const statusIcon = this.getStatusIcon(group.status);
            
            groupText += `${missionIcon} GROUP ${index + 1} [${info.id}]\n`;
            groupText += `Mission: ${info.mission.toUpperCase()}\n`;
            groupText += `Leader: ${info.leaderRank} ${info.leader}\n`;
            groupText += `Size: ${info.size} units\n`;
            groupText += `Status: ${statusIcon} ${group.status}\n`;
            groupText += `Efficiency: ${info.efficiency}\n`;
            
            // Show member composition
            if (group.members.length > 0) {
                const composition = this.getGroupComposition(group);
                groupText += `Composition: ${composition}\n`;
            }
            
            // Show current objective
            if (group.target) {
                groupText += `Target: ${group.target.type ? group.target.type.name : 'Position'}\n`;
            } else if (group.mission === 'sortie' && group.sortiePhase) {
                groupText += `Sortie Phase: ${group.sortiePhase}\n`;
            }
            
            groupText += "\n";
        });

        // Show mission distribution
        const distribution = this.hierarchy.getDistribution();
        groupText += "MISSION DISTRIBUTION:\n";
        Object.entries(distribution.missions).forEach(([mission, count]) => {
            if (count > 0) {
                const icon = this.getMissionIcon(mission);
                groupText += `${icon} ${mission}: ${count}\n`;
            }
        });

        return groupText;
    }

    getGroupComposition(group) {
        const roles = {};
        group.members.forEach(member => {
            const role = group.getUnitRole(member);
            roles[role] = (roles[role] || 0) + 1;
        });

        return Object.entries(roles)
            .map(([role, count]) => `${count}${this.getRoleIcon(role)}`)
            .join(' ');
    }

    getRoleIcon(role) {
        const icons = {
            'air': '✈️',
            'anti_air': '🎯',
            'ground': '🚗',
            'amphibious': '🚤',
            'support': '🔧'
        };
        return icons[role] || '●';
    }

    getMissionIcon(mission) {
        const icons = {
            'search': '🔍',
            'hunt': '🎯',
            'collect': '📦',
            'defend': '🛡️',
            'assault': '⚔️',
            'support': '🔧',
            'sortie': '✈️'
        };
        return icons[mission] || '📋';
    }

    getStatusIcon(status) {
        const icons = {
            'forming': '⏳',
            'active': '✅',
            'engaged': '⚡',
            'retreating': '↩️',
            'sortie_approach': '➡️',
            'sortie_strike': '💥',
            'sortie_return': '↖️',
            'reformed': '🔄'
        };
        return icons[status] || '●';
    }

    getUnitStatusInfo() {
        const units = this.gameContext.units.filter(u => u.team === this.team);
        let statusText = "UNIT STATUS:\n\n";

        // Health summary
        const healthy = units.filter(u => u.hp > u.maxHp * 0.8).length;
        const damaged = units.filter(u => u.hp <= u.maxHp * 0.8 && u.hp > u.maxHp * 0.3).length;
        const critical = units.filter(u => u.hp <= u.maxHp * 0.3).length;

        statusText += `🟢 Healthy: ${healthy}\n`;
        statusText += `🟡 Damaged: ${damaged}\n`;
        statusText += `🔴 Critical: ${critical}\n\n`;

        // Combat status
        const inCombat = units.filter(u => u.target && u.target.hp > 0).length;
        const moving = units.filter(u => u.patrolTarget || u.path).length;
        const idle = units.filter(u => !u.target && !u.patrolTarget && !u.path).length;

        statusText += "ACTIVITY:\n";
        statusText += `⚔️ Combat: ${inCombat}\n`;
        statusText += `🚶 Moving: ${moving}\n`;
        statusText += `💤 Idle: ${idle}\n\n`;

        // Special units status
        const commander = units.find(u => u.type.name === 'Commander');
        if (commander) {
            statusText += "COMMANDER:\n";
            statusText += `HP: ${Math.ceil(commander.hp)}/${commander.maxHp}\n`;
            if (commander.constructionTask) {
                const progress = Math.floor((commander.constructionTask.progress / commander.constructionTask.type.buildTime) * 100);
                statusText += `Building: ${commander.constructionTask.type.name}\n`;
                statusText += `Progress: ${progress}%\n`;
            }
            statusText += "\n";
        }

        // Support units
        const engineers = units.filter(u => u.type.name === 'Engineer').length;
        if (engineers > 0) {
            statusText += `🔧 Engineers: ${engineers}\n`;
        }

        return statusText;
    }

    getActivityLog() {
        const events = this.gameContext.gameState.events || [];
        let logText = "RECENT ACTIVITY:\n\n";

        // Get last 5 relevant events for this team
        const relevantEvents = events
            .filter(event => 
                event.message.toLowerCase().includes(this.team) ||
                event.type === 'strategic' ||
                event.type === 'battle'
            )
            .slice(0, 5);

        if (relevantEvents.length === 0) {
            logText += "No recent activity.";
            return logText;
        }

        relevantEvents.forEach(event => {
            const time = this.formatTime(event.time);
            const icon = this.getEventIcon(event.type);
            logText += `${icon} ${time}\n${event.message}\n\n`;
        });

        return logText;
    }

    getEventIcon(eventType) {
        const icons = {
            'strategic': '⭐',
            'battle': '⚔️',
            'resource': '📦',
            'build': '🏗️',
            'ability_used': '💥',
            'ui_error': '⚠️'
        };
        return icons[eventType] || '📋';
    }

    formatTime(seconds) {
        const mins = Math.floor(seconds / 60);
        const secs = Math.floor(seconds % 60);
        return `${mins}:${secs.toString().padStart(2, '0')}`;
    }

    update() {
        const now = Date.now();
        if (now - this.lastUpdate < this.updateInterval) return;

        if (this.window && this.window.visible) {
            // Update hierarchy
            this.hierarchy.update(this.gameContext);
            
            // Update all components
            this.updateContent();
        }

        this.lastUpdate = now;
    }

    updateContent() {
        if (!this.window) return;

        // Update each region's content
        const northComponent = this.window.regions.get(BorderRegion.NORTH);
        if (northComponent) {
            northComponent.text = this.getCommandSummary();
        }

        const westComponent = this.window.regions.get(BorderRegion.WEST);
        if (westComponent) {
            westComponent.text = this.getRankDistribution();
        }

        const centerComponent = this.window.regions.get(BorderRegion.CENTER);
        if (centerComponent) {
            centerComponent.text = this.getMissionGroupsInfo();
        }

        const eastComponent = this.window.regions.get(BorderRegion.EAST);
        if (eastComponent) {
            eastComponent.text = this.getUnitStatusInfo();
        }

        const southComponent = this.window.regions.get(BorderRegion.SOUTH);
        if (southComponent) {
            southComponent.text = this.getActivityLog();
        }
    }

    getWindow() {
        return this.window;
    }

    isVisible() {
        return this.window && this.window.visible;
    }

    show() {
        if (this.window) {
            this.window.visible = true;
        }
    }

    hide() {
        if (this.window) {
            this.window.visible = false;
        }
    }

    close() {
        if (this.window) {
            this.window.visible = false;
            this.window = null;
        }
    }
}
/**
 * # Bao-Cline Telemetry System
 * 
 * Tracks where Bao-Cline is sent to die - monitoring the lifecycle of
 * code generation requests, their success/failure rates, and user interactions
 * in the VS Code extension environment.
 * 
 * ## Features
 * - Code generation lifecycle tracking
 * - Error tracking and analysis
 * - User interaction monitoring
 * - Performance metrics collection
 * - Death location tracking (where requests fail)
 * 
 * ## Death Tracking
 * - Request timeout tracking
 * - API failure monitoring
 * - User rejection tracking
 * - Context understanding failures
 * - Model performance issues
 */

import * as vscode from 'vscode';
import { TelemetryReporter } from 'vscode';

export interface TelemetryEvent {
    eventName: string;
    properties?: { [key: string]: string | number | boolean };
    measurements?: { [key: string]: number };
}

export interface DeathLocation {
    location: string;
    reason: string;
    context: string;
    timestamp: Date;
    sessionId: string;
}

export interface CodeGenerationRequest {
    id: string;
    prompt: string;
    language: string;
    context: string;
    startTime: Date;
    endTime?: Date;
    success?: boolean;
    deathLocation?: DeathLocation;
    responseTime?: number;
    tokensGenerated?: number;
    userFeedback?: string;
}

export class BaoClineTelemetry {
    private telemetryReporter: TelemetryReporter;
    private sessionId: string;
    private activeRequests: Map<string, CodeGenerationRequest> = new Map();
    private deathLocations: DeathLocation[] = [];
    private metrics: Map<string, number> = new Map();
    
    constructor(extensionId: string, extensionVersion: string) {
        this.telemetryReporter = new TelemetryReporter(extensionId, extensionVersion, 'your-telemetry-key');
        this.sessionId = this.generateSessionId();
        
        // Track session start
        this.trackEvent('session_start', {
            sessionId: this.sessionId,
            extensionVersion: extensionVersion
        });
    }
    
    /**
     * Track a code generation request start
     */
    trackRequestStart(
        requestId: string,
        prompt: string,
        language: string,
        context: string
    ): void {
        const request: CodeGenerationRequest = {
            id: requestId,
            prompt: prompt,
            language: language,
            context: context,
            startTime: new Date()
        };
        
        this.activeRequests.set(requestId, request);
        
        this.trackEvent('code_generation_start', {
            requestId: requestId,
            language: language,
            contextLength: context.length.toString(),
            promptLength: prompt.length.toString()
        });
        
        this.incrementMetric('requests_started');
    }
    
    /**
     * Track a code generation request completion
     */
    trackRequestSuccess(
        requestId: string,
        responseTime: number,
        tokensGenerated: number,
        userFeedback?: string
    ): void {
        const request = this.activeRequests.get(requestId);
        if (request) {
            request.endTime = new Date();
            request.success = true;
            request.responseTime = responseTime;
            request.tokensGenerated = tokensGenerated;
            request.userFeedback = userFeedback;
            
            this.activeRequests.delete(requestId);
            
            this.trackEvent('code_generation_success', {
                requestId: requestId,
                language: request.language,
                responseTime: responseTime.toString(),
                tokensGenerated: tokensGenerated.toString(),
                userFeedback: userFeedback || 'none'
            }, {
                responseTime: responseTime,
                tokensGenerated: tokensGenerated
            });
            
            this.incrementMetric('requests_successful');
        }
    }
    
    /**
     * Track where Bao-Cline dies (request failure)
     */
    trackRequestDeath(
        requestId: string,
        deathLocation: string,
        reason: string,
        context: string
    ): void {
        const request = this.activeRequests.get(requestId);
        if (request) {
            request.endTime = new Date();
            request.success = false;
            request.deathLocation = {
                location: deathLocation,
                reason: reason,
                context: context,
                timestamp: new Date(),
                sessionId: this.sessionId
            };
            
            this.deathLocations.push(request.deathLocation);
            this.activeRequests.delete(requestId);
            
            this.trackEvent('code_generation_death', {
                requestId: requestId,
                language: request.language,
                deathLocation: deathLocation,
                reason: reason,
                context: context
            });
            
            this.incrementMetric('requests_failed');
            this.incrementMetric(`death_location_${deathLocation}`);
        }
    }
    
    /**
     * Track user interaction with generated code
     */
    trackUserInteraction(
        requestId: string,
        interactionType: 'accept' | 'reject' | 'modify' | 'ignore',
        details?: string
    ): void {
        this.trackEvent('user_interaction', {
            requestId: requestId,
            interactionType: interactionType,
            details: details || 'none'
        });
        
        this.incrementMetric(`user_interaction_${interactionType}`);
        
        if (interactionType === 'reject') {
            // Track rejection as a death location
            this.trackRequestDeath(
                requestId,
                'user_rejection',
                'User rejected the generated code',
                details || 'No details provided'
            );
        }
    }
    
    /**
     * Track context understanding failures
     */
    trackContextFailure(
        requestId: string,
        failureType: 'file_not_found' | 'parse_error' | 'context_too_large' | 'unsupported_language',
        details: string
    ): void {
        this.trackRequestDeath(
            requestId,
            'context_failure',
            `Context understanding failed: ${failureType}`,
            details
        );
        
        this.trackEvent('context_failure', {
            requestId: requestId,
            failureType: failureType,
            details: details
        });
        
        this.incrementMetric(`context_failure_${failureType}`);
    }
    
    /**
     * Track API failures
     */
    trackAPIFailure(
        requestId: string,
        errorType: 'timeout' | 'rate_limit' | 'authentication' | 'server_error' | 'network_error',
        errorMessage: string,
        responseTime?: number
    ): void {
        this.trackRequestDeath(
            requestId,
            'api_failure',
            `API failure: ${errorType}`,
            errorMessage
        );
        
        this.trackEvent('api_failure', {
            requestId: requestId,
            errorType: errorType,
            errorMessage: errorMessage,
            responseTime: responseTime?.toString() || 'unknown'
        }, {
            responseTime: responseTime || 0
        });
        
        this.incrementMetric(`api_failure_${errorType}`);
    }
    
    /**
     * Track model performance issues
     */
    trackModelPerformance(
        requestId: string,
        issueType: 'slow_response' | 'poor_quality' | 'hallucination' | 'incomplete_response',
        details: string,
        responseTime?: number
    ): void {
        this.trackEvent('model_performance_issue', {
            requestId: requestId,
            issueType: issueType,
            details: details,
            responseTime: responseTime?.toString() || 'unknown'
        }, {
            responseTime: responseTime || 0
        });
        
        this.incrementMetric(`model_issue_${issueType}`);
    }
    
    /**
     * Track file operations
     */
    trackFileOperation(
        operation: 'open' | 'save' | 'create' | 'delete',
        filePath: string,
        language?: string,
        fileSize?: number
    ): void {
        this.trackEvent('file_operation', {
            operation: operation,
            filePath: filePath,
            language: language || 'unknown',
            fileSize: fileSize?.toString() || 'unknown'
        });
        
        this.incrementMetric(`file_operation_${operation}`);
    }
    
    /**
     * Track code completion interactions
     */
    trackCodeCompletion(
        language: string,
        completionType: 'function' | 'variable' | 'import' | 'snippet',
        accepted: boolean,
        responseTime: number
    ): void {
        this.trackEvent('code_completion', {
            language: language,
            completionType: completionType,
            accepted: accepted.toString(),
            responseTime: responseTime.toString()
        }, {
            responseTime: responseTime
        });
        
        this.incrementMetric('code_completion_attempts');
        if (accepted) {
            this.incrementMetric('code_completion_accepted');
        } else {
            this.incrementMetric('code_completion_rejected');
        }
    }
    
    /**
     * Track extension activation/deactivation
     */
    trackExtensionLifecycle(
        event: 'activated' | 'deactivated',
        reason?: string
    ): void {
        this.trackEvent(`extension_${event}`, {
            reason: reason || 'normal',
            sessionId: this.sessionId
        });
        
        this.incrementMetric(`extension_${event}`);
    }
    
    /**
     * Track settings changes
     */
    trackSettingChange(
        settingName: string,
        oldValue: string,
        newValue: string
    ): void {
        this.trackEvent('setting_changed', {
            settingName: settingName,
            oldValue: oldValue,
            newValue: newValue
        });
        
        this.incrementMetric('settings_changed');
    }
    
    /**
     * Get death statistics
     */
    getDeathStatistics(): {
        totalDeaths: number;
        deathLocations: { [location: string]: number };
        deathReasons: { [reason: string]: number };
        recentDeaths: DeathLocation[];
    } {
        const deathLocations: { [location: string]: number } = {};
        const deathReasons: { [reason: string]: number } = {};
        
        this.deathLocations.forEach(death => {
            deathLocations[death.location] = (deathLocations[death.location] || 0) + 1;
            deathReasons[death.reason] = (deathReasons[death.reason] || 0) + 1;
        });
        
        return {
            totalDeaths: this.deathLocations.length,
            deathLocations: deathLocations,
            deathReasons: deathReasons,
            recentDeaths: this.deathLocations.slice(-10) // Last 10 deaths
        };
    }
    
    /**
     * Get session statistics
     */
    getSessionStatistics(): {
        sessionId: string;
        totalRequests: number;
        successfulRequests: number;
        failedRequests: number;
        successRate: number;
        averageResponseTime: number;
        metrics: { [key: string]: number };
    } {
        const totalRequests = this.metrics.get('requests_started') || 0;
        const successfulRequests = this.metrics.get('requests_successful') || 0;
        const failedRequests = this.metrics.get('requests_failed') || 0;
        const successRate = totalRequests > 0 ? (successfulRequests / totalRequests) * 100 : 0;
        
        const metrics: { [key: string]: number } = {};
        this.metrics.forEach((value, key) => {
            metrics[key] = value;
        });
        
        return {
            sessionId: this.sessionId,
            totalRequests: totalRequests,
            successfulRequests: successfulRequests,
            failedRequests: failedRequests,
            successRate: successRate,
            averageResponseTime: 0, // TODO: Calculate from actual response times
            metrics: metrics
        };
    }
    
    /**
     * Export telemetry data
     */
    exportTelemetryData(): {
        sessionStats: any;
        deathStats: any;
        activeRequests: CodeGenerationRequest[];
        deathLocations: DeathLocation[];
    } {
        return {
            sessionStats: this.getSessionStatistics(),
            deathStats: this.getDeathStatistics(),
            activeRequests: Array.from(this.activeRequests.values()),
            deathLocations: this.deathLocations
        };
    }
    
    /**
     * Dispose telemetry
     */
    dispose(): void {
        // Track session end
        this.trackEvent('session_end', {
            sessionId: this.sessionId,
            duration: this.getSessionDuration().toString()
        });
        
        // Send final statistics
        const stats = this.getSessionStatistics();
        this.trackEvent('session_statistics', {
            sessionId: this.sessionId,
            totalRequests: stats.totalRequests.toString(),
            successRate: stats.successRate.toString()
        });
        
        this.telemetryReporter.dispose();
    }
    
    // Private methods
    
    private trackEvent(
        eventName: string,
        properties?: { [key: string]: string | number | boolean },
        measurements?: { [key: string]: number }
    ): void {
        this.telemetryReporter.sendTelemetryEvent(eventName, properties, measurements);
    }
    
    private incrementMetric(name: string): void {
        const current = this.metrics.get(name) || 0;
        this.metrics.set(name, current + 1);
    }
    
    private generateSessionId(): string {
        return `session_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
    }
    
    private getSessionDuration(): number {
        // Calculate session duration in milliseconds
        return Date.now() - parseInt(this.sessionId.split('_')[1]);
    }
    
    // TODO: Add support for telemetry data persistence
    TODO("Implement telemetry data persistence to local storage");
    
    // TODO: Add support for telemetry data export
    TODO("Implement telemetry data export functionality");
    
    // TODO: Add support for telemetry data visualization
    TODO("Implement telemetry data visualization dashboard");
    
    // TODO: Add support for telemetry data anonymization
    TODO("Implement telemetry data anonymization");
    
    // TODO: Add support for telemetry data retention policies
    TODO("Implement telemetry data retention policies");
}

/**
 * Global Bao-Cline telemetry instance
 */
export class BaoClineTelemetryGlobal {
    private static instance: BaoClineTelemetry | null = null;
    
    static initialize(extensionId: string, extensionVersion: string): BaoClineTelemetry {
        if (!this.instance) {
            this.instance = new BaoClineTelemetry(extensionId, extensionVersion);
        }
        return this.instance;
    }
    
    static getInstance(): BaoClineTelemetry | null {
        return this.instance;
    }
    
    static dispose(): void {
        this.instance?.dispose();
        this.instance = null;
    }
} 
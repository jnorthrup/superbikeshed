// Deterministic Random Number Generator - Ensures reproducible randomness for battle replay
// Uses Linear Congruential Generator (LCG) algorithm for predictable random sequences

export class DeterministicRNG {
    constructor(seed = Date.now()) {
        this.seed = seed;
        this.state = seed;
        this.originalSeed = seed;
        this.callCount = 0;
        this.history = []; // Track random calls for debugging
    }

    // Linear Congruential Generator
    // Parameters from Numerical Recipes (a = 1664525, c = 1013904223, m = 2^32)
    next() {
        this.callCount++;
        this.state = (this.state * 1664525 + 1013904223) % 4294967296;
        const result = this.state / 4294967296;
        
        // Record call for debugging if needed
        if (this.history.length < 1000) { // Limit history size
            this.history.push({
                call: this.callCount,
                seed: this.state,
                value: result
            });
        }
        
        return result;
    }

    // Generate random float between 0 and 1
    random() {
        return this.next();
    }

    // Generate random integer between min and max (inclusive)
    randomInt(min, max) {
        return Math.floor(this.random() * (max - min + 1)) + min;
    }

    // Generate random float between min and max
    randomFloat(min, max) {
        return this.random() * (max - min) + min;
    }

    // Generate random boolean with given probability (0-1)
    randomBool(probability = 0.5) {
        return this.random() < probability;
    }

    // Choose random element from array
    randomChoice(array) {
        if (array.length === 0) return null;
        return array[this.randomInt(0, array.length - 1)];
    }

    // Shuffle array in place using Fisher-Yates algorithm
    shuffle(array) {
        const shuffled = [...array];
        for (let i = shuffled.length - 1; i > 0; i--) {
            const j = this.randomInt(0, i);
            [shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]];
        }
        return shuffled;
    }

    // Generate random position within bounds
    randomPosition(minX, maxX, minY, maxY) {
        return {
            x: this.randomFloat(minX, maxX),
            y: this.randomFloat(minY, maxY)
        };
    }

    // Generate random angle in radians
    randomAngle() {
        return this.randomFloat(0, Math.PI * 2);
    }

    // Generate random direction vector
    randomDirection() {
        const angle = this.randomAngle();
        return {
            x: Math.cos(angle),
            y: Math.sin(angle)
        };
    }

    // Reset to original seed for replay
    reset() {
        this.state = this.originalSeed;
        this.callCount = 0;
        this.history = [];
    }

    // Set new seed and reset
    setSeed(seed) {
        this.seed = seed;
        this.originalSeed = seed;
        this.state = seed;
        this.callCount = 0;
        this.history = [];
    }

    // Get current state for saving/restoring
    getState() {
        return {
            seed: this.seed,
            originalSeed: this.originalSeed,
            state: this.state,
            callCount: this.callCount
        };
    }

    // Restore from saved state
    setState(savedState) {
        this.seed = savedState.seed;
        this.originalSeed = savedState.originalSeed;
        this.state = savedState.state;
        this.callCount = savedState.callCount;
        this.history = [];
    }

    // Generate reproducible noise for terrain generation
    noise2D(x, y, scale = 1) {
        const tempState = this.state;
        const tempCallCount = this.callCount;
        
        // Use position to create deterministic seed offset
        const coordSeed = Math.floor(x * 73856093) ^ Math.floor(y * 19349663);
        this.state = (this.originalSeed + coordSeed) % 4294967296;
        this.callCount = 0;
        
        const noise = this.random() * scale;
        
        // Restore original state
        this.state = tempState;
        this.callCount = tempCallCount;
        
        return noise;
    }

    // Generate debug report
    getDebugInfo() {
        return {
            originalSeed: this.originalSeed,
            currentSeed: this.seed,
            currentState: this.state,
            callCount: this.callCount,
            historyLength: this.history.length,
            recentCalls: this.history.slice(-5)
        };
    }

    // Generate a random point within a circle
    randomPointInCircle(centerX, centerY, radius) {
        const angle = this.random() * Math.PI * 2;
        const r = radius * Math.sqrt(this.random());
        return {
            x: centerX + r * Math.cos(angle),
            y: centerY + r * Math.sin(angle)
        };
    }
    
    // Generate a random point within a rectangle
    randomPointInRect(x, y, width, height) {
        return {
            x: x + this.random() * width,
            y: y + this.random() * height
        };
    }
    
    // Generate a random point on the perimeter of a circle
    randomPointOnCircle(centerX, centerY, radius) {
        const angle = this.random() * Math.PI * 2;
        return {
            x: centerX + radius * Math.cos(angle),
            y: centerY + radius * Math.sin(angle)
        };
    }
    
    // Generate a random point on a line segment
    randomPointOnLine(x1, y1, x2, y2) {
        const t = this.random();
        return {
            x: x1 + t * (x2 - x1),
            y: y1 + t * (y2 - y1)
        };
    }
    
    // Generate a random point in a polygon (using rejection sampling)
    randomPointInPolygon(vertices) {
        // Find bounding box
        let minX = Infinity, minY = Infinity;
        let maxX = -Infinity, maxY = -Infinity;
        
        for (const vertex of vertices) {
            minX = Math.min(minX, vertex.x);
            minY = Math.min(minY, vertex.y);
            maxX = Math.max(maxX, vertex.x);
            maxY = Math.max(maxY, vertex.y);
        }
        
        // Try points until we find one inside the polygon
        while (true) {
            const point = {
                x: this.randomFloat(minX, maxX),
                y: this.randomFloat(minY, maxY)
            };
            
            if (this.isPointInPolygon(point, vertices)) {
                return point;
            }
        }
    }
    
    // Check if a point is inside a polygon using ray casting algorithm
    isPointInPolygon(point, vertices) {
        let inside = false;
        for (let i = 0, j = vertices.length - 1; i < vertices.length; j = i++) {
            const xi = vertices[i].x, yi = vertices[i].y;
            const xj = vertices[j].x, yj = vertices[j].y;
            
            const intersect = ((yi > point.y) !== (yj > point.y))
                && (point.x < (xj - xi) * (point.y - yi) / (yj - yi) + xi);
            if (intersect) inside = !inside;
        }
        return inside;
    }
    
    // Generate a random point in a triangle
    randomPointInTriangle(x1, y1, x2, y2, x3, y3) {
        const r1 = this.random();
        const r2 = this.random();
        
        const sqrtR1 = Math.sqrt(r1);
        return {
            x: (1 - sqrtR1) * x1 + sqrtR1 * (1 - r2) * x2 + sqrtR1 * r2 * x3,
            y: (1 - sqrtR1) * y1 + sqrtR1 * (1 - r2) * y2 + sqrtR1 * r2 * y3
        };
    }
    
    // Generate a random point in a sector of a circle
    randomPointInSector(centerX, centerY, radius, startAngle, endAngle) {
        const angle = this.randomFloat(startAngle, endAngle);
        const r = radius * Math.sqrt(this.random());
        return {
            x: centerX + r * Math.cos(angle),
            y: centerY + r * Math.sin(angle)
        };
    }
    
    // Generate a random point in an annulus (ring)
    randomPointInAnnulus(centerX, centerY, innerRadius, outerRadius) {
        const angle = this.random() * Math.PI * 2;
        const r = Math.sqrt(this.random() * (outerRadius * outerRadius - innerRadius * innerRadius) + innerRadius * innerRadius);
        return {
            x: centerX + r * Math.cos(angle),
            y: centerY + r * Math.sin(angle)
        };
    }
}

// Global deterministic RNG instance
export const gameRNG = new DeterministicRNG();

// Replace Math.random with deterministic version for game logic
export function enableDeterministicMode(seed = null) {
    if (seed !== null) {
        gameRNG.setSeed(seed);
    }
    
    // Store original Math.random
    if (!Math._originalRandom) {
        Math._originalRandom = Math.random;
    }
    
    // Replace Math.random with deterministic version
    Math.random = () => gameRNG.random();
    
    console.log(`🎲 Deterministic RNG enabled with seed: ${gameRNG.originalSeed}`);
}

// Restore original Math.random
export function disableDeterministicMode() {
    if (Math._originalRandom) {
        Math.random = Math._originalRandom;
        console.log('🎲 Deterministic RNG disabled, restored original Math.random');
    }
}

// Utility functions for battle replay
export function createReproducibleSeed(battleId, timestamp = Date.now()) {
    // Create seed from battle ID and timestamp for reproducibility
    let hash = 0;
    const str = `${battleId}_${timestamp}`;
    for (let i = 0; i < str.length; i++) {
        const char = str.charCodeAt(i);
        hash = ((hash << 5) - hash) + char;
        hash = hash & hash; // Convert to 32-bit integer
    }
    return Math.abs(hash);
}
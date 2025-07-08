/**
 * A* Pathfinding Implementation
 */

class Node {
    constructor(x, y, walkable = true) {
        this.x = x;
        this.y = y;
        this.walkable = walkable;
        this.g = 0; // Cost from start to current node
        this.h = 0; // Heuristic cost from current node to end
        this.f = 0; // Total cost (g + h)
        this.parent = null;
    }
}

export function findPath(startX, startY, endX, endY, grid) {
    const openSet = new Set();
    const closedSet = new Set();
    const startNode = new Node(startX, startY);
    const endNode = new Node(endX, endY);
    
    openSet.add(startNode);
    
    while (openSet.size > 0) {
        // Find node with lowest f cost
        let current = null;
        let lowestF = Infinity;
        
        for (const node of openSet) {
            if (node.f < lowestF) {
                lowestF = node.f;
                current = node;
            }
        }
        
        // If we reached the end, reconstruct and return the path
        if (current.x === endNode.x && current.y === endNode.y) {
            return reconstructPath(current);
        }
        
        // Move current node from open to closed set
        openSet.delete(current);
        closedSet.add(current);
        
        // Check all neighbors
        const neighbors = getNeighbors(current, grid);
        for (const neighbor of neighbors) {
            if (closedSet.has(neighbor)) {
                continue;
            }
            
            const tentativeG = current.g + 1;
            
            if (!openSet.has(neighbor)) {
                openSet.add(neighbor);
            } else if (tentativeG >= neighbor.g) {
                continue;
            }
            
            // This path is the best until now
            neighbor.parent = current;
            neighbor.g = tentativeG;
            neighbor.h = heuristic(neighbor, endNode);
            neighbor.f = neighbor.g + neighbor.h;
        }
    }
    
    // No path found
    return null;
}

function getNeighbors(node, grid) {
    const neighbors = [];
    const directions = [
        [-1, -1], [0, -1], [1, -1],
        [-1, 0],           [1, 0],
        [-1, 1],  [0, 1],  [1, 1]
    ];
    
    for (const [dx, dy] of directions) {
        const newX = node.x + dx;
        const newY = node.y + dy;
        
        // Check bounds
        if (newX < 0 || newX >= grid[0].length || newY < 0 || newY >= grid.length) {
            continue;
        }
        
        // Check if walkable
        if (!isWalkable(grid[newY][newX])) {
            continue;
        }
        
        neighbors.push(new Node(newX, newY));
    }
    
    return neighbors;
}

function isWalkable(tile) {
    // Add your terrain walkability logic here
    return tile !== 0; // Example: 0 represents unwalkable terrain
}

function heuristic(a, b) {
    // Manhattan distance
    return Math.abs(a.x - b.x) + Math.abs(a.y - b.y);
}

function reconstructPath(node) {
    const path = [];
    let current = node;
    
    while (current) {
        path.unshift({ x: current.x, y: current.y });
        current = current.parent;
    }
    
    return path;
} 
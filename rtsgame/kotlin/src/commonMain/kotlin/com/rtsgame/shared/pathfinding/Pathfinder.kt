package com.rtsgame.shared.pathfinding

import com.rtsgame.shared.entity.Position
import com.rtsgame.shared.map.GameMap
import kotlin.math.abs
import kotlin.math.sqrt

class Pathfinder(internal val gameMap: GameMap) {
    data class Node(
        val x: Int,
        val y: Int,
        var g: Float = 0f,
        var h: Float = 0f,
        var parent: Node? = null
    ) {
        val f: Float get() = g + h
    }

    fun findPath(start: Position, end: Position): List<Position>? {
        val startX = (start.x / gameMap.tileSize).toInt()
        val startY = (start.y / gameMap.tileSize).toInt()
        val endX = (end.x / gameMap.tileSize).toInt()
        val endY = (end.y / gameMap.tileSize).toInt()

        if (!gameMap.isWalkable(start) || !gameMap.isWalkable(end)) {
            return null
        }

        val openSet = mutableSetOf<Node>()
        val closedSet = mutableSetOf<Node>()
        val startNode = Node(startX, startY)
        val endNode = Node(endX, endY)

        openSet.add(startNode)

        while (openSet.isNotEmpty()) {
            val current = openSet.minByOrNull { it.f } ?: break
            if (current.x == endX && current.y == endY) {
                return reconstructPath(current)
            }

            openSet.remove(current)
            closedSet.add(current)

            for ((nx, ny) in gameMap.getNeighbors(current.x, current.y)) {
                val neighbor = Node(nx, ny)
                if (neighbor in closedSet) continue

                val tentativeG = current.g + distance(current.x, current.y, nx, ny)
                val isNewPath = neighbor !in openSet || tentativeG < neighbor.g

                if (isNewPath) {
                    neighbor.parent = current
                    neighbor.g = tentativeG
                    neighbor.h = distance(nx, ny, endX, endY)
                    if (neighbor !in openSet) {
                        openSet.add(neighbor)
                    }
                }
            }
        }

        return null
    }

    internal fun reconstructPath(endNode: Node): List<Position> {
        val path = mutableListOf<Position>()
        var current: Node? = endNode

        while (current != null) {
            path.add(0, Position(
                x = current.x * gameMap.tileSize + gameMap.tileSize / 2,
                y = current.y * gameMap.tileSize + gameMap.tileSize / 2
            ))
            current = current.parent
        }

        return path
    }

    internal fun distance(x1: Int, y1: Int, x2: Int, y2: Int): Float {
        val dx = abs(x2 - x1)
        val dy = abs(y2 - y1)
        return sqrt((dx * dx + dy * dy).toFloat())
    }
} 
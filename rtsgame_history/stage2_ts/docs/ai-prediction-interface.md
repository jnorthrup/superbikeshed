> **Note: This document may be outdated and refer to a previous version of the rtsgame project (likely TypeScript/JavaScript-based). The current project has been significantly refactored to Kotlin Multiplatform with WebGPU. Please cross-reference with the main `rtsgame/README.md` for the latest project information.**

# AI Prediction Interface - Initial Design

## 1. Introduction

The goal of this system is to create an interactive experience where the game's AI can visualize its predictions about future game states or enemy actions directly to the human player. The player can then interact with these visualizations to acknowledge, dispute, or even counter-predict, thereby influencing game events and subsequent AI behavior. This document outlines the initial design for the first iteration of this feature, focusing on a single, core prediction type.

## 2. Chosen Initial Prediction Types

For the initial implementation, the chosen predictions are:

*   **Prediction 1:** "Next Likely Enemy Ground Attack Vector/Target Area."
    *   **Reasoning:**
        *   **Actionable:** Provides clear information that the player can act upon (e.g., by reinforcing, setting ambushes, or repositioning).
        *   **Common RTS Scenario:** Predicting enemy attacks is a fundamental aspect of RTS gameplay.
        *   **Good Visual Potential:** The path and target area can be clearly represented on the game map.
    *   **AI Output Details & Generation:**
        *   The AI's prediction is stored in `simulation.strategicAI.currentPrediction`.
        *   Predictions are periodically updated by the `StrategicAI.update()` method, governed by `PREDICTION_UPDATE_INTERVAL` (10 seconds) and `predictionUpdateCooldown`.
        *   **Heuristic in `generateAttackPrediction`:**
            *   Filters for active enemy ground units and the AI's opponent's buildings (e.g., if AI is 'red', it targets 'blue' buildings).
            *   **Target Selection:** For simplicity, it currently selects the first found opponent building as the `potentialTarget`.
            *   **Attacker Identification:** Identifies the enemy ground unit closest to this `potentialTarget` as the representative "attacking force" origin (`attackerCentroid`).
            *   **Pathfinding:** Uses `findPath` from `attackerCentroid` to `potentialTarget` to generate the `predictedPath`.
            *   **Confidence:** Determined by the proximity of `closestEnemyUnit` to `potentialTarget` ('low', 'medium', 'high').
            *   **Prediction Object Structure:**
                ```javascript
                {
                  id: string, // e.g., "pred_red_1678886400000"
                  path: Array<{x: number, y: number}>,
                  targetArea: { x: number, y: number, radius: number }, // Centered on potentialTarget, radius 50
                  confidence: 'low' | 'medium' | 'high',
                  type: 'ENEMY_GROUND_ATTACK',
                  timestamp: number, // Game time of prediction
                  playerAcknowledged: false // Boolean flag, initially false
                }
                ```

*   **Prediction 2:** "Enemy Formation Movement Pattern"
    *   **Reasoning:**
        *   **Tactical Value:** Understanding enemy formation patterns helps in planning counter-formations and flanking maneuvers.
        *   **Strategic Depth:** Adds another layer of tactical decision-making to the game.
        *   **Visual Interest:** Formation patterns can be represented with clear visual indicators.
    *   **AI Output Details & Generation:**
        *   **Formation Analysis:**
            *   Detects enemy units moving in formation using `FormationSystem` analysis.
            *   Identifies formation type (LINE, COLUMN, WEDGE, etc.).
            *   Tracks formation leader and predicted movement.
        *   **Prediction Object Structure:**
            ```javascript
            {
              id: string,
              formationType: 'LINE' | 'COLUMN' | 'WEDGE' | 'BOX' | 'CIRCLE' | 'SCATTER',
              leaderPosition: { x: number, y: number },
              predictedPath: Array<{x: number, y: number}>,
              formationArea: { x: number, y: number, radius: number },
              confidence: 'low' | 'medium' | 'high',
              type: 'ENEMY_FORMATION_MOVEMENT',
              timestamp: number,
              playerAcknowledged: false,
              suggestedCounterFormation: string // Recommended formation type to counter
            }
            ```

## 3. Basic Visualization Design

Implemented in `renderAIPredictionsDebug` (located in `rtsgame/js/ai/strategicAI.js` and attached to `window.debugRTS` for global access).

*   **Attack Path Visualization:**
    *   **Description:** Rendered as a line with small chevrons (arrowheads) at the midpoint of each segment, drawn on the 2D canvas.
    *   **Color Coding:** Based on `prediction.confidence`:
        *   `'low'`: Semi-transparent yellow (`rgba(255, 255, 0, alpha)`).
        *   `'medium'`: Semi-transparent orange (`rgba(255, 165, 0, alpha)`).
        *   `'high'`: Semi-transparent red (`rgba(255, 0, 0, alpha)`).
        *   Alpha values for path stroke are typically higher than for area fill.
*   **Target Area Visualization:**
    *   **Description:** Rendered as a filled circle with an optional border at `prediction.targetArea`.
    *   **Color Coding:** Uses the same base RGB as the path, but with a lower alpha for the fill to make it more transparent.
*   **Formation Visualization:**
    *   **Description:** Rendered as a series of connected points representing the formation shape.
    *   **Color Coding:** Similar to attack path, but with formation-specific styling:
        *   Different line patterns for different formation types
        *   Formation leader highlighted with a distinct marker
        *   Formation area shown as a semi-transparent overlay
*   **Acknowledged Prediction Visualization:**
    *   If `prediction.playerAcknowledged === true`, both the path and target area are rendered in a distinct bright cyan color, overriding the confidence-based colors.
*   **General Appearance:** Visualizations are noticeable but semi-transparent to avoid completely obscuring gameplay. Line widths and sizes are scaled with camera zoom.

## 4. Basic Player Interaction Design

*   **Interaction Method:** Implemented via a `'contextmenu'` event listener on the main canvas in `rtsgame/js/input/inputHandler.js`. Right-clicking triggers the interaction.
*   **Hit Detection:**
    *   The `isPointOnPrediction(worldX, worldY, prediction, camera)` helper function (in `inputHandler.js`) determines if the click hit a prediction.
    *   It checks if the click is within the circular `prediction.targetArea` or `prediction.formationArea`.
    *   It also checks if the click is near any segment of the `prediction.path` using a point-to-line-segment distance calculation (current threshold: 10 world units).
*   **Player Actions (Implemented):**
    *   If a right-click hits a prediction, the system currently defaults to the "Acknowledge & Reinforce" action.
    *   A console log confirms the interaction: `Player interacted (right-click) with prediction: [ID]`.
    *   The `PlayerInteraction_AckReinforce_AttackVector` or `PlayerInteraction_AckReinforce_Formation` game event is dispatched.
*   **Player Actions (Future - via context menu as per original design):**
    1.  "Acknowledge & Reinforce"
    2.  "Dispute & Monitor"
    3.  "Counter-Predict: New Threat"
    4.  "Counter-Formation: Suggest Alternative"

## 5. Resulting Game Events

*   **`PlayerInteraction_AckReinforce_AttackVector`**:
    *   **Payload (Implemented):**
        ```json
        {
          "predictedPathID": "string",
          "confidence": "string", // Confidence level at the time of interaction
          "playerReinforceFocus": true
        }
        ```
    *   (Timestamp is part of the internal event object created by `gameState.addEvent`, not explicitly added to payload here).

*   **`PlayerInteraction_AckReinforce_Formation`**:
    *   **Payload:**
        ```json
        {
          "predictedFormationID": "string",
          "confidence": "string",
          "playerReinforceFocus": true,
          "suggestedCounterFormation": "string"
        }
        ```

## 6. AI Reaction to Player Interaction

*   **Event Processing:** The `StrategicAI.update()` method filters `gameContext.gameState.events` for prediction-related events.
*   **Handler:** For each matching event, the appropriate handler is called:
    *   `StrategicAI.handlePlayerPredictionInteraction(event)` for attack vectors
    *   `StrategicAI.handlePlayerFormationInteraction(event)` for formation predictions
*   **State Update:**
    *   The handler sets `this.currentPrediction.playerAcknowledged = true`.
    *   It then boosts the `this.currentPrediction.confidence`:
        *   'low' becomes 'medium'.
        *   'medium' becomes 'high'.
        *   'high' remains 'high'.
    *   For formation predictions, it also considers the player's suggested counter-formation.
*   **Event Consumption:** Processed events are filtered out of the `gameContext.gameState.events` array by `StrategicAI.update` to prevent re-processing.

## 7. Integration into Game Loop

*   **Instantiation:** An instance of `StrategicAI` is created in `rtsgame/js/app.js` during game initialization and assigned to `simulation.strategicAI`. It's initialized for the 'red' team: `simulation.strategicAI = new StrategicAI('red');`.
*   **AI Update Call:** The `simulation.strategicAI.update(...)` method is called every frame within the `animate` function in `rtsgame/js/app.js`, passing the main `simulation` object as `gameContext`, the AI's team, and current game entities.
*   **Rendering:** The `renderAIPredictionsDebug` function is called from the `render` method of `rtsgame/js/rendering/webgl_2d_renderer.js`, using the 2D canvas context `ctx` and the `simulation` object (as `gameContext`).

This document reflects the current state of the AI Prediction Interface implementation.

Now, I will proceed to **Step 2: Add Inline Comments to Code**. I'll handle each file separately.

**File 1: `rtsgame/js/ai/strategicAI.js`**

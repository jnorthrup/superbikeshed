# RequestFactory RTS Codec

## Why RequestFactory Fits Perfectly

The RTS simulation is fundamentally a stream of:
- **Commands** (player/AI inputs)
- **State Updates** (simulation ticks)
- **Queries** (fog of war, unit selection)
- **Events** (combat, construction, death)

RequestFactory provides the codec layer naturally:

```kotlin
// KMP Side
sealed class RTSRequest {
    data class MoveUnit(val unitId: Int, val x: Double, val y: Double) : RTSRequest()
    data class BuildStructure(val type: String, val x: Double, val y: Double) : RTSRequest()
    data class AttackTarget(val attackerId: Int, val targetId: Int) : RTSRequest()
    data class SimulationTick(val deltaTime: Double, val frameNumber: Long) : RTSRequest()
}

// JS Side receives serialized requests
{
  "type": "MoveUnit",
  "unitId": 42,
  "x": 100.5,
  "y": 200.7
}
```

## Deterministic Replay via Request Stream

Instead of serializing entire game states, we only need:
1. **Initial seed**
2. **Request stream** (with frame numbers)
3. **Identical simulation logic**

The RequestFactory becomes the **synchronization protocol**:

```kotlin
// Record mode
class RTSRecorder(val requestFactory: RequestFactory) {
    fun recordFrame(requests: List<RTSRequest>) {
        requests.forEach { request ->
            requestFactory.serialize(request, frameNumber)
        }
    }
}

// Playback mode  
class RTSPlayer(val requestFactory: RequestFactory) {
    fun playbackFrame(frameNumber: Long): List<RTSRequest> {
        return requestFactory.deserializeFrame(frameNumber)
    }
}
```

## Cross-Platform Synchronization

JS and KMP clients can play together:
1. Both run identical simulation logic
2. RequestFactory handles serialization differences
3. Only requests cross the wire, not full state
4. Deterministic simulation ensures sync

## Override Injection

RequestFactory makes AI overrides clean:

```kotlin
class RTSRequestInterceptor(val ai: StrategicAI) {
    fun processRequest(request: RTSRequest): RTSRequest {
        return when (request) {
            is MoveUnit -> {
                if (shouldOverride(request)) {
                    ai.suggestBetterMove(request)
                } else request
            }
            else -> request
        }
    }
}
```

The RequestFactory pattern turns the chaotic port into a clean protocol definition!
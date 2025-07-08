# TrikeAria Batch Control & Media Player Integration

## Overview

TrikeAria provides advanced batch control and media player integration capabilities, allowing you to manage torrent downloads in organized batches with priority control, scheduling, and seamless media playback integration.

## Key Features

### 🎯 Batch Management
- **Priority-based ordering**: CRITICAL, HIGH, NORMAL, LOW priorities
- **Concurrent download limits**: Control how many torrents download simultaneously
- **Batch reordering**: Dynamically change batch positions in the queue
- **Scheduling**: Time-based and day-of-week scheduling for batches
- **Bandwidth limits**: Per-batch bandwidth control

### 🎮 Media Player Integration
- **Auto-play**: Automatically start playback when threshold is reached
- **Playlist management**: Organize torrents into playable sequences
- **Media controls**: Play, pause, stop, next, previous, seek
- **Volume control**: Per-batch volume settings
- **Progress tracking**: Real-time playback progress monitoring

### 📊 Monitoring & Control
- **Real-time status**: Live batch and torrent progress
- **Batch statistics**: Comprehensive download metrics
- **Media player state**: Current playback status and controls
- **Queue management**: Dynamic batch queue processing

## Architecture

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│  TrikeAriaClient│    │ TorrentBatch     │    │ TorrentMedia    │
│                 │    │ Controller       │    │ Player          │
│ • createBatch   │───▶│ • Priority Queue │───▶│ • Playlist      │
│ • addTorrent    │    │ • Concurrent     │    │ • Auto-play     │
│ • startBatch    │    │   Management     │    │ • Controls      │
│ • mediaControl  │    │ • Scheduling     │    │ • State         │
└─────────────────┘    └──────────────────┘    └─────────────────┘
                                │
                                ▼
                       ┌──────────────────┐
                       │ TorrentRpcServer │
                       │                  │
                       │ • Batch RPC      │
                       │ • Media RPC      │
                       │ • Status RPC     │
                       └──────────────────┘
```

## Usage Examples

### Basic Batch Operations

```kotlin
// Create a high-priority batch with media integration
val batchId = ariaClient.createBatch(
    name = "Movie Collection",
    maxConcurrent = 2,
    priority = "HIGH",
    mediaPlayerIntegration = true
)

// Add torrents to batch
val torrentId1 = ariaClient.addTorrentToBatch(batchId, "magnet:?xt=urn:btih:...")
val torrentId2 = ariaClient.addTorrentToBatch(batchId, "magnet:?xt=urn:btih:...")

// Start the batch
ariaClient.startBatch(batchId)
```

### Media Player Control

```kotlin
// Set up playlist
val torrentIds = listOf("hash1", "hash2", "hash3")
ariaClient.setMediaPlaylist(batchId, torrentIds)

// Control playback
ariaClient.playMedia(batchId)
ariaClient.pauseMedia(batchId)
ariaClient.nextMedia(batchId)
ariaClient.seekMedia(batchId, 0.5) // Seek to 50%
ariaClient.setMediaVolume(batchId, 0.8) // 80% volume
```

### Priority Management

```kotlin
// Change batch priority
ariaClient.setBatchPriority(batchId, "CRITICAL")

// Reorder batches in queue
ariaClient.reorderBatch(batchId, 0) // Move to front
ariaClient.reorderBatch(batchId, 2) // Move to position 2
```

### Real-time Monitoring

```kotlin
// Get batch status
val status = ariaClient.getBatchStatus(batchId)
println("Progress: ${status["totalProgress"]}")
println("Speed: ${status["downloadSpeed"]} MB/s")

// Monitor batch over time
val statuses = ariaClient.monitorBatch(batchId, intervalMs = 1000, maxDuration = 60000)
statuses.forEach { status ->
    println("${status["totalProgress"]}% complete")
}
```

### Convenience Methods

```kotlin
// Quick batch download
val batchId = ariaClient.batchDownload(
    torrentUrls = listOf("magnet:...", "magnet:..."),
    batchName = "Quick Downloads"
)

// Create and start batch in one call
val batchId = ariaClient.createAndStartBatch(
    name = "Auto Batch",
    torrentUrls = listOf("magnet:...", "magnet:..."),
    maxConcurrent = 3,
    priority = "NORMAL",
    mediaPlayerIntegration = true
)
```

## RPC Methods

### Batch Management

| Method | Parameters | Description |
|--------|------------|-------------|
| `createBatch` | name, maxConcurrent, priority, mediaPlayerIntegration | Create new batch |
| `addTorrentToBatch` | batchId, torrentUrl | Add torrent to batch |
| `startBatch` | batchId | Start batch downloads |
| `pauseBatch` | batchId | Pause batch downloads |
| `resumeBatch` | batchId | Resume batch downloads |
| `removeBatch` | batchId | Remove batch |
| `getBatchStatus` | batchId | Get batch status |
| `getAllBatchStatuses` | - | Get all batch statuses |
| `reorderBatch` | batchId, newPosition | Reorder batch in queue |
| `setBatchPriority` | batchId, priority | Set batch priority |
| `processBatchQueue` | - | Process batch queue |

### Media Player Control

| Method | Parameters | Description |
|--------|------------|-------------|
| `getMediaPlayerState` | batchId | Get media player state |
| `controlMediaPlayer` | batchId, action, params | Control media player |
| `playMedia` | batchId | Start playback |
| `pauseMedia` | batchId | Pause playback |
| `stopMedia` | batchId | Stop playback |
| `nextMedia` | batchId | Next track |
| `previousMedia` | batchId | Previous track |
| `seekMedia` | batchId, position | Seek to position |
| `setMediaVolume` | batchId, volume | Set volume |
| `setMediaPlaylist` | batchId, torrentIds | Set playlist |

## Configuration Options

### BatchConfig

```kotlin
data class BatchConfig(
    val name: String,                    // Batch name
    val maxConcurrent: Int = 3,          // Max concurrent downloads
    val priority: BatchPriority = NORMAL, // Priority level
    val mediaPlayerIntegration: Boolean = false, // Enable media controls
    val autoPlayThreshold: Double = 0.1, // Auto-play at 10% downloaded
    val bandwidthLimit: Long? = null,    // Bandwidth limit (bytes/sec)
    val scheduleConfig: ScheduleConfig? = null // Scheduling options
)
```

### ScheduleConfig

```kotlin
data class ScheduleConfig(
    val startTime: String? = null,       // HH:mm format
    val endTime: String? = null,         // HH:mm format
    val daysOfWeek: List<Int> = emptyList(), // 0=Sunday, 1=Monday, etc.
    val timezone: String = "UTC"         // Timezone
)
```

## Priority Levels

- **CRITICAL**: Highest priority, processed first
- **HIGH**: High priority, processed after critical
- **NORMAL**: Standard priority, default
- **LOW**: Low priority, processed last

## Media Player Actions

- **Play**: Start playback
- **Pause**: Pause playback
- **Stop**: Stop playback and reset
- **Next**: Play next track in playlist
- **Previous**: Play previous track in playlist
- **Seek**: Seek to specific position (0.0-1.0)
- **SetVolume**: Set volume level (0.0-1.0)
- **SetPlaylist**: Set new playlist order

## Error Handling

```kotlin
try {
    val batchId = ariaClient.createBatch("Test Batch")
    ariaClient.startBatch(batchId)
} catch (e: RuntimeException) {
    println("Batch operation failed: ${e.message}")
}
```

## Performance Considerations

- **Concurrent limits**: Set appropriate maxConcurrent values
- **Priority management**: Use priorities to optimize download order
- **Bandwidth limits**: Configure bandwidth limits for network management
- **Batch size**: Keep batches manageable (10-50 torrents recommended)
- **Monitoring frequency**: Adjust monitoring intervals based on needs

## Integration with Existing Systems

### aria2c Compatibility

The batch control system is designed to be compatible with aria2c's batch operations:

```bash
# aria2c equivalent
aria2c --input-file=downloads.txt --max-concurrent-downloads=3

# TrikeAria equivalent
val batchId = ariaClient.createBatch("Downloads", maxConcurrent = 3)
downloads.forEach { url -> ariaClient.addTorrentToBatch(batchId, url) }
ariaClient.startBatch(batchId)
```

### Media Player Integration

```kotlin
// Integrate with external media players
val mediaState = ariaClient.getMediaPlayerState(batchId)
if (mediaState["isPlaying"] == true) {
    // Control external player (VLC, mpv, etc.)
    externalPlayer.control(mediaState["currentTorrentId"])
}
```

## Best Practices

1. **Batch Organization**: Group related content in batches
2. **Priority Planning**: Use priorities to manage download importance
3. **Media Integration**: Enable media integration for video/audio content
4. **Monitoring**: Use real-time monitoring for large batches
5. **Cleanup**: Remove completed batches to free resources
6. **Scheduling**: Use scheduling for off-peak downloads
7. **Bandwidth Management**: Set appropriate bandwidth limits

## Troubleshooting

### Common Issues

1. **Batch not starting**: Check priority and queue position
2. **Media not playing**: Verify media player integration is enabled
3. **Slow downloads**: Adjust concurrent limits and priorities
4. **Queue stuck**: Use processBatchQueue() to force processing

### Debug Commands

```kotlin
// Get detailed batch status
val status = ariaClient.getBatchStatus(batchId)
println("Status: $status")

// Get all batch statuses
val allBatches = ariaClient.getAllBatchStatuses()
allBatches.forEach { println("Batch: $it") }

// Force queue processing
ariaClient.processBatchQueue()
```

## Future Enhancements

- **Advanced scheduling**: Cron-like scheduling expressions
- **Bandwidth shaping**: Per-batch bandwidth allocation
- **Media transcoding**: Automatic format conversion
- **Playlist import**: Import playlists from external sources
- **Cloud integration**: Sync batches across devices
- **Analytics**: Download and playback analytics
- **Notifications**: Batch completion and error notifications 
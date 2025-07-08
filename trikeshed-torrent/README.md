# TrikeDownloader - aria2c + curl Feature Minimum

A unified downloader that combines the best features of aria2c and curl into two command-line personas with shared backend infrastructure.

## 🎯 Feature Minimum

### TrikeAria (aria2c-like)
- **Torrent downloads**: Magnet links, .torrent files
- **Multi-protocol**: HTTP, HTTPS, FTP support
- **Concurrent downloads**: Multiple simultaneous downloads
- **Connection limits**: Configurable per-server connections
- **Speed limits**: Upload/download rate limiting
- **Session management**: Pause/resume/cancel downloads
- **Progress reporting**: Real-time download statistics

### TrikeCurl (curl-like)
- **HTTP/HTTPS downloads**: Single-file downloads with resume
- **Custom headers**: Authentication, User-Agent, etc.
- **Request methods**: GET, POST, PUT, etc.
- **Verbose output**: Detailed request/response information
- **Redirect following**: Automatic redirect handling
- **Timeout control**: Connection and operation timeouts
- **Output control**: File output, silent mode, progress bars

## 🚀 Quick Start

### Installation
```bash
# Build the project
./gradlew :trikeshed-torrent:build

# Create symlinks (optional)
ln -s trikeshed-torrent/build/libs/trikeshed-torrent.jar /usr/local/bin/trike-aria
ln -s trikeshed-torrent/build/libs/trikeshed-torrent.jar /usr/local/bin/trike-curl
```

### Usage Examples

#### TrikeAria (aria2c-like)
```bash
# Download a torrent
trike-aria magnet:?xt=urn:btih:...

# Download multiple files
trike-aria https://example.com/file1.zip https://example.com/file2.zip

# Set download directory and limits
trike-aria -d /downloads -x 16 -j 5 file.torrent

# List active downloads
trike-aria --list

# Pause all downloads
trike-aria --pause-all
```

#### TrikeCurl (curl-like)
```bash
# Simple download
trike-curl https://example.com/file.zip

# Download with custom headers
trike-curl -H "Authorization: Bearer token" https://api.example.com/data

# Resume download
trike-curl -C - https://example.com/large-file.zip

# Verbose output
trike-curl -v -L https://example.com/redirect

# Custom output file
trike-curl -o download.zip https://example.com/file.zip
```

## 🏗️ Architecture

### Shared Backend (TrikeDownloader)
- **Unified download queue**: Handles both HTTP and torrent downloads
- **Progress tracking**: Real-time statistics and monitoring
- **Resource management**: Connection pooling and limits
- **Error handling**: Graceful failure recovery
- **Session persistence**: Resume downloads across restarts

### Command-Line Personas
- **TrikeAria**: Focused on batch downloads and torrents
- **TrikeCurl**: Focused on single HTTP requests and debugging

## 📊 Features Comparison

| Feature | aria2c | curl | TrikeAria | TrikeCurl |
|---------|--------|------|-----------|-----------|
| Torrent downloads | ✅ | ❌ | ✅ | ❌ |
| HTTP downloads | ✅ | ✅ | ✅ | ✅ |
| Concurrent downloads | ✅ | ❌ | ✅ | ❌ |
| Resume support | ✅ | ✅ | ✅ | ✅ |
| Custom headers | ❌ | ✅ | ✅ | ✅ |
| Verbose output | ❌ | ✅ | ✅ | ✅ |
| Speed limits | ✅ | ❌ | ✅ | ❌ |
| Session management | ✅ | ❌ | ✅ | ❌ |

## 🔧 Configuration

### Environment Variables
```bash
export TRIKE_DOWNLOAD_DIR="./downloads"
export TRIKE_TEMP_DIR="./temp"
export TRIKE_MAX_CONCURRENT=5
```

### Configuration File
```json
{
  "downloadDir": "./downloads",
  "tempDir": "./temp",
  "maxConcurrentDownloads": 5,
  "defaultHeaders": {
    "User-Agent": "TrikeDownloader/1.0"
  }
}
```

## 🧪 Testing

### Run Demo
```bash
# Run the combined demo
kotlin demo-downloaders.kts

# Test individual personas
kotlin -cp build/libs/trikeshed-torrent.jar borg.trikeshed.torrent.TrikeAria --help
kotlin -cp build/libs/trikeshed-torrent.jar borg.trikeshed.torrent.TrikeCurl --help
```

### Unit Tests
```bash
./gradlew :trikeshed-torrent:test
```

## 🚧 Current Status

### ✅ Implemented
- Basic download infrastructure
- Command-line argument parsing
- Progress tracking and reporting
- Download management (pause/resume/cancel)
- Simulated HTTP and torrent downloads

### 🔄 In Progress
- Real HTTP client implementation
- BitTorrent protocol implementation
- File I/O and resume functionality
- Network error handling

### 📋 Planned
- DHT integration for torrent discovery
- Real-time peer management
- Advanced HTTP features (compression, caching)
- Configuration file support
- Plugin system for additional protocols

## 🤝 Contributing

This is a feature minimum implementation. Contributions are welcome for:

1. **Real protocol implementations** (HTTP, BitTorrent)
2. **Network layer improvements** (connection pooling, retry logic)
3. **Additional protocols** (FTP, SFTP, etc.)
4. **Performance optimizations** (async I/O, memory management)
5. **Testing and documentation**

## 📄 License

Part of the TrikeShed project. See main project license for details. 
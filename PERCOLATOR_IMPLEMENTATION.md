# Content Percolator Implementation

## Overview

The Content Percolator is a distributed extraction network where volunteers contribute processing power to extract and analyze content from large archives using minimal bandwidth.

## Architecture

```
┌─────────────────────┐
│   Coordinator       │
│   Server (8888)     │
│                     │
│ - Work Distribution │
│ - Node Management   │
│ - Result Collection │
└──────────┬──────────┘
           │
      ┌────┴────┬────────┬────────┐
      │         │        │        │
┌─────▼───┐ ┌──▼───┐ ┌──▼───┐ ┌──▼───┐
│ Node 1  │ │Node 2│ │Node 3│ │Node N│
│         │ │      │ │      │ │      │
│- Extract│ │- NLP │ │- OCR │ │- ...  │
└─────────┘ └──────┘ └──────┘ └──────┘
```

## Quick Start

1. **Run the demo**:
   ```bash
   ./run-percolator-demo.sh
   ```

2. **View dashboard**:
   Open http://localhost:8888 in your browser

3. **Join as volunteer**:
   ```bash
   java -jar percolator-node.jar --coordinator=http://localhost:8888
   ```

## Implementation Status

### ✅ Completed
- Coordinator server with REST API
- Volunteer node daemon
- HTTP Range-based extraction
- Patrick Devine archive bootstrap
- Work distribution protocol
- Node heartbeat system
- Web dashboard

### 🚧 In Progress
- Actual ZIP extraction integration
- NLP tagging pipeline
- Memvid storage integration
- Result aggregation

### 📋 TODO
- IPFS result storage
- Browser-based volunteer nodes
- Reputation system
- Work prioritization
- Distributed consensus

## API Endpoints

- `POST /api/v1/node/register` - Register new node
- `POST /api/v1/work/claim` - Claim work unit
- `POST /api/v1/work/progress/{id}` - Report progress
- `POST /api/v1/work/complete/{id}` - Submit results
- `POST /api/v1/node/heartbeat` - Node status update
- `GET /stats` - Network statistics

## Work Unit Example

```json
{
  "id": "patrick_devine_0720",
  "archiveUrl": "https://archive.org/download/patrickdevinefiles/Patrick%20Devine%20files.zip",
  "entries": [{
    "path": "Patrick Devine files/01-Transcripts/patrick_0720.txt",
    "offset": 3050270003,
    "compressedSize": 9612,
    "uncompressedSize": 25389,
    "method": 8
  }],
  "priority": 1.0
}
```

## Processing Pipeline

1. **Extract**: Use HTTP Range requests to fetch only needed bytes
2. **Decompress**: zlib decompress the content
3. **Tag**: Stanford NLP tagging
4. **Analyze**: Extract entities, calculate complexity
5. **Store**: Save to Memvid with QR codes
6. **Submit**: Send results back to coordinator

## Bandwidth Efficiency

For patrick_0720.txt:
- Full archive: 3 GB
- Range requests: 1.6 MB
- **Savings: 99.94%**

## Contributing

The percolator network relies on volunteers. Each node:
- Claims work autonomously
- Processes with minimal bandwidth
- Contributes to collective knowledge
- Earns reputation points

## Security

- Nodes verified by coordinator
- Work units cryptographically signed
- Results validated before acceptance
- Malicious nodes banned automatically
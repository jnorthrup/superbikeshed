# Nexus Service Loop Setup Guide

## Quick Start

1. **Enable IntelliJ REST API** (if not already done):
   ```bash
   # macOS
   echo "-Dide.rest.api=true" >> ~/Library/Application\ Support/JetBrains/IntelliJIdea2024.3/idea.vmoptions
   echo "-Dide.rest.api.port=63342" >> ~/Library/Application\ Support/JetBrains/IntelliJIdea2024.3/idea.vmoptions
   echo "-Dide.rest.api.cors.enabled=true" >> ~/Library/Application\ Support/JetBrains/IntelliJIdea2024.3/idea.vmoptions
   
   # Restart IntelliJ
   ```

2. **Get Nemotron API Key** (optional but recommended):
   - Visit: https://build.nvidia.com
   - Sign up for free account
   - Get API key
   - Set environment variable:
   ```bash
   export NVIDIA_API_KEY="your-api-key-here"
   ```

3. **Start the Service**:
   ```bash
   cd nexus
   ./start-nexus-service.sh
   ```

## What the Service Loop Does

The Nexus Service Loop provides an integrated development environment that:

### 🤖 **Autonomous Agent System**
- Continuously monitors your development environment
- Learns from your coding patterns and preferences
- Generates autonomous tasks based on project state
- Maintains a knowledge base of successful patterns

### 🔧 **IntelliJ Integration**
- Connects to IntelliJ's REST API for real-time code analysis
- Performs automated refactoring operations
- Runs code inspections and reports issues
- Provides intelligent code completion suggestions

### 🧠 **Nemotron LLM Integration**
- Uses NVIDIA's Nemotron model for intelligent code suggestions
- Analyzes code patterns and provides improvements
- Generates documentation and explanations
- Assists with debugging and problem-solving

### 📊 **Continuous Monitoring**
- Tracks project health and code quality
- Monitors for potential issues and improvements
- Provides real-time feedback and suggestions
- Maintains learning patterns for future optimization

## Service Components

### Core Subsystems

1. **Learning Subsystem** (`autonomousLearning`)
   - Processes observations from all operations
   - Extracts patterns and success rates
   - Builds knowledge base for future decisions

2. **Task Execution Subsystem** (`autonomousTaskExecution`)
   - Executes development tasks autonomously
   - Handles refactoring, inspection, and suggestion tasks
   - Reports outcomes for learning

3. **Environment Monitoring** (`environmentMonitoring`)
   - Monitors IntelliJ connection status
   - Checks Nemotron LLM availability
   - Tracks system capabilities and constraints

4. **LLM Integration** (`llmIntegration`)
   - Manages Nemotron API interactions
   - Processes code analysis requests
   - Generates intelligent suggestions

5. **IntelliJ Integration** (`intellijIntegration`)
   - Manages IntelliJ API connections
   - Executes refactoring operations
   - Runs code inspections and completions

### Task Types

- **`refactor`**: Automated code refactoring using IntelliJ
- **`inspect`**: Code quality analysis and issue detection
- **`suggest`**: LLM-powered code improvement suggestions

## Configuration

### Environment Variables

```bash
# Required for LLM features
export NVIDIA_API_KEY="your-nvidia-api-key"
# OR
export HF_TOKEN="your-huggingface-token"

# Optional configuration
export PROJECT_PATH="/path/to/your/project"
export NEXUS_SERVICE_MODE=true
```

### IntelliJ Setup

1. **Enable REST API**:
   - Help → Edit Custom VM Options
   - Add these lines:
   ```
   -Dide.rest.api=true
   -Dide.rest.api.port=63342
   -Dide.rest.api.cors.enabled=true
   ```
   - Restart IntelliJ

2. **Test Connection**:
   ```bash
   curl http://localhost:63342/api/status
   ```

### Project Structure

The service expects this project structure:
```
v2superbikeshed/
├── nexus/
│   ├── nexus-service-loop.kts
│   ├── start-nexus-service.sh
│   └── SERVICE_LOOP_SETUP.md
└── [your project files]
```

## Usage Examples

### Basic Service Loop
```bash
# Start the service
cd nexus
./start-nexus-service.sh
```

### Custom Configuration
```bash
# Set custom project path
export PROJECT_PATH="/Users/jim/work/my-custom-project"
./start-nexus-service.sh
```

### Manual Execution
```bash
# Run directly with kotlin
kotlin nexus-service-loop.kts
```

## Monitoring and Control

### Service Status
The service provides real-time status information:
- Connection status for IntelliJ and Nemotron
- Number of observations and learned patterns
- Current task execution status

### Logging
The service outputs structured logs with timestamps:
```
[14:30:15] 🔄 Service loop iteration
[14:30:16] 🎯 Executing task: Autonomous refactor task
[14:30:17] ✅ Task completed: Refactoring successful: 2 files changed
```

### Stopping the Service
- Press `Ctrl+C` to gracefully stop the service
- The service will complete current operations before shutting down

## Troubleshooting

### IntelliJ Connection Issues
```bash
# Check if IntelliJ API is running
curl http://localhost:63342/api/status

# If not responding, restart IntelliJ with REST API enabled
```

### Nemotron API Issues
```bash
# Check API key
echo $NVIDIA_API_KEY

# Test API directly
curl -H "Authorization: Bearer $NVIDIA_API_KEY" \
     -H "Content-Type: application/json" \
     -d '{"model":"nvidia/llama-3.1-nemotron-70b-instruct","messages":[{"role":"user","content":"Hello"}]}' \
     https://integrate.api.nvidia.com/v1/chat/completions
```

### Service Loop Issues
```bash
# Check Kotlin installation
kotlin --version

# Check dependencies
kotlin -cp "path/to/dependencies" nexus-service-loop.kts
```

## Advanced Features

### Custom Task Generation
The service can be extended to generate custom tasks based on:
- Code analysis results
- Project metrics
- User preferences
- Historical patterns

### Integration with Other Tools
The service architecture supports integration with:
- Git operations
- CI/CD pipelines
- Code review systems
- Documentation generators

### Learning Customization
The learning system can be customized for:
- Specific programming languages
- Framework preferences
- Coding style guidelines
- Performance requirements

## Next Steps

1. **Run the service** and observe its behavior
2. **Configure your environment** with proper API keys
3. **Customize task generation** for your specific needs
4. **Extend the system** with additional integrations
5. **Monitor and optimize** based on usage patterns

The Nexus Service Loop provides a foundation for autonomous development assistance that learns and improves over time. 
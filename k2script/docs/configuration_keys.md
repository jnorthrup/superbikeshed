# K2Script Configuration Keys & Use Cases

## Overview

K2Script provides a comprehensive configuration system through environment variables and system properties. This document covers all configuration keys and their use cases, with special focus on installation scenarios.

## Configuration Hierarchy

1. **Environment Variables** (highest priority)
2. **System Properties** (fallback)
3. **Default Values** (lowest priority)

## Core Configuration Sections

### 1. K2Script Core (`K2SCRIPT_*`)

#### Installation & Paths
```bash
# Installation prefix (e.g., ~/.local, /usr/local)
export K2SCRIPT_INSTALL_PREFIX="/usr/local"

# Cache directory for dependencies and compiled scripts
export K2SCRIPT_CACHE_DIR="$HOME/.k2script/cache"

# Configuration directory for user settings
export K2SCRIPT_CONFIG_DIR="$HOME/.k2script/config"
```

#### Execution Control
```bash
# Log level: DEBUG, INFO, WARN, ERROR
export K2SCRIPT_LOG_LEVEL="INFO"

# Verbose output mode
export K2SCRIPT_VERBOSE="true"

# Sandbox mode for script execution
export K2SCRIPT_SANDBOX="true"

# Allowed paths for sandboxed execution (colon-separated)
export K2SCRIPT_ALLOWED_PATHS="/home/user/projects:/tmp/scripts"

# Maximum memory for script execution
export K2SCRIPT_MAX_MEMORY="2g"

# Initial memory for script execution
export K2SCRIPT_INITIAL_MEMORY="512m"

# Execution timeout in seconds
export K2SCRIPT_EXECUTION_TIMEOUT="300"

# Working directory for script execution
export K2SCRIPT_WORKING_DIR="/home/user/workspace"

# Auto-cleanup temporary files
export K2SCRIPT_AUTO_CLEANUP="true"
```

### 2. AI/LLM Integration (`K2SCRIPT_AI_*`)

```bash
# Default AI provider (openai, anthropic, nvidia, etc.)
export K2SCRIPT_AI_PROVIDER="openai"

# Default model for AI operations
export K2SCRIPT_AI_MODEL="gpt-4"

# API key for the default provider
export K2SCRIPT_AI_API_KEY="sk-..."

# Base URL for API calls (for self-hosted instances)
export K2SCRIPT_AI_BASE_URL="https://api.openai.com/v1"

# Request timeout in seconds
export K2SCRIPT_AI_TIMEOUT="60"

# Maximum tokens for AI responses
export K2SCRIPT_AI_MAX_TOKENS="4096"

# Temperature for AI responses (0.0-2.0)
export K2SCRIPT_AI_TEMPERATURE="0.7"

# Enable AI features
export K2SCRIPT_AI_ENABLED="true"
```

### 3. Installation Management (`K2SCRIPT_INSTALL_*`)

```bash
# Default installation prefix for user installations
export K2SCRIPT_INSTALL_DEFAULT_PREFIX="$HOME/.local"

# System-wide installation prefix
export K2SCRIPT_INSTALL_SYSTEM_PREFIX="/usr/local"

# Create uninstall scripts
export K2SCRIPT_INSTALL_CREATE_UNINSTALL="true"

# Set executable permissions
export K2SCRIPT_INSTALL_SET_EXECUTABLE="true"

# Backup existing installations
export K2SCRIPT_INSTALL_BACKUP_EXISTING="true"

# Installation mode: user, system, development
export K2SCRIPT_INSTALL_MODE="user"
```

### 4. Script Execution (`K2SCRIPT_SCRIPT_*`)

```bash
# Default script template
export K2SCRIPT_SCRIPT_DEFAULT_TEMPLATE="basic"

# Auto-resolve dependencies
export K2SCRIPT_SCRIPT_AUTO_RESOLVE="true"

# Cache resolved dependencies
export K2SCRIPT_SCRIPT_CACHE_DEPS="true"

# Validate script syntax before execution
export K2SCRIPT_SCRIPT_VALIDATE_SYNTAX="true"

# Allow network access for dependency resolution
export K2SCRIPT_SCRIPT_ALLOW_NETWORK="true"

# Maximum dependency resolution time in seconds
export K2SCRIPT_SCRIPT_DEPS_TIMEOUT="120"
```

### 5. Security & Sandbox (`K2SCRIPT_SECURITY_*`)

```bash
# Isolation level: process, container, vm
export K2SCRIPT_SECURITY_ISOLATION_LEVEL="process"

# Enable file system restrictions
export K2SCRIPT_SECURITY_RESTRICT_FS="true"

# Enable network restrictions
export K2SCRIPT_SECURITY_RESTRICT_NETWORK="false"

# Enable process restrictions
export K2SCRIPT_SECURITY_RESTRICT_PROCESS="true"

# Maximum file size for script execution (bytes)
export K2SCRIPT_SECURITY_MAX_FILE_SIZE="10000000"

# Allowed file extensions for execution
export K2SCRIPT_SECURITY_ALLOWED_EXTENSIONS="kts,kt"
```

### 6. Performance (`K2SCRIPT_PERFORMANCE_*`)

```bash
# Enable parallel dependency resolution
export K2SCRIPT_PERFORMANCE_PARALLEL_DEPS="true"

# Maximum parallel threads for dependency resolution
export K2SCRIPT_PERFORMANCE_MAX_THREADS="4"

# Enable incremental compilation
export K2SCRIPT_PERFORMANCE_INCREMENTAL="true"

# Enable bytecode caching
export K2SCRIPT_PERFORMANCE_BYTECODE_CACHE="true"

# Cache TTL in seconds
export K2SCRIPT_PERFORMANCE_CACHE_TTL="3600"
```

## Installation Use Cases

### 1. User Installation (`k2script --install ~/.local`)

```bash
# Configuration for user installation
export K2SCRIPT_INSTALL_PREFIX="$HOME/.local"
export K2SCRIPT_INSTALL_MODE="user"
export K2SCRIPT_INSTALL_CREATE_UNINSTALL="true"
export K2SCRIPT_INSTALL_SET_EXECUTABLE="true"
export K2SCRIPT_CACHE_DIR="$HOME/.k2script/cache"
export K2SCRIPT_CONFIG_DIR="$HOME/.k2script/config"

# Creates:
# ~/.local/bin/k2script
# ~/.local/lib/k2script/k2script.jar
# ~/.local/share/k2script/
```

### 2. System Installation (`k2script --install /usr/local`)

```bash
# Configuration for system installation
export K2SCRIPT_INSTALL_PREFIX="/usr/local"
export K2SCRIPT_INSTALL_MODE="system"
export K2SCRIPT_INSTALL_CREATE_UNINSTALL="true"
export K2SCRIPT_INSTALL_BACKUP_EXISTING="true"
export K2SCRIPT_CACHE_DIR="/var/cache/k2script"
export K2SCRIPT_CONFIG_DIR="/etc/k2script"

# Creates:
# /usr/local/bin/k2script
# /usr/local/lib/k2script/k2script.jar
# /usr/local/share/k2script/
```

### 3. Development Installation (`k2script --install ./build/install`)

```bash
# Configuration for development installation
export K2SCRIPT_INSTALL_PREFIX="./build/install"
export K2SCRIPT_INSTALL_MODE="development"
export K2SCRIPT_INSTALL_CREATE_UNINSTALL="false"
export K2SCRIPT_VERBOSE="true"
export K2SCRIPT_LOG_LEVEL="DEBUG"
export K2SCRIPT_CACHE_DIR="./build/cache"
export K2SCRIPT_CONFIG_DIR="./build/config"

# Creates:
# ./build/install/bin/k2script
# ./build/install/lib/k2script/k2script.jar
# ./build/install/share/k2script/
```

### 4. Script Installation (`k2script --install $PREFIX FookScript.kts`)

```bash
# Configuration for script installation
export K2SCRIPT_INSTALL_PREFIX="$PREFIX"
export K2SCRIPT_SCRIPT_AUTO_RESOLVE="true"
export K2SCRIPT_SCRIPT_CACHE_DEPS="true"
export K2SCRIPT_SCRIPT_VALIDATE_SYNTAX="true"
export K2SCRIPT_SECURITY_ISOLATION_LEVEL="process"
export K2SCRIPT_SECURITY_RESTRICT_FS="true"

# Creates:
# $PREFIX/bin/fookscript
# $PREFIX/lib/fookscript/ (dependencies)
# $PREFIX/share/fookscript/FookScript.kts
```

## Environment Setup Scripts

### Generate Environment Script

```bash
# Generate environment setup script
k2script --env-generate > setup-k2script.sh
chmod +x setup-k2script.sh
source setup-k2script.sh
```

### Example Environment Script

```bash
#!/bin/bash
# K2Script Environment Configuration
# Generated on: 2025-01-27T10:30:00

export K2SCRIPT_INSTALL_PREFIX="$HOME/.local"
export K2SCRIPT_CACHE_DIR="$HOME/.k2script/cache"
export K2SCRIPT_CONFIG_DIR="$HOME/.k2script/config"
export K2SCRIPT_LOG_LEVEL="INFO"
export K2SCRIPT_VERBOSE="false"
export K2SCRIPT_SANDBOX="true"
export K2SCRIPT_MAX_MEMORY="2g"
export K2SCRIPT_INITIAL_MEMORY="512m"
export K2SCRIPT_EXECUTION_TIMEOUT="300"
export K2SCRIPT_WORKING_DIR="."
export K2SCRIPT_AUTO_CLEANUP="true"
export K2SCRIPT_AI_PROVIDER="openai"
export K2SCRIPT_AI_ENABLED="true"
export K2SCRIPT_INSTALL_MODE="user"
export K2SCRIPT_SECURITY_ISOLATION_LEVEL="process"
export K2SCRIPT_PERFORMANCE_PARALLEL_DEPS="true"

# Add k2script to PATH if installed
export PATH="$HOME/.local/bin:$PATH"
```

## Configuration Validation

### Check Configuration

```bash
# Validate configuration and report issues
k2script --env-check
```

### Example Validation Output

```
=== K2Script Configuration Validation ===
✅ Cache directory: /home/user/.k2script/cache
✅ Config directory: /home/user/.k2script/config
✅ AI provider: openai
⚠️  AI API key not configured (AI features disabled)
✅ Security level: process
✅ Performance: parallel deps enabled
========================================
```

## Advanced Use Cases

### 1. Multi-Environment Setup

```bash
# Development environment
export K2SCRIPT_ENV="development"
export K2SCRIPT_VERBOSE="true"
export K2SCRIPT_LOG_LEVEL="DEBUG"
export K2SCRIPT_SANDBOX="false"
export K2SCRIPT_AI_ENABLED="false"

# Production environment
export K2SCRIPT_ENV="production"
export K2SCRIPT_VERBOSE="false"
export K2SCRIPT_LOG_LEVEL="WARN"
export K2SCRIPT_SANDBOX="true"
export K2SCRIPT_SECURITY_ISOLATION_LEVEL="container"
```

### 2. CI/CD Integration

```bash
# CI/CD environment
export K2SCRIPT_INSTALL_PREFIX="/opt/k2script"
export K2SCRIPT_CACHE_DIR="/tmp/k2script-cache"
export K2SCRIPT_CONFIG_DIR="/tmp/k2script-config"
export K2SCRIPT_VERBOSE="false"
export K2SCRIPT_LOG_LEVEL="ERROR"
export K2SCRIPT_SANDBOX="true"
export K2SCRIPT_SECURITY_ISOLATION_LEVEL="process"
export K2SCRIPT_AUTO_CLEANUP="true"
```

### 3. Docker Container Setup

```bash
# Docker environment
export K2SCRIPT_INSTALL_PREFIX="/usr/local"
export K2SCRIPT_CACHE_DIR="/var/cache/k2script"
export K2SCRIPT_CONFIG_DIR="/etc/k2script"
export K2SCRIPT_SANDBOX="true"
export K2SCRIPT_SECURITY_ISOLATION_LEVEL="container"
export K2SCRIPT_SECURITY_RESTRICT_NETWORK="true"
export K2SCRIPT_SECURITY_RESTRICT_FS="true"
export K2SCRIPT_AUTO_CLEANUP="true"
```

## Configuration File Support

### .env File

Create a `.env` file in your project root:

```bash
# .env
K2SCRIPT_INSTALL_PREFIX=$HOME/.local
K2SCRIPT_VERBOSE=true
K2SCRIPT_LOG_LEVEL=DEBUG
K2SCRIPT_AI_PROVIDER=openai
K2SCRIPT_AI_API_KEY=sk-...
K2SCRIPT_SECURITY_ISOLATION_LEVEL=process
```

### System Properties

Set via command line:

```bash
java -Dk2script.install.prefix=/usr/local \
     -Dk2script.verbose=true \
     -Dk2script.ai.provider=openai \
     -jar k2script.jar
```

## Best Practices

1. **Use Environment Variables** for sensitive data (API keys)
2. **Use System Properties** for development overrides
3. **Use .env Files** for project-specific configuration
4. **Validate Configuration** before deployment
5. **Document Configuration** in project README
6. **Use Appropriate Isolation Levels** for security
7. **Enable Caching** for performance
8. **Set Reasonable Timeouts** for resource management

## Troubleshooting

### Common Issues

1. **Permission Denied**: Check executable permissions
2. **Cache Issues**: Clear cache directory
3. **Network Access**: Verify network restrictions
4. **Memory Issues**: Adjust memory settings
5. **Timeout Issues**: Increase timeout values

### Debug Mode

```bash
export K2SCRIPT_VERBOSE="true"
export K2SCRIPT_LOG_LEVEL="DEBUG"
k2script --env-check
```

This configuration system provides comprehensive control over k2script behavior while maintaining security and performance best practices. 
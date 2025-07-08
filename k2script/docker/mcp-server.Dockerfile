# MCP Server Docker Image
# Multi-stage build for optimized container

# Build stage
FROM openjdk:21-jdk-slim AS builder

WORKDIR /app

# Copy gradle files
COPY gradle/ gradle/
COPY gradlew build.gradle.kts settings.gradle.kts ./

# Download dependencies
RUN ./gradlew dependencies --no-daemon

# Copy source code
COPY src/ src/

# Build the application
RUN ./gradlew build --no-daemon

# Runtime stage
FROM openjdk:21-jre-slim

# Install necessary packages
RUN apt-get update && apt-get install -y \
    curl \
    wget \
    && rm -rf /var/lib/apt/lists/*

# Create app user
RUN groupadd -r mcp && useradd -r -g mcp mcp

# Set working directory
WORKDIR /app

# Copy built application from builder stage
COPY --from=builder /app/build/libs/k2script-*.jar app.jar

# Copy MCP server configuration
COPY docker/mcp-server.conf /app/config/

# Create directories for MCP data
RUN mkdir -p /app/data /app/logs /app/cache && \
    chown -R mcp:mcp /app

# Switch to mcp user
USER mcp

# Expose MCP server port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:8080/health || exit 1

# Environment variables
ENV MCP_SERVER_PORT=8080
ENV MCP_LOG_LEVEL=INFO
ENV MCP_CACHE_DIR=/app/cache
ENV MCP_DATA_DIR=/app/data

# Default MCP capabilities
ENV MCP_CAPABILITIES=tools,resources,prompts

# Start MCP server
ENTRYPOINT ["java", "-jar", "app.jar", "--mcp-server"]

# Default command
CMD ["start"] 
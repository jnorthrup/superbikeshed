package k2script.git

import java.io.File
import kotlinx.serialization.Serializable

/**
 * Deployment Recipe Manager
 * 
 * Generates deployment recipes (Docker, Kubernetes, etc.) for feature branches
 * with intelligent detection and customization based on project structure.
 */
class DeploymentRecipeManager {
    
    /**
     * Generate deployment recipes for a feature branch
     */
    suspend fun generateRecipes(repoDir: File, featureName: String): List<DeploymentRecipe> {
        val recipes = mutableListOf<DeploymentRecipe>()
        
        // Detect project type and generate appropriate recipes
        val projectType = detectProjectType(repoDir)
        
        when (projectType) {
            ProjectType.KOTLIN_JVM -> {
                recipes.add(generateDockerRecipe(repoDir, featureName, projectType))
                recipes.add(generateKubernetesRecipe(repoDir, featureName, projectType))
                recipes.add(generateDockerComposeRecipe(repoDir, featureName, projectType))
            }
            ProjectType.NODE_JS -> {
                recipes.add(generateDockerRecipe(repoDir, featureName, projectType))
                recipes.add(generateKubernetesRecipe(repoDir, featureName, projectType))
                recipes.add(generateHelmRecipe(repoDir, featureName, projectType))
            }
            ProjectType.PYTHON -> {
                recipes.add(generateDockerRecipe(repoDir, featureName, projectType))
                recipes.add(generateKubernetesRecipe(repoDir, featureName, projectType))
                recipes.add(generateTerraformRecipe(repoDir, featureName, projectType))
            }
            ProjectType.GO -> {
                recipes.add(generateDockerRecipe(repoDir, featureName, projectType))
                recipes.add(generateKubernetesRecipe(repoDir, featureName, projectType))
                recipes.add(generateDockerComposeRecipe(repoDir, featureName, projectType))
            }
            ProjectType.JAVA -> {
                recipes.add(generateDockerRecipe(repoDir, featureName, projectType))
                recipes.add(generateKubernetesRecipe(repoDir, featureName, projectType))
                recipes.add(generateHelmRecipe(repoDir, featureName, projectType))
            }
            ProjectType.UNKNOWN -> {
                recipes.add(generateGenericDockerRecipe(repoDir, featureName))
            }
        }
        
        return recipes
    }
    
    /**
     * Detect project type based on repository structure
     */
    private fun detectProjectType(repoDir: File): ProjectType {
        return when {
            File(repoDir, "build.gradle.kts").exists() || File(repoDir, "build.gradle").exists() -> ProjectType.KOTLIN_JVM
            File(repoDir, "package.json").exists() -> ProjectType.NODE_JS
            File(repoDir, "requirements.txt").exists() || File(repoDir, "pyproject.toml").exists() -> ProjectType.PYTHON
            File(repoDir, "go.mod").exists() -> ProjectType.GO
            File(repoDir, "pom.xml").exists() -> ProjectType.JAVA
            else -> ProjectType.UNKNOWN
        }
    }
    
    /**
     * Generate Docker recipe
     */
    private fun generateDockerRecipe(repoDir: File, featureName: String, projectType: ProjectType): DeploymentRecipe {
        val dockerfile = when (projectType) {
            ProjectType.KOTLIN_JVM -> generateKotlinDockerfile(repoDir, featureName)
            ProjectType.NODE_JS -> generateNodeDockerfile(repoDir, featureName)
            ProjectType.PYTHON -> generatePythonDockerfile(repoDir, featureName)
            ProjectType.GO -> generateGoDockerfile(repoDir, featureName)
            ProjectType.JAVA -> generateJavaDockerfile(repoDir, featureName)
            ProjectType.UNKNOWN -> generateGenericDockerfile(repoDir, featureName)
        }
        
        return DeploymentRecipe(
            name = "Dockerfile",
            type = RecipeType.DOCKER,
            content = dockerfile,
            description = "Docker containerization for $featureName"
        )
    }
    
    /**
     * Generate Kubernetes recipe
     */
    private fun generateKubernetesRecipe(repoDir: File, featureName: String, projectType: ProjectType): DeploymentRecipe {
        val k8sManifest = generateK8sManifest(repoDir, featureName, projectType)
        
        return DeploymentRecipe(
            name = "k8s-deployment.yaml",
            type = RecipeType.KUBERNETES,
            content = k8sManifest,
            description = "Kubernetes deployment for $featureName"
        )
    }
    
    /**
     * Generate Docker Compose recipe
     */
    private fun generateDockerComposeRecipe(repoDir: File, featureName: String, projectType: ProjectType): DeploymentRecipe {
        val composeFile = generateDockerCompose(repoDir, featureName, projectType)
        
        return DeploymentRecipe(
            name = "docker-compose.yml",
            type = RecipeType.DOCKER_COMPOSE,
            content = composeFile,
            description = "Docker Compose orchestration for $featureName"
        )
    }
    
    /**
     * Generate Helm recipe
     */
    private fun generateHelmRecipe(repoDir: File, featureName: String, projectType: ProjectType): DeploymentRecipe {
        val helmChart = generateHelmChart(repoDir, featureName, projectType)
        
        return DeploymentRecipe(
            name = "helm-chart",
            type = RecipeType.HELM,
            content = helmChart,
            description = "Helm chart for $featureName"
        )
    }
    
    /**
     * Generate Terraform recipe
     */
    private fun generateTerraformRecipe(repoDir: File, featureName: String, projectType: ProjectType): DeploymentRecipe {
        val terraformConfig = generateTerraformConfig(repoDir, featureName, projectType)
        
        return DeploymentRecipe(
            name = "terraform",
            type = RecipeType.TERRAFORM,
            content = terraformConfig,
            description = "Terraform infrastructure for $featureName"
        )
    }
    
    // Dockerfile generators
    
    private fun generateKotlinDockerfile(repoDir: File, featureName: String): String {
        return """
            # Multi-stage build for Kotlin/JVM application
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
            RUN apt-get update && apt-get install -y \\
                curl \\
                && rm -rf /var/lib/apt/lists/*
            
            # Create app user
            RUN groupadd -r app && useradd -r -g app app
            
            # Set working directory
            WORKDIR /app
            
            # Copy built application from builder stage
            COPY --from=builder /app/build/libs/*.jar app.jar
            
            # Switch to app user
            USER app
            
            # Expose port
            EXPOSE 8080
            
            # Health check
            HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \\
                CMD curl -f http://localhost:8080/health || exit 1
            
            # Start application
            ENTRYPOINT ["java", "-jar", "app.jar"]
            
            # Default command
            CMD ["--feature-branch", "$featureName"]
        """.trimIndent()
    }
    
    private fun generateNodeDockerfile(repoDir: File, featureName: String): String {
        return """
            # Multi-stage build for Node.js application
            FROM node:18-alpine AS builder
            
            WORKDIR /app
            
            # Copy package files
            COPY package*.json ./
            
            # Install dependencies
            RUN npm ci --only=production
            
            # Copy source code
            COPY . .
            
            # Build application
            RUN npm run build
            
            # Runtime stage
            FROM node:18-alpine
            
            # Create app user
            RUN addgroup -g 1001 -S nodejs && \\
                adduser -S nodejs -u 1001
            
            WORKDIR /app
            
            # Copy built application from builder stage
            COPY --from=builder --chown=nodejs:nodejs /app/dist ./dist
            COPY --from=builder --chown=nodejs:nodejs /app/node_modules ./node_modules
            COPY --from=builder --chown=nodejs:nodejs /app/package*.json ./
            
            # Switch to app user
            USER nodejs
            
            # Expose port
            EXPOSE 3000
            
            # Health check
            HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \\
                CMD curl -f http://localhost:3000/health || exit 1
            
            # Start application
            CMD ["npm", "start", "--", "--feature-branch", "$featureName"]
        """.trimIndent()
    }
    
    private fun generatePythonDockerfile(repoDir: File, featureName: String): String {
        return """
            # Multi-stage build for Python application
            FROM python:3.11-slim AS builder
            
            WORKDIR /app
            
            # Copy requirements
            COPY requirements*.txt ./
            
            # Install dependencies
            RUN pip install --no-cache-dir -r requirements.txt
            
            # Copy source code
            COPY . .
            
            # Runtime stage
            FROM python:3.11-slim
            
            # Install necessary packages
            RUN apt-get update && apt-get install -y \\
                curl \\
                && rm -rf /var/lib/apt/lists/*
            
            # Create app user
            RUN groupadd -r app && useradd -r -g app app
            
            WORKDIR /app
            
            # Copy application from builder stage
            COPY --from=builder /app /app
            COPY --from=builder /usr/local/lib/python3.11/site-packages /usr/local/lib/python3.11/site-packages
            
            # Switch to app user
            USER app
            
            # Expose port
            EXPOSE 8000
            
            # Health check
            HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \\
                CMD curl -f http://localhost:8000/health || exit 1
            
            # Start application
            CMD ["python", "main.py", "--feature-branch", "$featureName"]
        """.trimIndent()
    }
    
    private fun generateGoDockerfile(repoDir: File, featureName: String): String {
        return """
            # Multi-stage build for Go application
            FROM golang:1.21-alpine AS builder
            
            WORKDIR /app
            
            # Copy go mod files
            COPY go.mod go.sum ./
            
            # Download dependencies
            RUN go mod download
            
            # Copy source code
            COPY . .
            
            # Build application
            RUN CGO_ENABLED=0 GOOS=linux go build -a -installsuffix cgo -o main .
            
            # Runtime stage
            FROM alpine:latest
            
            # Install necessary packages
            RUN apk --no-cache add ca-certificates curl
            
            # Create app user
            RUN addgroup -g 1001 app && \\
                adduser -D -s /bin/sh -u 1001 -G app app
            
            WORKDIR /root/
            
            # Copy binary from builder stage
            COPY --from=builder /app/main .
            
            # Switch to app user
            USER app
            
            # Expose port
            EXPOSE 8080
            
            # Health check
            HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \\
                CMD curl -f http://localhost:8080/health || exit 1
            
            # Start application
            CMD ["./main", "--feature-branch", "$featureName"]
        """.trimIndent()
    }
    
    private fun generateJavaDockerfile(repoDir: File, featureName: String): String {
        return """
            # Multi-stage build for Java application
            FROM openjdk:21-jdk-slim AS builder
            
            WORKDIR /app
            
            # Copy Maven files
            COPY pom.xml ./
            
            # Download dependencies
            RUN mvn dependency:go-offline
            
            # Copy source code
            COPY src/ src/
            
            # Build application
            RUN mvn clean package -DskipTests
            
            # Runtime stage
            FROM openjdk:21-jre-slim
            
            # Install necessary packages
            RUN apt-get update && apt-get install -y \\
                curl \\
                && rm -rf /var/lib/apt/lists/*
            
            # Create app user
            RUN groupadd -r app && useradd -r -g app app
            
            WORKDIR /app
            
            # Copy built application from builder stage
            COPY --from=builder /app/target/*.jar app.jar
            
            # Switch to app user
            USER app
            
            # Expose port
            EXPOSE 8080
            
            # Health check
            HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \\
                CMD curl -f http://localhost:8080/health || exit 1
            
            # Start application
            ENTRYPOINT ["java", "-jar", "app.jar"]
            
            # Default command
            CMD ["--feature-branch", "$featureName"]
        """.trimIndent()
    }
    
    private fun generateGenericDockerfile(repoDir: File, featureName: String): String {
        return """
            # Generic Dockerfile for unknown project type
            FROM ubuntu:22.04
            
            # Install necessary packages
            RUN apt-get update && apt-get install -y \\
                curl \\
                wget \\
                git \\
                && rm -rf /var/lib/apt/lists/*
            
            # Create app user
            RUN groupadd -r app && useradd -r -g app app
            
            WORKDIR /app
            
            # Copy application files
            COPY . .
            
            # Switch to app user
            USER app
            
            # Expose port
            EXPOSE 8080
            
            # Health check
            HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \\
                CMD curl -f http://localhost:8080/health || exit 1
            
            # Start application
            CMD ["echo", "Feature branch: $featureName"]
        """.trimIndent()
    }
    
    // Kubernetes manifest generator
    
    private fun generateK8sManifest(repoDir: File, featureName: String, projectType: ProjectType): String {
        val appName = repoDir.name.lowercase().replace(Regex("[^a-z0-9-]"), "-")
        val imageName = "$appName:$featureName"
        
        return """
            apiVersion: apps/v1
            kind: Deployment
            metadata:
              name: $appName-$featureName
              labels:
                app: $appName
                feature: $featureName
            spec:
              replicas: 2
              selector:
                matchLabels:
                  app: $appName
                  feature: $featureName
              template:
                metadata:
                  labels:
                    app: $appName
                    feature: $featureName
                spec:
                  containers:
                  - name: $appName
                    image: $imageName
                    ports:
                    - containerPort: 8080
                    env:
                    - name: FEATURE_BRANCH
                      value: "$featureName"
                    - name: NODE_ENV
                      value: "production"
                    resources:
                      requests:
                        memory: "256Mi"
                        cpu: "250m"
                      limits:
                        memory: "512Mi"
                        cpu: "500m"
                    livenessProbe:
                      httpGet:
                        path: /health
                        port: 8080
                      initialDelaySeconds: 30
                      periodSeconds: 10
                    readinessProbe:
                      httpGet:
                        path: /ready
                        port: 8080
                      initialDelaySeconds: 5
                      periodSeconds: 5
            ---
            apiVersion: v1
            kind: Service
            metadata:
              name: $appName-$featureName-service
            spec:
              selector:
                app: $appName
                feature: $featureName
              ports:
              - protocol: TCP
                port: 80
                targetPort: 8080
              type: ClusterIP
            ---
            apiVersion: autoscaling/v2
            kind: HorizontalPodAutoscaler
            metadata:
              name: $appName-$featureName-hpa
            spec:
              scaleTargetRef:
                apiVersion: apps/v1
                kind: Deployment
                name: $appName-$featureName
              minReplicas: 2
              maxReplicas: 10
              metrics:
              - type: Resource
                resource:
                  name: cpu
                  target:
                    type: Utilization
                    averageUtilization: 70
        """.trimIndent()
    }
    
    // Docker Compose generator
    
    private fun generateDockerCompose(repoDir: File, featureName: String, projectType: ProjectType): String {
        val appName = repoDir.name.lowercase().replace(Regex("[^a-z0-9-]"), "-")
        
        return """
            version: '3.8'
            
            services:
              $appName-$featureName:
                build:
                  context: .
                  dockerfile: Dockerfile
                container_name: $appName-$featureName
                ports:
                  - "8080:8080"
                environment:
                  - FEATURE_BRANCH=$featureName
                  - NODE_ENV=production
                volumes:
                  - ./data:/app/data
                  - ./logs:/app/logs
                networks:
                  - $appName-network
                restart: unless-stopped
                healthcheck:
                  test: ["CMD", "curl", "-f", "http://localhost:8080/health"]
                  interval: 30s
                  timeout: 10s
                  retries: 3
                  start_period: 40s
              
              redis:
                image: redis:7-alpine
                container_name: $appName-redis-$featureName
                ports:
                  - "6379:6379"
                volumes:
                  - redis-data:/data
                networks:
                  - $appName-network
                restart: unless-stopped
            
            volumes:
              redis-data:
                driver: local
            
            networks:
              $appName-network:
                driver: bridge
        """.trimIndent()
    }
    
    // Helm chart generator
    
    private fun generateHelmChart(repoDir: File, featureName: String, projectType: ProjectType): String {
        val appName = repoDir.name.lowercase().replace(Regex("[^a-z0-9-]"), "-")
        
        return """
            # Chart.yaml
            apiVersion: v2
            name: $appName
            description: Helm chart for $appName feature branch $featureName
            type: application
            version: 0.1.0
            appVersion: "1.0.0"
            
            # values.yaml
            replicaCount: 2
            
            image:
              repository: $appName
              tag: $featureName
              pullPolicy: IfNotPresent
            
            service:
              type: ClusterIP
              port: 80
              targetPort: 8080
            
            ingress:
              enabled: true
              className: nginx
              hosts:
                - host: $appName-$featureName.local
                  paths:
                    - path: /
                      pathType: Prefix
            
            resources:
              limits:
                cpu: 500m
                memory: 512Mi
              requests:
                cpu: 250m
                memory: 256Mi
            
            autoscaling:
              enabled: true
              minReplicas: 2
              maxReplicas: 10
              targetCPUUtilizationPercentage: 70
        """.trimIndent()
    }
    
    // Terraform config generator
    
    private fun generateTerraformConfig(repoDir: File, featureName: String, projectType: ProjectType): String {
        val appName = repoDir.name.lowercase().replace(Regex("[^a-z0-9-]"), "-")
        
        return """
            # main.tf
            terraform {
              required_version = ">= 1.0"
              required_providers {
                aws = {
                  source  = "hashicorp/aws"
                  version = "~> 5.0"
                }
              }
            }
            
            provider "aws" {
              region = var.aws_region
            }
            
            # ECS Cluster
            resource "aws_ecs_cluster" "main" {
              name = "$appName-$featureName-cluster"
            }
            
            # ECS Task Definition
            resource "aws_ecs_task_definition" "app" {
              family                   = "$appName-$featureName"
              network_mode             = "awsvpc"
              requires_compatibilities = ["FARGATE"]
              cpu                      = 256
              memory                   = 512
            
              container_definitions = jsonencode([
                {
                  name  = "$appName-$featureName"
                  image = "$appName:$featureName"
                  portMappings = [
                    {
                      containerPort = 8080
                      protocol      = "tcp"
                    }
                  ]
                  environment = [
                    {
                      name  = "FEATURE_BRANCH"
                      value = "$featureName"
                    }
                  ]
                }
              ])
            }
            
            # ECS Service
            resource "aws_ecs_service" "app" {
              name            = "$appName-$featureName-service"
              cluster         = aws_ecs_cluster.main.id
              task_definition = aws_ecs_task_definition.app.arn
              desired_count   = 2
              launch_type     = "FARGATE"
            }
            
            # variables.tf
            variable "aws_region" {
              description = "AWS region"
              type        = string
              default     = "us-west-2"
            }
            
            # outputs.tf
            output "cluster_name" {
              value = aws_ecs_cluster.main.name
            }
            
            output "service_name" {
              value = aws_ecs_service.app.name
            }
        """.trimIndent()
    }
    
    private fun generateGenericDockerRecipe(repoDir: File, featureName: String): DeploymentRecipe {
        return DeploymentRecipe(
            name = "Dockerfile",
            type = RecipeType.DOCKER,
            content = generateGenericDockerfile(repoDir, featureName),
            description = "Generic Docker containerization for $featureName"
        )
    }
}

enum class ProjectType {
    KOTLIN_JVM, NODE_JS, PYTHON, GO, JAVA, UNKNOWN
} 
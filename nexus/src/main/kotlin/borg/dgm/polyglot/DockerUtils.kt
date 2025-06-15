package borg.dgm.polyglot

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.BuildImageResultCallback
import com.github.dockerjava.api.model.*
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import com.github.dockerjava.transport.DockerHttpClient
import java.io.File
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DockerUtils(
    private val logger: (String) -> Unit = { println(it) }
) {
    private val config: DockerClientConfig = DefaultDockerClientConfig.createDefaultConfigBuilder().build()
    private val httpClient: DockerHttpClient = ApacheDockerHttpClient.Builder()
        .dockerHost(config.dockerHost)
        .maxConnections(100)
        .connectionTimeout(Duration.ofSeconds(30))
        .responseTimeout(Duration.ofSeconds(45))
        .build()
    private val dockerClient: DockerClient = DockerClientImpl.getInstance(config, httpClient)

    suspend fun buildBaseImage(testSpec: TestSpec, buildDir: File) {
        withContext(Dispatchers.IO) {
            logger("Building base image for ${testSpec.instanceId}")
            
            val dockerfile = """
                FROM ubuntu:22.04
                
                # Install common dependencies
                RUN apt-get update && apt-get install -y \
                    git \
                    curl \
                    wget \
                    build-essential \
                    && rm -rf /var/lib/apt/lists/*
                
                # Set working directory
                WORKDIR /testbed
            """.trimIndent()

            buildImage(
                imageName = testSpec.baseImageKey,
                dockerfile = dockerfile,
                buildDir = buildDir
            )
        }
    }

    suspend fun buildEnvImage(testSpec: TestSpec, buildDir: File) {
        withContext(Dispatchers.IO) {
            logger("Building environment image for ${testSpec.instanceId}")
            
            val dockerfile = """
                FROM ${testSpec.baseImageKey}
                
                # Copy environment setup script
                COPY setup_env.sh /setup_env.sh
                RUN chmod +x /setup_env.sh
                
                # Run environment setup
                RUN /setup_env.sh
            """.trimIndent()

            // Write setup script
            File(buildDir, "setup_env.sh").writeText(testSpec.setupEnvScript)

            buildImage(
                imageName = testSpec.envImageKey,
                dockerfile = dockerfile,
                buildDir = buildDir
            )
        }
    }

    suspend fun buildInstanceImage(testSpec: TestSpec, buildDir: File) {
        withContext(Dispatchers.IO) {
            logger("Building instance image for ${testSpec.instanceId}")
            
            val dockerfile = """
                FROM ${testSpec.envImageKey}
                
                # Copy repository setup script
                COPY setup_repo.sh /setup_repo.sh
                RUN chmod +x /setup_repo.sh
                
                # Run repository setup
                RUN /setup_repo.sh
            """.trimIndent()

            // Write setup script
            File(buildDir, "setup_repo.sh").writeText(testSpec.installRepoScript)

            buildImage(
                imageName = testSpec.instanceImageKey,
                dockerfile = dockerfile,
                buildDir = buildDir
            )
        }
    }

    suspend fun runTests(testSpec: TestSpec, runId: String): TestResult {
        return withContext(Dispatchers.IO) {
            logger("Running tests for ${testSpec.instanceId}")
            
            // Create container
            val container = dockerClient.createContainerCmd(testSpec.instanceImageKey)
                .withName(testSpec.getInstanceContainerName(runId))
                .withCmd("tail", "-f", "/dev/null")
                .exec()

            try {
                // Start container
                dockerClient.startContainerCmd(container.id).exec()

                // Copy test script
                val testScript = File.createTempFile("test", ".sh")
                testScript.writeText(testSpec.evalScript)
                testScript.setExecutable(true)

                dockerClient.copyArchiveToContainerCmd(container.id)
                    .withHostResource(testScript.absolutePath)
                    .withRemotePath("/test.sh")
                    .exec()

                // Run tests
                val execResult = dockerClient.execCreateCmd(container.id)
                    .withCmd("/test.sh")
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .exec()

                val output = dockerClient.execStartCmd(execResult.id)
                    .exec()
                    .readAllBytes()
                    .toString(Charsets.UTF_8)

                // Get exit code
                val exitCode = dockerClient.inspectExecCmd(execResult.id)
                    .exec()
                    .exitCode

                TestResult(
                    success = exitCode == 0,
                    output = output,
                    exitCode = exitCode
                )
            } finally {
                // Clean up
                dockerClient.removeContainerCmd(container.id)
                    .withForce(true)
                    .exec()
            }
        }
    }

    private suspend fun buildImage(imageName: String, dockerfile: String, buildDir: File) {
        withContext(Dispatchers.IO) {
            // Write Dockerfile
            File(buildDir, "Dockerfile").writeText(dockerfile)

            // Build image
            dockerClient.buildImageCmd()
                .withDockerfile(File(buildDir, "Dockerfile"))
                .withTags(setOf(imageName))
                .exec(BuildImageResultCallback())
                .awaitImageId()
        }
    }

    fun cleanup() {
        httpClient.close()
    }

    data class TestResult(
        val success: Boolean,
        val output: String,
        val exitCode: Int
    )
} 
package com.groom.platform.localdev.docker

import com.groom.platform.localdev.autoconfigure.LocalDevProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.core.io.ResourceLoader
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Manager for Docker Compose operations
 */
class DockerComposeManager(
    private val properties: LocalDevProperties,
    private val resourceLoader: ResourceLoader,
    private val healthChecker: DockerHealthChecker
) {

    private val logger = KotlinLogging.logger {}
    private var composeFile: File? = null

    /**
     * Start Docker Compose services
     */
    fun start() {
        logger.info { "Starting Docker Compose services for local development..." }

        // Check Docker availability
        if (!healthChecker.isDockerAvailable()) {
            throw IllegalStateException(
                """
                Docker is not installed or not running!
                Please install Docker from: https://www.docker.com/get-started
                Or disable local-dev auto-start: platform.local-dev.auto-start=false
                """.trimIndent()
            )
        }

        if (!healthChecker.isDockerComposeAvailable()) {
            throw IllegalStateException(
                """
                Docker Compose is not available!
                Please ensure Docker Desktop is installed with Docker Compose support.
                Or disable local-dev auto-start: platform.local-dev.auto-start=false
                """.trimIndent()
            )
        }

        // Extract compose file
        val composeFile = extractComposeFile()

        // Build environment variables
        val env = buildEnvironment()

        // Run docker compose up
        val processBuilder = ProcessBuilder(
            "docker", "compose",
            "-f", composeFile.absolutePath,
            "-p", properties.projectName,
            "up", "-d"
        )

        processBuilder.environment().putAll(env)
        processBuilder.redirectErrorStream(true)

        logger.info { "Executing: docker compose -f ${composeFile.absolutePath} -p ${properties.projectName} up -d" }

        val process = processBuilder.start()

        // Capture and log output
        BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
            reader.lines().forEach { line ->
                logger.debug { "Docker Compose: $line" }
            }
        }

        val exitCode = process.waitFor(30, TimeUnit.SECONDS)
        if (!exitCode) {
            process.destroyForcibly()
            throw RuntimeException("Docker Compose startup timed out after 30 seconds")
        }

        if (process.exitValue() != 0) {
            throw RuntimeException("Docker Compose failed with exit code: ${process.exitValue()}")
        }

        logger.info { "Docker Compose services started successfully" }
    }

    /**
     * Stop Docker Compose services
     */
    fun stop() {
        logger.info { "Stopping Docker Compose services..." }

        val composeFile = this.composeFile ?: extractComposeFile()

        val processBuilder = ProcessBuilder(
            "docker", "compose",
            "-f", composeFile.absolutePath,
            "-p", properties.projectName,
            "down"
        )

        processBuilder.redirectErrorStream(true)

        val process = processBuilder.start()

        // Capture and log output
        BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
            reader.lines().forEach { line ->
                logger.debug { "Docker Compose: $line" }
            }
        }

        val exitCode = process.waitFor(30, TimeUnit.SECONDS)
        if (!exitCode) {
            process.destroyForcibly()
            logger.warn { "Docker Compose stop timed out after 30 seconds" }
        } else if (process.exitValue() != 0) {
            logger.warn { "Docker Compose stop failed with exit code: ${process.exitValue()}" }
        } else {
            logger.info { "Docker Compose services stopped successfully" }
        }

        // Clean up temp file
        composeFile.deleteOnExit()
    }

    /**
     * Wait for all services to be healthy
     */
    fun waitForHealthy(timeout: Duration) {
        logger.info { "Waiting for all services to be healthy (timeout: ${timeout.seconds}s)..." }

        val startTime = Instant.now()
        val containerNames = getExpectedContainerNames()

        logger.debug { "Expected containers: ${containerNames.joinToString(", ")}" }

        while (Duration.between(startTime, Instant.now()) < timeout) {
            if (checkAllServicesHealthy(containerNames)) {
                logger.info { "All services are healthy!" }
                return
            }

            Thread.sleep(2000) // Wait 2 seconds before next check
            val elapsed = Duration.between(startTime, Instant.now())
            logger.debug { "Still waiting for services to be healthy... (${elapsed.seconds}s elapsed)" }
        }

        throw TimeoutException("Services did not become healthy within $timeout")
    }

    private fun checkAllServicesHealthy(containerNames: List<String>): Boolean {
        return containerNames.all { containerName ->
            val healthy = healthChecker.isContainerHealthy(containerName)
            if (!healthy) {
                logger.debug { "Container $containerName is not yet healthy" }
            }
            healthy
        }
    }

    private fun getExpectedContainerNames(): List<String> {
        val names = mutableListOf<String>()

        if (properties.services.postgres.enabled) {
            names.add("c4ang-postgres-primary")
            names.add("c4ang-postgres-replica")
        }

        if (properties.services.redis.enabled) {
            names.add("c4ang-redis")
        }

        if (properties.services.kafka.enabled) {
            names.add("c4ang-kafka")
        }

        return names
    }

    private fun extractComposeFile(): File {
        if (composeFile != null && composeFile!!.exists()) {
            return composeFile!!
        }

        val resource = resourceLoader.getResource(properties.dockerComposeFile)

        if (!resource.exists()) {
            throw IllegalStateException("Docker Compose file not found: ${properties.dockerComposeFile}")
        }

        // Create temp file
        val tempFile = Files.createTempFile("local-dev", ".yml").toFile()
        tempFile.deleteOnExit()

        // Copy resource to temp file
        resource.inputStream.use { input ->
            Files.copy(input, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }

        logger.debug { "Docker Compose file extracted to: ${tempFile.absolutePath}" }
        composeFile = tempFile
        return tempFile
    }

    private fun buildEnvironment(): Map<String, String> {
        val env = mutableMapOf<String, String>()

        // PostgreSQL settings
        if (properties.services.postgres.enabled) {
            env["PRIMARY_POSTGRES_PORT"] = properties.services.postgres.primaryPort.toString()
            env["REPLICA_POSTGRES_PORT"] = properties.services.postgres.replicaPort.toString()
            env["PRIMARY_POSTGRES_USER"] = properties.services.postgres.username
            env["PRIMARY_POSTGRES_PASSWORD"] = properties.services.postgres.password
            env["PRIMARY_POSTGRES_DB"] = properties.services.postgres.database
            env["REPLICA_POSTGRES_USER"] = properties.services.postgres.username
            env["REPLICA_POSTGRES_PASSWORD"] = properties.services.postgres.password
            env["REPLICA_POSTGRES_DB"] = properties.services.postgres.database
            env["POSTGRES_REPL_USER"] = properties.services.postgres.replicationUser
            env["POSTGRES_REPL_PASSWORD"] = properties.services.postgres.replicationPassword
        }

        // Redis settings
        if (properties.services.redis.enabled) {
            env["REDIS_PORT"] = properties.services.redis.port.toString()
        }

        // Kafka settings
        if (properties.services.kafka.enabled) {
            env["KAFKA_PORT"] = properties.services.kafka.port.toString()
            env["SCHEMA_REGISTRY_PORT"] = properties.services.kafka.schemaRegistryPort.toString()
            env["KAFKA_CLUSTER_ID"] = properties.services.kafka.clusterId
        }

        logger.debug { "Environment variables configured for Docker Compose" }
        return env
    }
}
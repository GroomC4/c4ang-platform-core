package com.groom.platform.localdev.docker

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Health checker for Docker containers
 */
class DockerHealthChecker {

    private val logger = KotlinLogging.logger {}

    /**
     * Check if Docker is available
     */
    fun isDockerAvailable(): Boolean {
        return try {
            val process = ProcessBuilder("docker", "version")
                .redirectErrorStream(true)
                .start()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                logger.warn { "Docker command failed with exit code: $exitCode" }
                false
            } else {
                logger.debug { "Docker is available" }
                true
            }
        } catch (e: Exception) {
            logger.warn { "Docker is not available: ${e.message}" }
            false
        }
    }

    /**
     * Check if Docker Compose is available
     */
    fun isDockerComposeAvailable(): Boolean {
        return try {
            val process = ProcessBuilder("docker", "compose", "version")
                .redirectErrorStream(true)
                .start()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                logger.warn { "Docker Compose command failed with exit code: $exitCode" }
                false
            } else {
                logger.debug { "Docker Compose is available" }
                true
            }
        } catch (e: Exception) {
            logger.warn { "Docker Compose is not available: ${e.message}" }
            false
        }
    }

    /**
     * Check if a container is healthy
     */
    fun isContainerHealthy(containerName: String): Boolean {
        return try {
            // Check if container exists and is running
            val runningProcess = ProcessBuilder(
                "docker", "container", "inspect",
                "-f", "{{.State.Running}}",
                containerName
            ).redirectErrorStream(true).start()

            val running = BufferedReader(InputStreamReader(runningProcess.inputStream)).use { reader ->
                reader.readLine()?.trim() == "true"
            }

            if (!running) {
                logger.debug { "Container $containerName is not running" }
                return false
            }

            // Check health status
            val healthProcess = ProcessBuilder(
                "docker", "container", "inspect",
                "-f", "{{.State.Health.Status}}",
                containerName
            ).redirectErrorStream(true).start()

            val healthStatus = BufferedReader(InputStreamReader(healthProcess.inputStream)).use { reader ->
                reader.readLine()?.trim()
            }

            when (healthStatus) {
                "healthy" -> {
                    logger.debug { "Container $containerName is healthy" }
                    true
                }
                "starting" -> {
                    logger.debug { "Container $containerName is still starting" }
                    false
                }
                "unhealthy" -> {
                    logger.warn { "Container $containerName is unhealthy" }
                    false
                }
                "<no value>", "" -> {
                    // No health check defined, consider it as healthy if running
                    logger.debug { "Container $containerName has no health check, but is running" }
                    true
                }
                else -> {
                    logger.debug { "Container $containerName health status: $healthStatus" }
                    false
                }
            }
        } catch (e: Exception) {
            logger.debug { "Failed to check health of container $containerName: ${e.message}" }
            false
        }
    }

    /**
     * Check if all containers in a project are healthy
     */
    fun areAllContainersHealthy(projectName: String): Boolean {
        return try {
            // Get all container names for the project
            val listProcess = ProcessBuilder(
                "docker", "compose",
                "-p", projectName,
                "ps", "--format", "json"
            ).redirectErrorStream(true).start()

            val output = BufferedReader(InputStreamReader(listProcess.inputStream)).use { reader ->
                reader.readText()
            }

            if (output.isBlank()) {
                logger.debug { "No containers found for project $projectName" }
                return false
            }

            // Parse container names from JSON output
            val containerNames = parseContainerNames(output)

            if (containerNames.isEmpty()) {
                logger.debug { "No containers found for project $projectName" }
                return false
            }

            // Check each container's health
            containerNames.all { containerName ->
                isContainerHealthy(containerName)
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to check containers health for project $projectName" }
            false
        }
    }

    private fun parseContainerNames(jsonOutput: String): List<String> {
        // Simple parsing for container names from docker compose ps output
        // Format: {"Name":"container_name",...}
        val names = mutableListOf<String>()
        val lines = jsonOutput.lines()

        for (line in lines) {
            if (line.contains("\"Name\"")) {
                val nameMatch = "\"Name\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(line)
                nameMatch?.groupValues?.get(1)?.let { names.add(it) }
            }
        }

        return names
    }

    /**
     * Check if a port is available
     */
    fun isPortAvailable(port: Int): Boolean {
        return try {
            java.net.ServerSocket(port).use {
                true
            }
        } catch (e: Exception) {
            logger.debug { "Port $port is not available: ${e.message}" }
            false
        }
    }

    /**
     * Find an available port starting from a preferred port
     */
    fun findAvailablePort(preferredPort: Int): Int {
        if (isPortAvailable(preferredPort)) {
            return preferredPort
        }

        // Try to find an available port
        return try {
            java.net.ServerSocket(0).use { socket ->
                val port = socket.localPort
                logger.info { "Port $preferredPort is not available, using port $port instead" }
                port
            }
        } catch (e: Exception) {
            throw RuntimeException("Failed to find an available port", e)
        }
    }
}
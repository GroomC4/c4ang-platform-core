package com.groom.platform.infrastructure

import com.groom.platform.autoconfigure.InfrastructureProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Docker Compose를 직접 실행하여 인프라를 관리하는 클래스
 *
 * local 프로필에서 사용되며, 컨테이너 생명주기를 관리합니다.
 * - 앱 시작 시: 컨테이너가 없으면 시작, 있으면 재사용
 * - 앱 종료 시: 컨테이너 유지 (개발 편의성)
 */
class DockerComposeManager(
    private val composeFile: File,
    private val properties: InfrastructureProperties,
    private val projectName: String = "c4ang-local"
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Docker Compose로 인프라 시작
     * 이미 실행 중이면 스킵
     */
    fun startIfNotRunning() {
        if (!isDockerAvailable()) {
            throw IllegalStateException(
                """
                Docker is not installed or not running!
                Please install Docker from: https://www.docker.com/get-started
                """.trimIndent()
            )
        }

        val services = getEnabledServices()
        if (services.isEmpty()) {
            logger.info { "No infrastructure services enabled, skipping Docker Compose" }
            return
        }

        logger.info { "Starting Docker Compose services: ${services.joinToString(", ")}" }

        val command = listOf(
            "docker", "compose",
            "-f", composeFile.absolutePath,
            "-p", projectName,
            "up", "-d"
        ) + services

        executeCommand(command, timeout = Duration.ofSeconds(60))

        logger.info { "Docker Compose services started successfully" }
    }

    /**
     * 서비스의 매핑된 포트 조회
     */
    fun getServicePort(service: String, containerPort: Int): Int {
        val command = listOf(
            "docker", "compose",
            "-f", composeFile.absolutePath,
            "-p", projectName,
            "port", service, containerPort.toString()
        )

        val output = executeCommandWithOutput(command)
        // output: "0.0.0.0:32768" or ":::32768"
        return output.trim().substringAfterLast(":").toIntOrNull()
            ?: throw IllegalStateException("Failed to get port for $service:$containerPort, output: $output")
    }

    /**
     * 모든 서비스가 healthy 상태가 될 때까지 대기
     */
    fun waitForHealthy(timeout: Duration = properties.healthCheckTimeout) {
        logger.info { "Waiting for all services to be healthy (timeout: ${timeout.seconds}s)..." }

        val startTime = Instant.now()
        val services = getEnabledServices()

        while (Duration.between(startTime, Instant.now()) < timeout) {
            if (services.all { isServiceHealthy(it) }) {
                logger.info { "All services are healthy!" }
                return
            }

            Thread.sleep(2000)
            val elapsed = Duration.between(startTime, Instant.now())
            logger.debug { "Still waiting for services to be healthy... (${elapsed.seconds}s elapsed)" }
        }

        throw TimeoutException("Services did not become healthy within $timeout")
    }

    /**
     * Docker Compose 중지 (보통 호출하지 않음 - 컨테이너 유지)
     */
    fun stop() {
        logger.info { "Stopping Docker Compose services..." }

        val command = listOf(
            "docker", "compose",
            "-f", composeFile.absolutePath,
            "-p", projectName,
            "down"
        )

        executeCommand(command, timeout = Duration.ofSeconds(30))

        logger.info { "Docker Compose services stopped" }
    }

    private fun getEnabledServices(): List<String> {
        return buildList {
            if (properties.postgres.enabled) {
                add("postgres-primary")
                add("postgres-replica")
            }
            if (properties.redis.enabled) {
                add("redis")
            }
            if (properties.kafka.enabled) {
                add("kafka")
                // Schema Registry는 Kafka와 함께 활성화
                if (properties.kafka.schemaRegistry.enabled) {
                    add("schema-registry")
                }
            }
        }
    }

    private fun isServiceHealthy(service: String): Boolean {
        return try {
            // 먼저 컨테이너 이름 조회
            val containerName = getContainerName(service)
            if (containerName.isBlank()) {
                logger.debug { "Container not found for service: $service" }
                return false
            }

            // docker inspect로 health status 확인 (warning 메시지 없이 깨끗한 출력)
            val command = listOf(
                "docker", "inspect",
                "--format", "{{.State.Health.Status}}",
                containerName
            )

            val output = executeCommandWithOutput(command).trim()
            output.equals("healthy", ignoreCase = true)
        } catch (e: Exception) {
            logger.debug { "Health check failed for $service: ${e.message}" }
            false
        }
    }

    /**
     * Docker Compose 서비스의 컨테이너 이름 조회
     */
    private fun getContainerName(service: String): String {
        val command = listOf(
            "docker", "compose",
            "-f", composeFile.absolutePath,
            "-p", projectName,
            "ps", "--format", "{{.Name}}", service
        )

        return executeCommandWithOutput(command)
            .lines()
            .firstOrNull { it.isNotBlank() && !it.contains("level=") }  // warning 메시지 제외
            ?.trim()
            ?: ""
    }

    private fun isDockerAvailable(): Boolean {
        return try {
            val process = ProcessBuilder("docker", "info")
                .redirectErrorStream(true)
                .start()
            process.waitFor(10, TimeUnit.SECONDS)
            process.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }

    private fun executeCommand(command: List<String>, timeout: Duration) {
        logger.debug { "Executing: ${command.joinToString(" ")}" }

        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()

        // Log output
        BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
            reader.lines().forEach { line ->
                logger.debug { "Docker Compose: $line" }
            }
        }

        val completed = process.waitFor(timeout.seconds, TimeUnit.SECONDS)
        if (!completed) {
            process.destroyForcibly()
            throw RuntimeException("Command timed out after ${timeout.seconds} seconds")
        }

        if (process.exitValue() != 0) {
            throw RuntimeException("Command failed with exit code: ${process.exitValue()}")
        }
    }

    private fun executeCommandWithOutput(command: List<String>): String {
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()

        val completed = process.waitFor(10, TimeUnit.SECONDS)
        if (!completed) {
            process.destroyForcibly()
            throw RuntimeException("Command timed out")
        }

        return output
    }
}

package com.groom.platform.localdev.listener

import com.groom.platform.localdev.autoconfigure.LocalDevProperties
import com.groom.platform.localdev.docker.DockerComposeManager
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationListener
import org.springframework.context.event.ContextClosedEvent
import org.springframework.context.event.EventListener

/**
 * Application listener that manages Docker Compose lifecycle
 */
class LocalDevApplicationListener(
    private val dockerComposeManager: DockerComposeManager,
    private val properties: LocalDevProperties
) : ApplicationListener<ApplicationReadyEvent> {

    private val logger = KotlinLogging.logger {}
    private var shutdownHookRegistered = false

    override fun onApplicationEvent(event: ApplicationReadyEvent) {
        if (!properties.autoStart) {
            logger.info { "Local development auto-start is disabled" }
            return
        }

        logger.info { "Initializing local development infrastructure..." }

        try {
            // Start Docker Compose
            dockerComposeManager.start()

            // Wait for services to be healthy
            if (properties.waitForHealthy) {
                dockerComposeManager.waitForHealthy(properties.healthCheckTimeout)
            }

            logger.info {
                """
                ╔════════════════════════════════════════════════════════════╗
                ║  Local Development Infrastructure is Ready! 🚀             ║
                ╠════════════════════════════════════════════════════════════╣
                ║  PostgreSQL Primary: localhost:${properties.services.postgres.primaryPort.toString().padEnd(5)}                       ║
                ║  PostgreSQL Replica: localhost:${properties.services.postgres.replicaPort.toString().padEnd(5)}                       ║
                ║  Redis:              localhost:${properties.services.redis.port.toString().padEnd(5)}                       ║
                ║  Kafka:              localhost:${properties.services.kafka.port.toString().padEnd(5)}                       ║
                ╚════════════════════════════════════════════════════════════╝
                """.trimIndent()
            }

            // Register shutdown hook
            if (properties.autoStop && !shutdownHookRegistered) {
                registerShutdownHook()
            }

        } catch (e: Exception) {
            logger.error(e) { "Failed to start local development infrastructure" }

            // Try to clean up
            try {
                dockerComposeManager.stop()
            } catch (stopError: Exception) {
                logger.debug(stopError) { "Failed to stop services after startup error" }
            }

            throw RuntimeException(
                "Failed to start local development infrastructure. " +
                "You can disable auto-start by setting: platform.local-dev.auto-start=false",
                e
            )
        }
    }

    @EventListener(ContextClosedEvent::class)
    fun onContextClosed(event: ContextClosedEvent) {
        if (properties.autoStop) {
            stopServices()
        }
    }

    private fun registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(Thread {
            if (properties.autoStop) {
                logger.info { "Shutdown hook triggered, stopping local development infrastructure..." }
                stopServices()
            }
        })
        shutdownHookRegistered = true
        logger.debug { "Shutdown hook registered for Docker Compose cleanup" }
    }

    private fun stopServices() {
        try {
            logger.info { "Stopping local development infrastructure..." }
            dockerComposeManager.stop()
            logger.info { "Local development infrastructure stopped successfully" }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to stop local development infrastructure cleanly" }
        }
    }
}
package com.groom.platform.localdev.autoconfigure

import com.groom.platform.localdev.docker.DockerComposeManager
import com.groom.platform.localdev.docker.DockerHealthChecker
import com.groom.platform.localdev.listener.LocalDevApplicationListener
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Profile
import org.springframework.core.io.ResourceLoader

/**
 * Auto-configuration for Local Development Environment
 */
@AutoConfiguration
@Profile("local")
@ConditionalOnProperty(
    prefix = "platform.local-dev",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true
)
@EnableConfigurationProperties(LocalDevProperties::class)
class LocalDevAutoConfiguration {

    private val logger = KotlinLogging.logger {}

    init {
        logger.info { "Local Development Auto-Configuration is being initialized" }
    }

    @Bean
    fun dockerHealthChecker(): DockerHealthChecker {
        logger.debug { "Creating DockerHealthChecker bean" }
        return DockerHealthChecker()
    }

    @Bean
    fun dockerComposeManager(
        properties: LocalDevProperties,
        resourceLoader: ResourceLoader,
        dockerHealthChecker: DockerHealthChecker
    ): DockerComposeManager {
        logger.debug { "Creating DockerComposeManager bean" }
        return DockerComposeManager(properties, resourceLoader, dockerHealthChecker)
    }

    @Bean
    @ConditionalOnProperty(
        prefix = "platform.local-dev",
        name = ["auto-start"],
        havingValue = "true",
        matchIfMissing = true
    )
    fun localDevApplicationListener(
        dockerComposeManager: DockerComposeManager,
        properties: LocalDevProperties
    ): LocalDevApplicationListener {
        logger.debug { "Creating LocalDevApplicationListener bean" }
        return LocalDevApplicationListener(dockerComposeManager, properties)
    }
}
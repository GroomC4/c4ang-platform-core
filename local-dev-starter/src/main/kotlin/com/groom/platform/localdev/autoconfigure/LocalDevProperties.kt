package com.groom.platform.localdev.autoconfigure

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Local Development Environment Configuration Properties
 */
@ConfigurationProperties(prefix = "platform.local-dev")
data class LocalDevProperties(
    /**
     * Enable local development environment auto-configuration
     */
    var enabled: Boolean = true,

    /**
     * Automatically start Docker Compose when application starts
     */
    var autoStart: Boolean = true,

    /**
     * Automatically stop Docker Compose when application stops
     */
    var autoStop: Boolean = true,

    /**
     * Docker Compose file location (can be classpath: or file:)
     */
    var dockerComposeFile: String = "classpath:docker-compose/local-dev.yml",

    /**
     * Timeout for waiting services to be healthy
     */
    var healthCheckTimeout: Duration = Duration.ofSeconds(60),

    /**
     * Wait for all services to be healthy before starting application
     */
    var waitForHealthy: Boolean = true,

    /**
     * Docker Compose project name
     */
    var projectName: String = "c4ang-local-dev",

    /**
     * Service-specific configurations
     */
    var services: ServicesConfig = ServicesConfig()
) {

    data class ServicesConfig(
        var postgres: PostgresConfig = PostgresConfig(),
        var redis: RedisConfig = RedisConfig(),
        var kafka: KafkaConfig = KafkaConfig()
    )

    data class PostgresConfig(
        var enabled: Boolean = true,
        var primaryPort: Int = 15432,
        var replicaPort: Int = 15433,
        var database: String = "groom",
        var username: String = "application",
        var password: String = "application",
        var replicationUser: String = "repl_user",
        var replicationPassword: String = "repl_password"
    )

    data class RedisConfig(
        var enabled: Boolean = true,
        var port: Int = 6379
    )

    data class KafkaConfig(
        var enabled: Boolean = true,
        var port: Int = 9092,
        var schemaRegistryPort: Int = 8081,
        var useKraft: Boolean = true,
        var clusterId: String = "MkU3OEVBNTcwNTJENDM2Qk"
    )
}
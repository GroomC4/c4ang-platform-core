package com.groom.platform.localdev.autoconfigure

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import javax.sql.DataSource

/**
 * Auto-configuration for DataSource in local development environment
 * This configuration works with datasource-starter to provide automatic DataSource setup
 */
@AutoConfiguration
@Profile("local")
@ConditionalOnClass(DataSource::class, HikariDataSource::class)
@ConditionalOnProperty(
    prefix = "platform.local-dev",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class LocalDevDataSourceConfiguration(
    private val localDevProperties: LocalDevProperties
) {

    private val logger = KotlinLogging.logger {}

    /**
     * Master DataSource for local development
     * This bean will be picked up by datasource-starter's DataSourceAutoConfiguration
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(name = ["masterDataSource"])
    @ConditionalOnProperty(
        prefix = "platform.local-dev.services.postgres",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true
    )
    fun masterDataSource(): DataSource {
        val config = localDevProperties.services.postgres

        logger.info {
            "Configuring master DataSource for local development: " +
            "jdbc:postgresql://localhost:${config.primaryPort}/${config.database}"
        }

        val hikariConfig = HikariConfig().apply {
            jdbcUrl = "jdbc:postgresql://localhost:${config.primaryPort}/${config.database}"
            username = config.username
            password = config.password
            driverClassName = "org.postgresql.Driver"

            // Connection pool settings optimized for local development
            maximumPoolSize = 10
            minimumIdle = 2
            connectionTimeout = 30000
            idleTimeout = 600000
            maxLifetime = 1800000

            // Pool name
            poolName = "LocalDev-Master-Pool"

            // Additional properties
            addDataSourceProperty("cachePrepStmts", "true")
            addDataSourceProperty("prepStmtCacheSize", "250")
            addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
        }

        return HikariDataSource(hikariConfig)
    }

    /**
     * Replica DataSource for local development
     * This bean will be picked up by datasource-starter's DataSourceAutoConfiguration
     */
    @Bean
    @ConditionalOnMissingBean(name = ["replicaDataSource"])
    @ConditionalOnProperty(
        prefix = "platform.local-dev.services.postgres",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true
    )
    fun replicaDataSource(): DataSource {
        val config = localDevProperties.services.postgres

        logger.info {
            "Configuring replica DataSource for local development: " +
            "jdbc:postgresql://localhost:${config.replicaPort}/${config.database}"
        }

        val hikariConfig = HikariConfig().apply {
            jdbcUrl = "jdbc:postgresql://localhost:${config.replicaPort}/${config.database}"
            username = config.username
            password = config.password
            driverClassName = "org.postgresql.Driver"

            // Connection pool settings optimized for local development
            maximumPoolSize = 10
            minimumIdle = 2
            connectionTimeout = 30000
            idleTimeout = 600000
            maxLifetime = 1800000

            // Pool name
            poolName = "LocalDev-Replica-Pool"

            // Additional properties
            addDataSourceProperty("cachePrepStmts", "true")
            addDataSourceProperty("prepStmtCacheSize", "250")
            addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
        }

        return HikariDataSource(hikariConfig)
    }

    /**
     * Alternative configuration method using qualified beans
     * This is used when datasource-starter expects specific bean names
     */
    @Bean("localDevMasterDataSource")
    @ConditionalOnMissingBean(name = ["localDevMasterDataSource"])
    @ConditionalOnProperty(
        prefix = "platform.local-dev.services.postgres",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true
    )
    fun localDevMasterDataSource(): DataSource {
        return masterDataSource()
    }

    @Bean("localDevReplicaDataSource")
    @ConditionalOnMissingBean(name = ["localDevReplicaDataSource"])
    @ConditionalOnProperty(
        prefix = "platform.local-dev.services.postgres",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true
    )
    fun localDevReplicaDataSource(): DataSource {
        return replicaDataSource()
    }
}
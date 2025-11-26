package com.groom.platform.autoconfigure

import com.groom.platform.datasource.DataSourceType
import com.groom.platform.datasource.DynamicRoutingDataSource
import com.zaxxer.hikari.HikariDataSource
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration as SpringDataSourceAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy
import javax.sql.DataSource

/**
 * Platform DataSource 자동 설정
 *
 * Primary-Replica 패턴을 위한 DynamicRoutingDataSource를 구성합니다.
 *
 * **프로퍼티 소스:**
 * - local 프로필: LocalInfraEnvironmentPostProcessor가 동적 포트로 주입
 * - test 프로필: Testcontainers가 동적 포트로 주입
 * - prod 프로필: application-prod.yml 또는 환경변수
 *
 * **자동 구성되는 Bean:**
 * - masterDataSource: Primary DB 연결
 * - replicaDataSource: Replica DB 연결
 * - routingDataSource: @Transactional(readOnly) 기반 라우팅
 * - dataSource: LazyConnectionDataSourceProxy (@Primary)
 */
@AutoConfiguration(before = [SpringDataSourceAutoConfiguration::class])
@ConditionalOnClass(DataSource::class)
@ConditionalOnProperty(prefix = "spring.datasource.master", name = ["url"])
@EnableConfigurationProperties(PlatformProperties::class)
class DataSourceConfiguration {

    private val logger = KotlinLogging.logger {}

    /**
     * Master DataSource
     *
     * spring.datasource.master.* 설정을 사용하여 생성
     */
    @Bean
    @ConditionalOnMissingBean(name = ["masterDataSource"])
    @ConditionalOnProperty(prefix = "spring.datasource.master", name = ["url"])
    fun masterDataSource(
        @Value("\${spring.datasource.master.url}") url: String,
        @Value("\${spring.datasource.master.username}") username: String,
        @Value("\${spring.datasource.master.password}") password: String,
        @Value("\${spring.datasource.master.driver-class-name:org.postgresql.Driver}") driverClassName: String
    ): DataSource {
        logger.info { "Creating Master DataSource: $url" }

        return HikariDataSource().apply {
            jdbcUrl = url
            this.username = username
            this.password = password
            this.driverClassName = driverClassName
            poolName = "Master-HikariPool"
            maximumPoolSize = 10
            minimumIdle = 2
            connectionTimeout = 30000
            idleTimeout = 600000
            maxLifetime = 1800000
        }
    }

    /**
     * Replica DataSource
     *
     * spring.datasource.replica.* 설정을 사용하여 생성
     */
    @Bean
    @ConditionalOnMissingBean(name = ["replicaDataSource"])
    @ConditionalOnProperty(prefix = "spring.datasource.replica", name = ["url"])
    fun replicaDataSource(
        @Value("\${spring.datasource.replica.url}") url: String,
        @Value("\${spring.datasource.replica.username}") username: String,
        @Value("\${spring.datasource.replica.password}") password: String,
        @Value("\${spring.datasource.replica.driver-class-name:org.postgresql.Driver}") driverClassName: String
    ): DataSource {
        logger.info { "Creating Replica DataSource: $url" }

        return HikariDataSource().apply {
            jdbcUrl = url
            this.username = username
            this.password = password
            this.driverClassName = driverClassName
            poolName = "Replica-HikariPool"
            maximumPoolSize = 10
            minimumIdle = 2
            connectionTimeout = 30000
            idleTimeout = 600000
            maxLifetime = 1800000
        }
    }

    /**
     * Routing DataSource
     *
     * @Transactional(readOnly=true) → replicaDataSource
     * @Transactional(readOnly=false) → masterDataSource
     */
    @Bean
    @ConditionalOnMissingBean(name = ["routingDataSource"])
    fun routingDataSource(
        @Qualifier("masterDataSource") masterDataSource: DataSource,
        @Qualifier("replicaDataSource") replicaDataSource: DataSource
    ): DataSource {
        logger.info { "Creating Routing DataSource (MASTER/REPLICA)" }

        return DynamicRoutingDataSource().apply {
            setTargetDataSources(
                mapOf(
                    DataSourceType.MASTER to masterDataSource,
                    DataSourceType.REPLICA to replicaDataSource
                )
            )
            setDefaultTargetDataSource(masterDataSource)
            afterPropertiesSet()
        }
    }

    /**
     * Primary DataSource (LazyConnectionDataSourceProxy)
     *
     * 실제 Connection이 필요한 시점까지 DataSource 선택을 지연시킵니다.
     */
    @Primary
    @Bean
    @ConditionalOnMissingBean(name = ["dataSource"])
    fun dataSource(
        @Qualifier("routingDataSource") routingDataSource: DataSource
    ): DataSource {
        logger.info { "Creating Primary DataSource (LazyConnectionDataSourceProxy)" }
        return LazyConnectionDataSourceProxy(routingDataSource)
    }
}

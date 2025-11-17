package com.groom.platform.testcontainers.autoconfigure

import com.groom.platform.datasource.DataSourceType
import com.groom.platform.datasource.DynamicRoutingDataSource
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import javax.sql.DataSource

/**
 * 테스트 환경 DataSource 자동 설정
 *
 * Testcontainers로 시작된 PostgreSQL 컨테이너를 사용하여
 * Primary-Replica 패턴의 DataSource를 자동으로 구성합니다.
 *
 * **자동 구성되는 Bean:**
 * - masterDataSource: Primary PostgreSQL
 * - replicaDataSource: Replica PostgreSQL (또는 Primary와 동일)
 * - routingDataSource: @Transactional(readOnly) 기반 라우팅
 * - dataSource: LazyConnectionDataSourceProxy
 * - redisConnectionFactory: Redis 연결
 *
 * **사용법:**
 * ```kotlin
 * @SpringBootTest
 * class MyTest {
 *     @Autowired
 *     private lateinit var dataSource: DataSource  // 자동 주입!
 *
 *     @Test
 *     @Transactional(readOnly = false)  // MASTER
 *     fun testWrite() { ... }
 *
 *     @Test
 *     @Transactional(readOnly = true)  // REPLICA
 *     fun testRead() { ... }
 * }
 * ```
 */
@AutoConfiguration(
    before = [DataSourceAutoConfiguration::class],
    after = [TestcontainersAutoConfiguration::class],
)
@ConditionalOnClass(PostgreSQLContainer::class)
@Profile("test")
class TestDataSourceAutoConfiguration {

    /**
     * Master DataSource (Primary PostgreSQL)
     */
    @Bean
    @ConditionalOnBean(name = ["postgresContainer"])
    @ConditionalOnProperty(prefix = "testcontainers.postgres", name = ["enabled"], matchIfMissing = true)
    fun masterDataSource(
        @Qualifier("postgresContainer") postgresContainer: PostgreSQLContainer<*>,
    ): DataSource {
        val config =
            HikariConfig().apply {
                jdbcUrl = postgresContainer.jdbcUrl
                username = postgresContainer.username
                password = postgresContainer.password
                driverClassName = "org.postgresql.Driver"
                maximumPoolSize = 10
                connectionTimeout = 30000
                poolName = "TestMasterHikariPool"
            }

        println("✅ Master DataSource configured: ${postgresContainer.jdbcUrl}")
        return HikariDataSource(config)
    }

    /**
     * Replica DataSource (Replica PostgreSQL)
     */
    @Bean
    @ConditionalOnBean(name = ["postgresReplicaContainer"])
    fun replicaDataSource(
        @Qualifier("postgresReplicaContainer") postgresReplicaContainer: PostgreSQLContainer<*>,
    ): DataSource {
        val config =
            HikariConfig().apply {
                jdbcUrl = postgresReplicaContainer.jdbcUrl
                username = postgresReplicaContainer.username
                password = postgresReplicaContainer.password
                driverClassName = "org.postgresql.Driver"
                maximumPoolSize = 10
                connectionTimeout = 30000
                poolName = "TestReplicaHikariPool"
            }

        println("✅ Replica DataSource configured: ${postgresReplicaContainer.jdbcUrl}")
        return HikariDataSource(config)
    }

    /**
     * Routing DataSource
     *
     * @Transactional(readOnly=true) → replicaDataSource
     * @Transactional(readOnly=false) → masterDataSource
     */
    @Bean
    fun routingDataSource(
        @Qualifier("masterDataSource") masterDataSource: DataSource,
        @Qualifier("replicaDataSource") replicaDataSource: DataSource,
    ): DataSource {
        val router = DynamicRoutingDataSource()
        router.setTargetDataSources(
            mapOf(
                DataSourceType.MASTER to masterDataSource,
                DataSourceType.REPLICA to replicaDataSource,
            ),
        )
        router.setDefaultTargetDataSource(masterDataSource)
        router.afterPropertiesSet()

        println("✅ Routing DataSource configured (MASTER/REPLICA)")
        return router
    }

    /**
     * Primary DataSource (LazyConnectionDataSourceProxy)
     *
     * Spring이 사용할 기본 DataSource
     */
    @Primary
    @Bean
    fun dataSource(
        @Qualifier("routingDataSource") routingDataSource: DataSource,
    ): DataSource {
        return LazyConnectionDataSourceProxy(routingDataSource)
    }

    /**
     * Redis ConnectionFactory
     */
    @Bean
    @ConditionalOnBean(name = ["redisContainer"])
    @ConditionalOnProperty(prefix = "testcontainers.redis", name = ["enabled"], matchIfMissing = true)
    fun redisConnectionFactory(
        @Qualifier("redisContainer") redisContainer: GenericContainer<*>,
    ): RedisConnectionFactory {
        val factory = LettuceConnectionFactory(redisContainer.host, redisContainer.getMappedPort(6379))
        factory.afterPropertiesSet()

        println("✅ Redis ConnectionFactory configured: ${redisContainer.host}:${redisContainer.getMappedPort(6379)}")
        return factory
    }
}

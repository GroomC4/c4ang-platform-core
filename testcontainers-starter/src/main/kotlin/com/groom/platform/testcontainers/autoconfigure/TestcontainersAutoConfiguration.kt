package com.groom.platform.testcontainers.autoconfigure

import com.groom.platform.testcontainers.container.SharedContainers
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.PostgreSQLContainer

/**
 * Testcontainers 자동 설정
 *
 * 이 AutoConfiguration은 통합 테스트를 위한 컨테이너들을 자동으로 시작합니다:
 * - PostgreSQL (Primary)
 * - PostgreSQL (Replica, 옵션)
 * - Redis
 * - Kafka
 *
 * **사용법:**
 * 1. 테스트 클래스에 @SpringBootTest 어노테이션 추가
 * 2. application-test.yml에 설정 추가
 * 3. 끝! 컨테이너가 자동으로 시작됩니다
 *
 * **설정 예시 (application-test.yml):**
 * ```yaml
 * spring:
 *   profiles:
 *     active: test
 *
 * testcontainers:
 *   postgres:
 *     enabled: true
 *     replica-enabled: true
 *     schema-location: classpath:db/schema.sql
 *   redis:
 *     enabled: true
 *   kafka:
 *     enabled: true
 * ```
 *
 * **테스트 코드:**
 * ```kotlin
 * @SpringBootTest
 * class MyIntegrationTest {
 *     @Autowired
 *     private lateinit var dataSource: DataSource  // 자동 주입!
 *
 *     @Test
 *     @Transactional(readOnly = true)  // REPLICA 자동!
 *     fun test() { ... }
 * }
 * ```
 */
@AutoConfiguration
@ConditionalOnClass(PostgreSQLContainer::class)
@EnableConfigurationProperties(TestcontainersProperties::class)
class TestcontainersAutoConfiguration(
    private val properties: TestcontainersProperties,
) {
    /**
     * PostgreSQL Primary 컨테이너 (JVM 전체 공유 싱글톤)
     *
     * SharedContainers.postgresContainer를 반환합니다.
     * 이 컨테이너는 JVM당 한 번만 시작되며, 모든 테스트가 공유합니다.
     */
    @Bean
    @ConditionalOnProperty(prefix = "testcontainers.postgres", name = ["enabled"], matchIfMissing = true)
    fun postgresContainer(): PostgreSQLContainer<*> {
        val postgres = properties.postgres
        val container = SharedContainers.postgresContainer

        // 스키마 파일 자동 로딩 (이미 시작된 컨테이너인 경우 스킵)
        if (!container.isRunning) {
            postgres.schemaLocation?.let { schemaPath ->
                val cleanPath = schemaPath.removePrefix("classpath:").removePrefix("file:")
                container.withInitScript(cleanPath)
                println("📄 PostgreSQL Primary: Schema location configured as $schemaPath")
            }

            // 컨테이너 시작
            container.start()
            println("✅ PostgreSQL Primary container started and ready (${container.jdbcUrl})")
        } else {
            println("✅ PostgreSQL Primary container already running (${container.jdbcUrl})")
        }

        return container
    }

    /**
     * PostgreSQL Replica 컨테이너 (JVM 전체 공유 싱글톤)
     *
     * replica-enabled=false인 경우 Primary 컨테이너를 반환합니다.
     * 이렇게 하면 라우팅 로직은 작동하지만 실제로는 같은 DB를 사용합니다.
     */
    @Bean
    @ConditionalOnProperty(prefix = "testcontainers.postgres", name = ["replica-enabled"], matchIfMissing = true)
    fun postgresReplicaContainer(
        postgresContainer: PostgreSQLContainer<*>,
    ): PostgreSQLContainer<*> {
        if (!properties.postgres.replicaEnabled) {
            println("⚠️  PostgreSQL Replica disabled, using Primary for both MASTER and REPLICA")
            return postgresContainer
        }

        val postgres = properties.postgres
        val container = SharedContainers.postgresReplicaContainer

        // 스키마 파일 자동 로딩 (이미 시작된 컨테이너인 경우 스킵)
        if (!container.isRunning) {
            postgres.schemaLocation?.let { schemaPath ->
                val cleanPath = schemaPath.removePrefix("classpath:").removePrefix("file:")
                container.withInitScript(cleanPath)
                println("📄 PostgreSQL Replica: Schema location configured as $schemaPath")
            }

            // 컨테이너 시작
            container.start()
            println("✅ PostgreSQL Replica container started and ready (${container.jdbcUrl})")
        } else {
            println("✅ PostgreSQL Replica container already running (${container.jdbcUrl})")
        }

        return container
    }

    /**
     * Redis 컨테이너 (JVM 전체 공유 싱글톤)
     */
    @Bean
    @ConditionalOnProperty(prefix = "testcontainers.redis", name = ["enabled"], matchIfMissing = true)
    fun redisContainer(): GenericContainer<*> {
        println("📄 Redis: Using shared singleton container")
        return SharedContainers.redisContainer
    }

    /**
     * Kafka 컨테이너 (JVM 전체 공유 싱글톤)
     */
    @Bean
    @ConditionalOnProperty(prefix = "testcontainers.kafka", name = ["enabled"], matchIfMissing = true)
    fun kafkaContainer(): KafkaContainer {
        println("📄 Kafka: Using shared singleton container")
        return SharedContainers.kafkaContainer
    }

    /**
     * Schema Registry 컨테이너 (JVM 전체 공유 싱글톤)
     *
     * Kafka Avro 직렬화/역직렬화를 위한 스키마 레지스트리입니다.
     */
    @Bean
    @ConditionalOnProperty(prefix = "testcontainers.schema-registry", name = ["enabled"], matchIfMissing = true)
    fun schemaRegistryContainer(
        kafkaContainer: KafkaContainer,
    ): GenericContainer<*> {
        println("📄 Schema Registry: Using shared singleton container")
        return SharedContainers.schemaRegistryContainer
    }
}

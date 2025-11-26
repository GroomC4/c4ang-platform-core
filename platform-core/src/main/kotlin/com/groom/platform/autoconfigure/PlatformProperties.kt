package com.groom.platform.autoconfigure

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Platform Core 통합 설정 프로퍼티
 *
 * **사용 예시 (application.yml):**
 * ```yaml
 * platform:
 *   infrastructure:
 *     postgres:
 *       enabled: true
 *       database: groom
 *       primary-port: 15432
 *       replica-port: 15433
 *     redis:
 *       enabled: true
 *       port: 6379
 *     kafka:
 *       enabled: false
 *
 *   datasource:
 *     replica-enabled: true
 *
 *   schema:
 *     init-enabled: true
 *     locations:
 *       - classpath:sql/schema.sql
 * ```
 */
@ConfigurationProperties(prefix = "platform")
data class PlatformProperties(
    val infrastructure: InfrastructureProperties = InfrastructureProperties(),
    val datasource: DataSourceProperties = DataSourceProperties(),
    val schema: SchemaProperties = SchemaProperties()
)

data class InfrastructureProperties(
    /**
     * Docker Compose 자동 시작 여부 (local 프로필에서만 동작)
     */
    val dockerComposeEnabled: Boolean = true,

    /**
     * 헬스체크 타임아웃
     */
    val healthCheckTimeout: Duration = Duration.ofSeconds(60),

    /**
     * PostgreSQL 설정
     */
    val postgres: PostgresProperties = PostgresProperties(),

    /**
     * Redis 설정
     */
    val redis: RedisProperties = RedisProperties(),

    /**
     * Kafka 설정
     */
    val kafka: KafkaProperties = KafkaProperties()
)

data class PostgresProperties(
    val enabled: Boolean = true,
    val database: String = "groom",
    val username: String = "application",
    val password: String = "application",
    val primaryPort: Int = 15432,
    val replicaPort: Int = 15433
)

data class RedisProperties(
    val enabled: Boolean = true,
    val port: Int = 6379
)

data class KafkaProperties(
    val enabled: Boolean = true,
    val port: Int = 9092
)

data class DataSourceProperties(
    /**
     * Replica DataSource 활성화 여부
     */
    val replicaEnabled: Boolean = true,

    /**
     * DataSource 라우팅 로깅 활성화 여부
     */
    val loggingEnabled: Boolean = false
)

data class SchemaProperties(
    /**
     * 스키마 초기화 활성화 여부
     */
    val initEnabled: Boolean = true,

    /**
     * 스키마 파일 위치
     */
    val locations: List<String> = listOf("classpath:sql/schema.sql")
)

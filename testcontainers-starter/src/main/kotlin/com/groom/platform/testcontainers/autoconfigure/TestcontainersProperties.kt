package com.groom.platform.testcontainers.autoconfigure

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Testcontainers 설정 프로퍼티
 *
 * **사용 예시 (application-test.yml):**
 * ```yaml
 * testcontainers:
 *   postgres:
 *     enabled: true
 *     replica-enabled: true
 *     schema-location: classpath:db/schema.sql
 *   redis:
 *     enabled: true
 *   kafka:
 *     enabled: true
 *     topics:
 *       - order-events
 *       - payment-events
 * ```
 */
@ConfigurationProperties(prefix = "testcontainers")
data class TestcontainersProperties(
    /**
     * PostgreSQL 설정
     */
    var postgres: PostgresProperties = PostgresProperties(),

    /**
     * Redis 설정
     */
    var redis: RedisProperties = RedisProperties(),

    /**
     * Kafka 설정
     */
    var kafka: KafkaProperties = KafkaProperties(),

    /**
     * Schema Registry 설정
     */
    var schemaRegistry: SchemaRegistryProperties = SchemaRegistryProperties(),
) {
    /**
     * PostgreSQL 컨테이너 설정
     */
    data class PostgresProperties(
        /**
         * PostgreSQL 컨테이너 활성화 여부
         * 기본값: true
         */
        var enabled: Boolean = true,

        /**
         * Replica 컨테이너 활성화 여부
         * true: Primary와 Replica 두 개의 컨테이너 시작
         * false: Primary만 시작 (Replica는 Primary와 동일한 컨테이너 사용)
         * 기본값: true
         */
        var replicaEnabled: Boolean = true,

        /**
         * Docker 이미지
         * 기본값: postgres:17
         */
        var image: String = "postgres:17",

        /**
         * 데이터베이스 스키마 파일 경로
         *
         * - classpath:db/schema.sql (클래스패스)
         * - file:/absolute/path/schema.sql (절대 경로)
         * - null (스키마 초기화 안함)
         *
         * 기본값: null
         */
        var schemaLocation: String? = null,

        /**
         * 데이터베이스 이름
         * 기본값: testdb
         */
        var database: String = "testdb",

        /**
         * 사용자 이름
         * 기본값: test
         */
        var username: String = "test",

        /**
         * 비밀번호
         * 기본값: test
         */
        var password: String = "test",

        /**
         * 최대 연결 수
         * 기본값: 10
         */
        var maxConnections: Int = 10,
    )

    /**
     * Redis 컨테이너 설정
     */
    data class RedisProperties(
        /**
         * Redis 컨테이너 활성화 여부
         * 기본값: true
         */
        var enabled: Boolean = true,

        /**
         * Docker 이미지
         * 기본값: redis:7-alpine
         */
        var image: String = "redis:7-alpine",
    )

    /**
     * Kafka 컨테이너 설정
     */
    data class KafkaProperties(
        /**
         * Kafka 컨테이너 활성화 여부
         * 기본값: true
         */
        var enabled: Boolean = true,

        /**
         * Docker 이미지
         * 기본값: confluentinc/cp-kafka:7.5.1
         */
        var image: String = "confluentinc/cp-kafka:7.5.1",

        /**
         * 자동 생성할 토픽 리스트
         * 기본값: 빈 리스트
         */
        var topics: List<String> = emptyList(),
    )

    /**
     * Schema Registry 컨테이너 설정
     */
    data class SchemaRegistryProperties(
        /**
         * Schema Registry 컨테이너 활성화 여부
         * 기본값: true
         */
        var enabled: Boolean = true,
    )
}

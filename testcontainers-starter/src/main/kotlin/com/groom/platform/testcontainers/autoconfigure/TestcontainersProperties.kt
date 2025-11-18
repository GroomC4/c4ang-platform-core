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
         * 토픽 자동 생성 활성화 여부
         *
         * - true: Producer가 존재하지 않는 토픽에 메시지를 보낼 때 자동으로 토픽 생성 (기본값)
         * - false: 명시적으로 정의된 토픽만 사용 가능
         *
         * **권장 사용법:**
         * - 테스트 환경: true (편의성)
         * - 운영 환경 시뮬레이션: false + topics 명시
         *
         * 기본값: true
         */
        var autoCreateTopics: Boolean = true,

        /**
         * 사전 정의 토픽 리스트
         *
         * 이 목록에 정의된 토픽은 Kafka 컨테이너 시작 시 자동으로 생성됩니다.
         * autoCreateTopics=true인 경우에도 이 토픽들은 지정한 설정으로 우선 생성됩니다.
         *
         * **사용 예시:**
         * ```yaml
         * testcontainers:
         *   kafka:
         *     auto-create-topics: true  # 기본값, 명시 안해도 됨
         *     topics:
         *       - name: store.info.updated
         *         partitions: 3
         *         replication-factor: 1
         *         config:
         *           retention.ms: 604800000  # 7일
         *       - name: order.created
         *         partitions: 1
         *         replication-factor: 1
         * ```
         *
         * 기본값: 빈 리스트
         */
        var topics: List<KafkaTopicConfig> = emptyList(),
    )

    /**
     * Kafka 토픽 설정
     */
    data class KafkaTopicConfig(
        /**
         * 토픽 이름
         */
        var name: String,

        /**
         * 파티션 수
         *
         * 파티션 수는 병렬 처리 성능에 영향을 줍니다.
         * - 1: 메시지 순서 보장, 단일 Consumer
         * - 여러 개: 높은 처리량, 파티션별 순서만 보장
         *
         * 기본값: 1
         */
        var partitions: Int = 1,

        /**
         * 복제 계수
         *
         * 각 파티션의 복사본 개수입니다.
         * - 1: 복제 없음 (테스트 환경 적합)
         * - 2: 1대 장애 허용
         * - 3: 2대 장애 허용 (운영 환경 권장)
         *
         * ⚠️ Testcontainers는 단일 브로커이므로 1만 가능합니다.
         *
         * 기본값: 1
         */
        var replicationFactor: Short = 1,

        /**
         * 토픽별 설정
         *
         * Kafka 토픽 설정 옵션을 key-value로 지정합니다.
         *
         * **주요 설정:**
         * - retention.ms: 메시지 보관 시간 (밀리초)
         * - retention.bytes: 파티션당 최대 크기
         * - max.message.bytes: 최대 메시지 크기
         * - compression.type: 압축 방식 (gzip, snappy, lz4, zstd)
         *
         * 기본값: 빈 맵
         */
        var config: Map<String, String> = emptyMap(),
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

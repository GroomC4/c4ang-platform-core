package com.groom.platform.testcontainers.container

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * JVM 전체에서 공유되는 Testcontainer 싱글톤 관리
 *
 * 이 객체는 모든 통합 테스트에서 컨테이너를 공유하기 위해 static singleton 패턴을 사용합니다.
 * - 컨테이너는 JVM당 한 번만 시작됩니다
 * - 모든 테스트 클래스가 동일한 컨테이너 인스턴스를 공유합니다
 * - 테스트가 끝나도 컨테이너는 유지되며, JVM 종료 시에만 정리됩니다
 *
 * **성능 이점:**
 * - 첫 테스트: 컨테이너 시작 (느림)
 * - 이후 모든 테스트: 컨테이너 재사용 (빠름!)
 *
 * **사용법:**
 * 서비스 레포에서는 이 클래스를 직접 사용하지 않습니다.
 * TestcontainersAutoConfiguration이 자동으로 이 싱글톤을 참조합니다.
 */
object SharedContainers {
    /**
     * PostgreSQL Primary 컨테이너 (싱글톤)
     *
     * lazy 초기화: 처음 사용될 때 한 번만 시작됩니다.
     */
    val postgresContainer: PostgreSQLContainer<*> by lazy {
        println("🚀 Starting shared PostgreSQL Primary container...")
        PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true)
            .apply {
                start()
                println("✅ PostgreSQL Primary container started and ready (${this.jdbcUrl})")
            }
    }

    /**
     * PostgreSQL Replica 컨테이너 (싱글톤)
     *
     * 참고: 테스트 환경에서는 실제 복제를 구성하지 않고,
     * 별도의 독립적인 PostgreSQL 인스턴스를 Replica로 사용합니다.
     */
    val postgresReplicaContainer: PostgreSQLContainer<*> by lazy {
        println("🚀 Starting shared PostgreSQL Replica container...")
        PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true)
            .apply {
                start()
                println("✅ PostgreSQL Replica container started and ready (${this.jdbcUrl})")
            }
    }

    /**
     * Redis 컨테이너 (싱글톤)
     */
    val redisContainer: GenericContainer<*> by lazy {
        println("🚀 Starting shared Redis container...")
        GenericContainer(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withReuse(true)
            .apply {
                start()
                println("✅ Redis container started and ready (${this.host}:${this.getMappedPort(6379)})")
            }
    }

    /**
     * Kafka 컨테이너 (싱글톤)
     *
     * KRaft 모드를 사용하여 Zookeeper 없이 작동합니다.
     */
    val kafkaContainer: KafkaContainer by lazy {
        println("🚀 Starting shared Kafka container...")
        KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.1"))
            .withReuse(true)
            .apply {
                start()
                println("✅ Kafka container started and ready (${this.bootstrapServers})")
            }
    }

    /**
     * Schema Registry 컨테이너 (싱글톤)
     *
     * Kafka Avro 직렬화를 위한 스키마 레지스트리입니다.
     * Kafka 컨테이너에 의존하며, Kafka가 먼저 시작된 후 실행됩니다.
     *
     * **안정성 설정**:
     * - 테스트 환경: replication.factor=1 (단일 브로커)
     * - 프로덕션 환경: replication.factor=3, min.insync.replicas=2 권장
     */
    val schemaRegistryContainer: GenericContainer<*> by lazy {
        println("🚀 Starting shared Schema Registry container...")

        // Kafka 컨테이너가 먼저 시작되도록 보장
        val kafka = kafkaContainer

        GenericContainer(DockerImageName.parse("confluentinc/cp-schema-registry:7.5.1"))
            .withExposedPorts(8081)
            .withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
            .withEnv("SCHEMA_REGISTRY_LISTENERS", "http://0.0.0.0:8081")
            .withEnv(
                "SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS",
                "PLAINTEXT://${kafka.host}:${kafka.firstMappedPort}"
            )
            // ===== _schemas 토픽 안정성 설정 =====
            // 테스트 환경: 단일 브로커이므로 replication.factor=1
            // 프로덕션 환경: 3-broker 클러스터에서는 3으로 변경 권장
            .withEnv("SCHEMA_REGISTRY_KAFKASTORE_TOPIC_REPLICATION_FACTOR", "1")

            // 최소 동기화 복제본 수 (프로덕션에서는 2 권장)
            // replication.factor=3일 때 min.insync.replicas=2 설정 시
            // 최대 1개 브로커 장애까지 허용하면서도 데이터 안전성 보장
            .withEnv("SCHEMA_REGISTRY_KAFKASTORE_MIN_INSYNC_REPLICAS", "1")

            // Kafka 연결 타임아웃 (ms)
            .withEnv("SCHEMA_REGISTRY_KAFKASTORE_TIMEOUT_MS", "10000")

            // Schema Registry 초기화 타임아웃 (ms)
            .withEnv("SCHEMA_REGISTRY_KAFKASTORE_INIT_TIMEOUT_MS", "60000")

            // 스키마 호환성 정책 (BACKWARD: 새 Consumer가 예전 데이터 읽기 가능)
            // 다른 옵션: FORWARD, FULL, NONE
            .withEnv("SCHEMA_REGISTRY_SCHEMA_COMPATIBILITY_LEVEL", "BACKWARD")

            .withReuse(true)
            .apply {
                start()
                val schemaRegistryUrl = "http://${this.host}:${this.getMappedPort(8081)}"
                println("✅ Schema Registry container started and ready ($schemaRegistryUrl)")
                println("   - Replication Factor: 1 (테스트 환경)")
                println("   - Compatibility Level: BACKWARD")
                println("   ⚠️  프로덕션 배포 시 replication.factor=3, min.insync.replicas=2 권장")
            }
    }

    /**
     * 스키마를 적용한 PostgreSQL Primary 컨테이너를 반환합니다.
     *
     * @param schemaLocation 스키마 파일 경로 (예: "db/schema.sql")
     * @return 스키마가 적용된 PostgreSQL 컨테이너
     */
    fun getPostgresWithSchema(schemaLocation: String): PostgreSQLContainer<*> {
        // 이미 시작된 컨테이너에 스키마 적용
        // 주의: withInitScript는 컨테이너 시작 전에만 작동하므로,
        // 여기서는 이미 시작된 컨테이너를 반환만 합니다.
        // 스키마 적용은 TestcontainersAutoConfiguration에서 처리합니다.
        return postgresContainer
    }

    /**
     * 스키마를 적용한 PostgreSQL Replica 컨테이너를 반환합니다.
     */
    fun getPostgresReplicaWithSchema(schemaLocation: String): PostgreSQLContainer<*> {
        return postgresReplicaContainer
    }
}

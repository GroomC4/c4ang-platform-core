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
     * 컨테이너 간 통신을 위한 공유 네트워크
     */
    private val network: org.testcontainers.containers.Network by lazy {
        org.testcontainers.containers.Network.newNetwork()
    }

    /**
     * PostgreSQL 컨테이너 (싱글톤)
     *
     * **단일 컨테이너 모드:**
     * - 테스트 환경에서는 단일 PostgreSQL 인스턴스 사용
     * - Primary와 Replica DataSource 모두 이 컨테이너를 참조
     * - 라우팅 로직은 정상 작동하지만 실제 복제는 없음
     *
     * **장점:**
     * - 빠른 테스트 실행 (컨테이너 1개만 시작)
     * - 간단한 설정
     * - @Transactional(readOnly) 라우팅 로직 테스트 가능
     *
     * **향후 계획:**
     * - 실제 Streaming Replication 구현 예정 (v2.0)
     * - GenericContainer 기반 커스텀 복제 설정
     *
     * lazy 초기화: 처음 사용될 때 한 번만 시작됩니다.
     */
    val postgresContainer: PostgreSQLContainer<*> by lazy {
        println("🚀 Initializing shared PostgreSQL container...")
        PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            .withReuse(false)  // 매번 새로운 컨테이너로 스키마 재적용 보장
            // Note: Do NOT call start() here!
            // The container will be started after schema configuration in TestcontainersAutoConfiguration
    }

    /**
     * Redis 컨테이너 (싱글톤)
     */
    val redisContainer: GenericContainer<*> by lazy {
        println("🚀 Starting shared Redis container...")
        GenericContainer(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withReuse(false)  // 매번 새로운 컨테이너로 깨끗한 환경 보장
            .apply {
                start()
                println("✅ Redis container started and ready (${this.host}:${this.getMappedPort(6379)})")
            }
    }

    /**
     * Kafka 컨테이너 (싱글톤)
     *
     * KRaft 모드를 사용하여 Zookeeper 없이 작동합니다.
     *
     * **토픽 자동 생성 설정:**
     * - auto.create.topics.enable=true: Producer가 존재하지 않는 토픽에 메시지를 보낼 때 자동 생성
     * - num.partitions=1: 자동 생성되는 토픽의 기본 파티션 수
     * - default.replication.factor=1: 단일 브로커 환경이므로 복제 계수 1
     *
     * **주의사항:**
     * - 프로덕션 환경에서는 토픽을 사전에 생성하고 적절한 파티션/복제 설정을 권장
     * - 테스트 환경에서는 편의를 위해 자동 생성 활성화
     */
    val kafkaContainer: KafkaContainer by lazy {
        println("🚀 Starting shared Kafka container...")
        KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.1"))
            .withNetwork(network)
            .withNetworkAliases("kafka")
            .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "true")
            .withEnv("KAFKA_NUM_PARTITIONS", "1")
            .withEnv("KAFKA_DEFAULT_REPLICATION_FACTOR", "1")
            .withReuse(false)  // 매번 새로운 컨테이너로 깨끗한 환경 보장
            .apply {
                start()
                println("✅ Kafka container started and ready (${this.bootstrapServers})")
                println("   - Auto Create Topics: Enabled")
                println("   - Default Partitions: 1")
                println("   - Replication Factor: 1")
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
            .withNetwork(network)
            .withNetworkAliases("schema-registry")
            .withExposedPorts(8081)
            .withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
            .withEnv("SCHEMA_REGISTRY_LISTENERS", "http://0.0.0.0:8081")
            .withEnv(
                "SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS",
                "PLAINTEXT://kafka:9092"  // Network alias 사용
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

            // Wait strategy: Schema Registry가 완전히 준비될 때까지 대기
            .waitingFor(
                org.testcontainers.containers.wait.strategy.HttpWaitStrategy()
                    .forPath("/subjects")
                    .forPort(8081)
                    .forStatusCode(200)
                    .withStartupTimeout(java.time.Duration.ofSeconds(60))
            )
            .withReuse(false)  // 매번 새로운 컨테이너로 깨끗한 환경 보장
            .apply {
                start()
                val schemaRegistryUrl = "http://${this.host}:${this.getMappedPort(8081)}"
                println("✅ Schema Registry container started and ready ($schemaRegistryUrl)")
                println("   - Replication Factor: 1 (테스트 환경)")
                println("   - Compatibility Level: BACKWARD")
                println("   ⚠️  프로덕션 배포 시 replication.factor=3, min.insync.replicas=2 권장")
            }
    }

}

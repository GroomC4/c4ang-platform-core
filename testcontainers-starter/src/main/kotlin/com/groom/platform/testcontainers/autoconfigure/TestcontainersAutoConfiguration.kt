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

        println("🔍 [DEBUG] PostgreSQL Container Status:")
        println("  - isRunning: ${container.isRunning}")
        println("  - schemaLocation: ${postgres.schemaLocation}")

        // 스키마 파일 자동 로딩 (이미 시작된 컨테이너인 경우 스킵)
        if (!container.isRunning) {
            println("🔍 [DEBUG] Container not running, proceeding with schema setup...")
            postgres.schemaLocation?.let { schemaPath ->
                println("🔍 [DEBUG] Schema path found: $schemaPath")
                when {
                    schemaPath.startsWith("project:") -> {
                        // project: 프로토콜 - 자동 경로 탐색 (IntelliJ/Gradle 환경 모두 지원)
                        val relativePath = schemaPath.removePrefix("project:")

                        // 경로 후보들을 시도 (IntelliJ는 모듈 루트, Gradle은 프로젝트 루트)
                        val userDir = java.io.File(System.getProperty("user.dir"))
                        val candidates = listOfNotNull(
                            userDir.resolve(relativePath),  // 1순위: user.dir 기준 (IntelliJ 모듈 루트)
                            userDir.parentFile?.resolve(relativePath)  // 2순위: 상위 디렉토리 기준 (Gradle 프로젝트 루트)
                        )

                        // 실제 존재하는 첫 번째 파일 찾기
                        val schemaFile = candidates.firstOrNull { it.exists() }
                            ?: throw IllegalStateException(
                                "Schema file not found: $relativePath\n" +
                                "Tried the following paths:\n" +
                                candidates.joinToString("\n") { "  - ${it.absolutePath}" } +
                                "\n\nPlease check:\n" +
                                "  1. Schema file exists at one of the paths above\n" +
                                "  2. Path is correct in testcontainers.postgres.schema-location"
                            )

                        val mountableFile = org.testcontainers.utility.MountableFile.forHostPath(schemaFile.absolutePath)
                        container.withCopyFileToContainer(
                            mountableFile,
                            "/docker-entrypoint-initdb.d/init-schema.sql"
                        )
                        println("📄 PostgreSQL Primary: Schema loaded from project: ${schemaFile.absolutePath}")
                    }
                    schemaPath.startsWith("file:") -> {
                        // file: 프로토콜 - 파일 시스템 절대 경로
                        val filePath = schemaPath.removePrefix("file:")
                        val mountableFile = org.testcontainers.utility.MountableFile.forHostPath(filePath)
                        container.withCopyFileToContainer(
                            mountableFile,
                            "/docker-entrypoint-initdb.d/init-schema.sql"
                        )
                        println("📄 PostgreSQL Primary: Schema loaded from file: $filePath")
                    }
                    else -> {
                        // classpath: 프로토콜 또는 프로토콜 없음 - classpath 리소스에서 읽음
                        val cleanPath = schemaPath.removePrefix("classpath:")
                        container.withInitScript(cleanPath)
                        println("📄 PostgreSQL Primary: Schema loaded from classpath: $cleanPath")
                    }
                }
            }

            // 컨테이너 시작
            container.start()
            println("✅ PostgreSQL Primary container started and ready (${container.jdbcUrl})")
        } else {
            println("🔍 [DEBUG] Container already running - schema setup skipped!")
            println("✅ PostgreSQL Primary container already running (${container.jdbcUrl})")
        }

        postgres.schemaLocation?.let {
            println("🔍 [DEBUG] After setup - schemaLocation was: $it")
        } ?: println("🔍 [DEBUG] After setup - schemaLocation was NULL")

        return container
    }

    /**
     * PostgreSQL Replica 컨테이너 Bean
     *
     * **단일 컨테이너 모드:**
     * - replica-enabled 설정과 관계없이 항상 Primary 컨테이너를 반환
     * - Primary와 Replica DataSource가 동일한 PostgreSQL 인스턴스를 참조
     * - 라우팅 로직은 정상 작동하지만 물리적으로는 같은 DB 사용
     *
     * **장점:**
     * - 빠른 테스트 실행 (컨테이너 1개만)
     * - 설정 간소화
     * - @Transactional(readOnly) 라우팅 로직 테스트 가능
     *
     * **이유:**
     * - Testcontainers 환경에서 실제 Streaming Replication 구현은 복잡함
     * - 대부분의 통합 테스트는 라우팅 로직만 검증하면 충분
     * - 실제 복제 지연(lag)이나 failover 테스트가 필요한 경우는 드뭄
     *
     * **향후 계획:**
     * - v2.0에서 실제 Streaming Replication 지원 예정
     * - 옵션으로 선택 가능하도록 구현 (streaming-replication: true)
     */
    @Bean
    @ConditionalOnProperty(prefix = "testcontainers.postgres", name = ["replica-enabled"], matchIfMissing = true)
    fun postgresReplicaContainer(
        postgresContainer: PostgreSQLContainer<*>,
    ): PostgreSQLContainer<*> {
        println("📄 PostgreSQL Replica: Using same container as Primary (single container mode)")
        println("   - Both MASTER and REPLICA DataSources will point to the same PostgreSQL instance")
        println("   - Routing logic will work, but physically using one database")
        println("   - This is sufficient for testing routing behavior")

        return postgresContainer
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
     *
     * **자동 토픽 생성 지원:**
     * - autoCreateTopics=true: Producer가 없는 토픽에 메시지 보낼 때 자동 생성
     * - topics 리스트: 사전 정의 토픽을 컨테이너 시작 시 생성
     */
    @Bean
    @ConditionalOnProperty(prefix = "testcontainers.kafka", name = ["enabled"], matchIfMissing = true)
    fun kafkaContainer(): KafkaContainer {
        println("📄 Kafka: Using shared singleton container")
        val container = SharedContainers.kafkaContainer

        // 사전 정의 토픽 생성
        if (properties.kafka.topics.isNotEmpty()) {
            createPredefinedTopics(container, properties.kafka.topics)
        }

        return container
    }

    /**
     * Kafka AdminClient를 사용하여 사전 정의된 토픽을 생성합니다.
     *
     * @param kafkaContainer Kafka 컨테이너
     * @param topics 생성할 토픽 목록
     */
    private fun createPredefinedTopics(
        kafkaContainer: KafkaContainer,
        topics: List<TestcontainersProperties.KafkaTopicConfig>,
    ) {
        val adminClient = org.apache.kafka.clients.admin.AdminClient.create(
            mapOf(
                org.apache.kafka.clients.admin.AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to kafkaContainer.bootstrapServers
            )
        )

        try {
            val newTopics = topics.map { topic ->
                org.apache.kafka.clients.admin.NewTopic(
                    topic.name,
                    topic.partitions,
                    topic.replicationFactor
                ).configs(topic.config)
            }

            adminClient.createTopics(newTopics).all().get(30, java.util.concurrent.TimeUnit.SECONDS)

            println("✅ Kafka predefined topics created:")
            topics.forEach { topic ->
                println("   - ${topic.name} (partitions=${topic.partitions}, replication-factor=${topic.replicationFactor})")
            }
        } catch (e: Exception) {
            when (e) {
                is org.apache.kafka.common.errors.TopicExistsException -> {
                    println("⚠️  Some topics already exist, skipping...")
                }
                else -> {
                    println("⚠️  Failed to create predefined topics: ${e.message}")
                }
            }
        } finally {
            adminClient.close()
        }
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

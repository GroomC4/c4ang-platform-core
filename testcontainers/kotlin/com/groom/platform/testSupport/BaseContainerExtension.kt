package com.groom.platform.testSupport

import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.testcontainers.containers.DockerComposeContainer
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.io.File
import java.time.Duration

/**
 * 모든 통합 테스트에서 공유되는 컨테이너를 관리하는 Base Extension
 *
 * - Docker Compose: PostgreSQL (Primary/Replica), Redis
 * - TestContainers: Kafka, Schema Registry (동적 포트 지원)
 */
abstract class BaseContainerExtension : BeforeAllCallback {
    companion object {
        @Volatile
        private var initialized = false

        private lateinit var composeContainer: DockerComposeContainer<*>
        private lateinit var kafkaContainer: KafkaContainer
        private lateinit var schemaRegistryContainer: GenericContainer<*>
        private val network = Network.newNetwork()

        private const val POSTGRES_MASTER_SERVICE = "test-postgres-primary"
        private const val POSTGRES_REPLICA_SERVICE = "test-postgres-replica"
        private const val REDIS_SERVICE = "test-redis"
        private const val POSTGRES_PORT = 5432
        private const val REDIS_PORT = 6379

        /**
         * Primary 데이터베이스 JDBC URL을 반환합니다.
         */
        @JvmStatic
        fun getPrimaryJdbcUrl(): String {
            ensureInitialized()
            val host = composeContainer.getServiceHost(POSTGRES_MASTER_SERVICE, POSTGRES_PORT)
            val port = composeContainer.getServicePort(POSTGRES_MASTER_SERVICE, POSTGRES_PORT)
            return "jdbc:postgresql://$host:$port/groom"
        }

        /**
         * Replica 데이터베이스 JDBC URL을 반환합니다.
         */
        @JvmStatic
        fun getReplicaJdbcUrl(): String {
            ensureInitialized()
            val host = composeContainer.getServiceHost(POSTGRES_REPLICA_SERVICE, POSTGRES_PORT)
            val port = composeContainer.getServicePort(POSTGRES_REPLICA_SERVICE, POSTGRES_PORT)
            return "jdbc:postgresql://$host:$port/groom"
        }

        /**
         * Redis 호스트를 반환합니다.
         */
        @JvmStatic
        fun getRedisHost(): String {
            ensureInitialized()
            return composeContainer.getServiceHost(REDIS_SERVICE, REDIS_PORT)
        }

        /**
         * Redis 포트를 반환합니다.
         */
        @JvmStatic
        fun getRedisPort(): Int {
            ensureInitialized()
            return composeContainer.getServicePort(REDIS_SERVICE, REDIS_PORT)
        }

        /**
         * Kafka Bootstrap Servers를 반환합니다.
         * TestContainers KafkaContainer가 자동으로 올바른 ADVERTISED_LISTENERS를 설정합니다.
         */
        @JvmStatic
        fun getKafkaBootstrapServers(): String {
            ensureInitialized()
            return kafkaContainer.bootstrapServers
        }

        /**
         * Schema Registry URL을 반환합니다.
         */
        @JvmStatic
        fun getSchemaRegistryUrl(): String {
            ensureInitialized()
            return "http://${schemaRegistryContainer.host}:${schemaRegistryContainer.getMappedPort(8081)}"
        }

        private fun ensureInitialized() {
            if (!initialized) {
                throw IllegalStateException("Container has not been initialized. Ensure tests use @IntegrationTest annotation.")
            }
        }

        /**
         * Compose 파일 경로를 해석합니다.
         */
        @JvmStatic
        protected fun resolveComposeFile(relativePath: String): File {
            val currentDir = File(System.getProperty("user.dir"))
            val candidates =
                listOf(
                    // 직접 지정된 경로
                    File(relativePath),
                    // 현재 디렉토리 기준
                    File(currentDir, relativePath),
                    // 부모 디렉토리 기준 (e-commerce 모듈에서 실행한 경우)
                    File(currentDir.parentFile, relativePath),
                )

            return candidates.firstOrNull { it.exists() }
                ?: throw IllegalStateException(
                    "Docker Compose file not found: $relativePath\n" +
                        "Current dir: ${currentDir.absolutePath}\n" +
                        "Searched in: ${candidates.joinToString { it.absolutePath }}",
                )
        }
    }

    /**
     * 각 서비스에서 override하여 자신의 compose 파일 경로를 제공합니다.
     */
    abstract fun getComposeFile(): File

    /**
     * 스키마 파일 경로를 제공합니다. (optional)
     * 기본값은 null이며, 각 서비스에서 필요 시 override합니다.
     */
    open fun getSchemaFile(): File? = null

    override fun beforeAll(context: ExtensionContext) {
        synchronized(BaseContainerExtension::class.java) {
            if (!initialized) {
                println("🚀 Starting shared containers for integration tests...")

                // 1. Kafka 컨테이너 시작 (TestContainers가 자동으로 ADVERTISED_LISTENERS 설정)
                println("📦 Starting Kafka container...")
                kafkaContainer =
                    KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.1"))
                        .withNetwork(network)
                        .withNetworkAliases("kafka")
                        .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "true")

                kafkaContainer.start()
                println("✅ Kafka started: ${kafkaContainer.bootstrapServers}")

                // 2. Schema Registry 컨테이너 시작
                println("📦 Starting Schema Registry container...")
                schemaRegistryContainer =
                    GenericContainer(DockerImageName.parse("confluentinc/cp-schema-registry:7.5.1"))
                        .withNetwork(network)
                        .withExposedPorts(8081)
                        .withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
                        .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "PLAINTEXT://kafka:9092")
                        .withEnv("SCHEMA_REGISTRY_LISTENERS", "http://0.0.0.0:8081")
                        .waitingFor(Wait.forHttp("/subjects").forStatusCode(200))
                        .dependsOn(kafkaContainer)

                schemaRegistryContainer.start()
                println("✅ Schema Registry started: http://${schemaRegistryContainer.host}:${schemaRegistryContainer.getMappedPort(8081)}")

                // 3. Docker Compose 시작 (PostgreSQL, Redis)
                println("📦 Starting Docker Compose (PostgreSQL, Redis)...")
                val composeFile = getComposeFile()
                val schemaFile = getSchemaFile()

                // 환경 변수 설정
                val envVars = mutableMapOf<String, String>()
                envVars["INFRA_CONFIG_PATH"] = composeFile.parentFile.parentFile.parentFile.absolutePath

                if (schemaFile != null && schemaFile.exists()) {
                    envVars["SCHEMA_PATH"] = schemaFile.absolutePath
                }

                composeContainer =
                    DockerComposeContainer(composeFile)
                        .withExposedService(
                            POSTGRES_MASTER_SERVICE,
                            POSTGRES_PORT,
                            Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(60)),
                        ).withExposedService(
                            POSTGRES_REPLICA_SERVICE,
                            POSTGRES_PORT,
                            Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(60)),
                        ).withExposedService(
                            REDIS_SERVICE,
                            REDIS_PORT,
                            Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(30)),
                        ).withOptions("--compatibility")
                        .withEnv(envVars)

                composeContainer.start()
                initialized = true

                // JVM 종료 시 컨테이너 정리
                Runtime.getRuntime().addShutdownHook(
                    Thread {
                        println("🛑 Stopping shared containers...")
                        composeContainer.stop()
                        schemaRegistryContainer.stop()
                        kafkaContainer.stop()
                        network.close()
                    },
                )

                println("✅ All containers started successfully")
                println("📍 Primary DB: ${getPrimaryJdbcUrl()}")
                println("📍 Replica DB: ${getReplicaJdbcUrl()}")
                println("📍 Redis: ${getRedisHost()}:${getRedisPort()}")
                println("📍 Kafka: ${getKafkaBootstrapServers()}")
                println("📍 Schema Registry: ${getSchemaRegistryUrl()}")
            }
        }
    }
}

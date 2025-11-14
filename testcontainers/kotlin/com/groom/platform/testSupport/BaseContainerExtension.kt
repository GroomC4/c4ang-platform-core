package com.groom.platform.testSupport

import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.testcontainers.containers.DockerComposeContainer
import org.testcontainers.containers.wait.strategy.Wait
import java.io.File
import java.time.Duration

/**
 * 모든 통합 테스트에서 공유되는 Docker Compose 컨테이너를 관리하는 Base Extension
 *
 * 각 서비스는 이 클래스를 상속받아 자신의 compose 파일 경로를 제공합니다.
 *
 * 사용 예시:
 * ```kotlin
 * class StoreServiceContainerExtension : BaseContainerExtension() {
 *     override fun getComposeFile(): File {k
 *         return resolveComposeFile("../c4ang-infra/docker-compose/test/docker-compose-integration-test.yml")
 *     }
 * }
 * ```
 */
abstract class BaseContainerExtension : BeforeAllCallback {
    companion object {
        @Volatile
        private var initialized = false

        private lateinit var composeContainer: DockerComposeContainer<*>

        private const val POSTGRES_MASTER_SERVICE = "test-postgres-primary"
        private const val POSTGRES_REPLICA_SERVICE = "test-postgres-replica"
        private const val REDIS_SERVICE = "test-redis"
        private const val KAFKA_SERVICE = "test-kafka"
        private const val POSTGRES_PORT = 5432
        private const val REDIS_PORT = 6379
        private const val KAFKA_PORT = 9092

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
         */
        @JvmStatic
        fun getKafkaBootstrapServers(): String {
            ensureInitialized()
            val host = composeContainer.getServiceHost(KAFKA_SERVICE, KAFKA_PORT)
            val port = composeContainer.getServicePort(KAFKA_SERVICE, KAFKA_PORT)
            return "$host:$port"
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
                println("🚀 Starting shared Docker Compose container for integration tests...")

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
                        ).withExposedService(
                            KAFKA_SERVICE,
                            KAFKA_PORT,
                            Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(90)),
                        ).withOptions("--compatibility")
                        .withEnv(envVars)

                composeContainer.start()
                initialized = true

                // JVM 종료 시 컨테이너 정리
                Runtime.getRuntime().addShutdownHook(
                    Thread {
                        println("🛑 Stopping shared Docker Compose container...")
                        composeContainer.stop()
                    },
                )

                println("✅ Shared Docker Compose container started successfully")
                println("📍 Primary DB: ${getPrimaryJdbcUrl()}")
                println("📍 Replica DB: ${getReplicaJdbcUrl()}")
                println("📍 Redis: ${getRedisHost()}:${getRedisPort()}")
                println("📍 Kafka: ${getKafkaBootstrapServers()}")
            }
        }
    }
}

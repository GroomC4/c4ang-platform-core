package com.groom.platform.infrastructure

import com.groom.platform.autoconfigure.PlatformProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.SpringApplication
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.env.EnvironmentPostProcessor
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.Profiles
import org.springframework.core.io.ClassPathResource
import java.io.File

/**
 * Local 프로필에서 Docker Compose 인프라를 자동으로 시작하고 동적 포트를 주입하는 EnvironmentPostProcessor
 *
 * 이 클래스는 애플리케이션 시작 시 다음을 수행합니다:
 * 1. local 프로필 체크
 * 2. Docker Compose 인프라 시작 (postgres-primary, postgres-replica, redis, kafka)
 * 3. 컨테이너의 동적 포트 조회
 * 4. Spring 환경 변수에 동적 포트 주입
 *
 * **주입되는 프로퍼티:**
 * - spring.datasource.master.url
 * - spring.datasource.master.username
 * - spring.datasource.master.password
 * - spring.datasource.replica.url
 * - spring.datasource.replica.username
 * - spring.datasource.replica.password
 * - spring.data.redis.host
 * - spring.data.redis.port
 * - spring.kafka.bootstrap-servers
 *
 * **동작 조건:**
 * - spring.profiles.active=local
 * - platform.infrastructure.docker-compose-enabled=true (기본값)
 */
class LocalInfraEnvironmentPostProcessor : EnvironmentPostProcessor {

    private val logger = KotlinLogging.logger {}

    companion object {
        private const val COMPOSE_FILE = "docker-compose/infrastructure.yml"
    }

    override fun postProcessEnvironment(environment: ConfigurableEnvironment, application: SpringApplication) {
        // local 프로필 체크
        if (!environment.acceptsProfiles(Profiles.of("local"))) {
            return
        }

        try {
            // PlatformProperties 바인딩
            val properties = Binder.get(environment)
                .bindOrCreate("platform", PlatformProperties::class.java)

            // Docker Compose 비활성화 체크
            if (!properties.infrastructure.dockerComposeEnabled) {
                logger.info { "Docker Compose is disabled (platform.infrastructure.docker-compose-enabled=false)" }
                return
            }

            logger.info { "🚀 Starting Local Infrastructure (Docker Compose)..." }

            // Docker Compose 파일 경로 찾기
            val composeFile = findComposeFile()
            if (!composeFile.exists()) {
                throw IllegalStateException("Docker Compose file not found: ${composeFile.absolutePath}")
            }

            // Docker Compose Manager 생성 및 시작
            val manager = DockerComposeManager(composeFile, properties.infrastructure)
            manager.startIfNotRunning()

            // 헬스체크 대기
            manager.waitForHealthy(properties.infrastructure.healthCheckTimeout)

            // 동적 포트 조회 및 Spring 환경에 주입
            val dynamicProperties = buildDynamicProperties(manager, properties)
            environment.propertySources.addFirst(
                MapPropertySource("dockerComposeProperties", dynamicProperties)
            )

            logger.info { "✅ Local Infrastructure is ready!" }
            logger.info { "   - PostgreSQL Primary: ${dynamicProperties["spring.datasource.master.url"]}" }
            logger.info { "   - PostgreSQL Replica: ${dynamicProperties["spring.datasource.replica.url"]}" }
            if (properties.infrastructure.redis.enabled) {
                logger.info { "   - Redis: ${dynamicProperties["spring.data.redis.host"]}:${dynamicProperties["spring.data.redis.port"]}" }
            }
            if (properties.infrastructure.kafka.enabled) {
                logger.info { "   - Kafka: ${dynamicProperties["spring.kafka.bootstrap-servers"]}" }
            }

        } catch (e: Exception) {
            logger.error(e) { "Failed to start local infrastructure" }
            throw IllegalStateException("Failed to start local infrastructure. Please check Docker is running.", e)
        }
    }

    /**
     * Docker Compose 파일 찾기
     *
     * 1. 클래스패스 리소스 (JAR 내부)
     * 2. 파일 시스템 (개발 환경)
     */
    private fun findComposeFile(): File {
        // 클래스패스에서 찾기
        val resource = ClassPathResource(COMPOSE_FILE)
        if (resource.exists()) {
            // 임시 파일로 추출 (JAR 내부 리소스는 직접 참조 불가)
            val tempFile = File.createTempFile("infrastructure", ".yml")
            tempFile.deleteOnExit()
            resource.inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            return tempFile
        }

        // 파일 시스템에서 찾기 (개발 환경)
        val baseDir = File(System.getProperty("user.dir"))
        val candidates = listOf(
            baseDir.resolve("c4ang-platform-core/platform-core/src/main/resources/$COMPOSE_FILE"),
            baseDir.resolve("platform-core/src/main/resources/$COMPOSE_FILE"),
            baseDir.resolve("src/main/resources/$COMPOSE_FILE")
        )

        return candidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException(
                "Docker Compose file not found. Tried:\n" +
                    candidates.joinToString("\n") { "  - ${it.absolutePath}" }
            )
    }

    /**
     * Docker Compose 컨테이너의 동적 포트를 조회하여 Spring 프로퍼티 맵 생성
     */
    private fun buildDynamicProperties(
        manager: DockerComposeManager,
        properties: PlatformProperties
    ): Map<String, Any> {
        val props = mutableMapOf<String, Any>()

        // PostgreSQL
        if (properties.infrastructure.postgres.enabled) {
            val primaryPort = manager.getServicePort("postgres-primary", 5432)
            val replicaPort = manager.getServicePort("postgres-replica", 5432)

            props["spring.datasource.master.url"] =
                "jdbc:postgresql://localhost:$primaryPort/${properties.infrastructure.postgres.database}"
            props["spring.datasource.master.username"] = properties.infrastructure.postgres.username
            props["spring.datasource.master.password"] = properties.infrastructure.postgres.password
            props["spring.datasource.master.driver-class-name"] = "org.postgresql.Driver"

            props["spring.datasource.replica.url"] =
                "jdbc:postgresql://localhost:$replicaPort/${properties.infrastructure.postgres.database}"
            props["spring.datasource.replica.username"] = properties.infrastructure.postgres.username
            props["spring.datasource.replica.password"] = properties.infrastructure.postgres.password
            props["spring.datasource.replica.driver-class-name"] = "org.postgresql.Driver"
        }

        // Redis
        if (properties.infrastructure.redis.enabled) {
            val redisPort = manager.getServicePort("redis", 6379)
            props["spring.data.redis.host"] = "localhost"
            props["spring.data.redis.port"] = redisPort
        }

        // Kafka
        if (properties.infrastructure.kafka.enabled) {
            val kafkaPort = manager.getServicePort("kafka", 9092)
            props["spring.kafka.bootstrap-servers"] = "localhost:$kafkaPort"
        }

        return props
    }
}

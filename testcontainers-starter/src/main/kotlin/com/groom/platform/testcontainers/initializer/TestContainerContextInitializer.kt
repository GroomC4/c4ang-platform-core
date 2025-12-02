package com.groom.platform.testcontainers.initializer

import com.groom.platform.testcontainers.autoconfigure.TestcontainersProperties
import com.groom.platform.testcontainers.container.SharedContainers
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext

/**
 * Testcontainers를 위한 ApplicationContext 초기화
 *
 * 이 Initializer는 @IntegrationTest 어노테이션과 함께 사용되며,
 * Spring ApplicationContext가 시작되기 전에 실행됩니다.
 *
 * **주요 역할:**
 * - 테스트 프로파일 활성화 확인
 * - Testcontainers 시작 및 동적 프로퍼티 주입
 * - Redis, Kafka, Schema Registry URL 주입
 *
 * **주입되는 프로퍼티:**
 * - spring.data.redis.host (Redis 활성화 시)
 * - spring.data.redis.port (Redis 활성화 시)
 * - spring.kafka.bootstrap-servers (Kafka 활성화 시)
 * - spring.kafka.properties.schema.registry.url (Schema Registry 활성화 시)
 *
 * **동작 순서:**
 * 1. TestContainerContextInitializer 실행 (이 클래스)
 * 2. Kafka/Schema Registry 컨테이너 시작 및 프로퍼티 주입
 * 3. TestcontainersAutoConfiguration 로드
 * 4. SharedContainers 싱글톤에서 컨테이너 가져오기
 * 5. TestDataSourceAutoConfiguration 로드
 * 6. Spring ApplicationContext 초기화 완료
 *
 * **사용법:**
 * ```kotlin
 * @IntegrationTest  // 자동으로 이 Initializer 적용
 * class MyIntegrationTest {
 *     // ...
 * }
 * ```
 *
 * 또는 직접 지정:
 * ```kotlin
 * @SpringBootTest
 * @ContextConfiguration(initializers = [TestContainerContextInitializer::class])
 * class MyIntegrationTest {
 *     // ...
 * }
 * ```
 */
class TestContainerContextInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {

    override fun initialize(applicationContext: ConfigurableApplicationContext) {
        val environment = applicationContext.environment

        // 로깅 (디버깅용)
        val activeProfiles = environment.activeProfiles.joinToString(", ")
        println("🔍 TestContainerContextInitializer - Active profiles: $activeProfiles")

        // TestcontainersProperties 바인딩
        val properties = try {
            Binder.get(environment)
                .bindOrCreate("testcontainers", TestcontainersProperties::class.java)
        } catch (e: Exception) {
            println("⚠️  Failed to bind TestcontainersProperties, using defaults: ${e.message}")
            TestcontainersProperties()
        }

        val dynamicProperties = mutableListOf<String>()

        // Redis 프로퍼티 주입
        if (properties.redis.enabled) {
            try {
                val redis = SharedContainers.redisContainer
                val redisHost = redis.host
                val redisPort = redis.getMappedPort(6379)
                dynamicProperties.add("spring.data.redis.host=$redisHost")
                dynamicProperties.add("spring.data.redis.port=$redisPort")
                println("✅ Redis: $redisHost:$redisPort")
            } catch (e: Exception) {
                println("⚠️  Failed to start Redis container: ${e.message}")
            }
        }

        // Kafka 프로퍼티 주입
        if (properties.kafka.enabled) {
            try {
                val kafka = SharedContainers.kafkaContainer
                val bootstrapServers = kafka.bootstrapServers
                dynamicProperties.add("spring.kafka.bootstrap-servers=$bootstrapServers")
                println("✅ Kafka bootstrap-servers: $bootstrapServers")

                // Schema Registry 프로퍼티 주입 (Kafka가 활성화된 경우에만)
                if (properties.schemaRegistry.enabled) {
                    val schemaRegistry = SharedContainers.schemaRegistryContainer
                    val schemaRegistryUrl = "http://${schemaRegistry.host}:${schemaRegistry.getMappedPort(8081)}"
                    dynamicProperties.add("spring.kafka.properties.schema.registry.url=$schemaRegistryUrl")
                    println("✅ Schema Registry URL: $schemaRegistryUrl")
                }
            } catch (e: Exception) {
                println("⚠️  Failed to start Kafka/Schema Registry containers: ${e.message}")
            }
        }

        // 동적 프로퍼티 적용
        if (dynamicProperties.isNotEmpty()) {
            TestPropertyValues.of(dynamicProperties).applyTo(applicationContext)
            println("🔍 TestContainerContextInitializer - Dynamic properties applied: ${dynamicProperties.size}")
        }

        println("🔍 TestContainerContextInitializer - Testcontainers will be started by AutoConfiguration")
    }
}

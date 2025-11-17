package com.groom.platform.testcontainers.initializer

import com.groom.platform.testcontainers.container.SharedContainers
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.test.context.support.TestPropertySourceUtils

/**
 * Testcontainers 환경의 동적 프로퍼티를 Spring ApplicationContext에 주입합니다.
 *
 * 주입되는 프로퍼티:
 * - Kafka Bootstrap Servers
 *
 * PostgreSQL과 Redis는 TestDataSourceAutoConfiguration에서 자동으로 구성됩니다.
 *
 * **사용법:**
 * 일반적으로 @SpringBootTest만 사용하면 자동으로 작동하므로 직접 사용할 필요 없습니다.
 * 특별한 경우에만 @ContextConfiguration(initializers = [TestContainerContextInitializer::class])를 사용하세요.
 */
class TestContainerContextInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
    override fun initialize(applicationContext: ConfigurableApplicationContext) {
        try {
            // SharedContainers에서 컨테이너 정보를 가져와서 Spring 프로퍼티에 등록
            val kafkaBootstrapServers = SharedContainers.kafkaContainer.bootstrapServers
            val schemaRegistryUrl =
                "http://${SharedContainers.schemaRegistryContainer.host}:" +
                "${SharedContainers.schemaRegistryContainer.getMappedPort(8081)}"

            val properties =
                arrayOf(
                    "KAFKA_BOOTSTRAP_SERVERS=$kafkaBootstrapServers",
                    "kafka.bootstrap-servers=$kafkaBootstrapServers",
                    "spring.kafka.bootstrap-servers=$kafkaBootstrapServers",
                    "SCHEMA_REGISTRY_URL=$schemaRegistryUrl",
                    "kafka.schema-registry.url=$schemaRegistryUrl",
                )

            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(applicationContext, *properties)

            println("✅ Test container properties configured:")
            println("   - Kafka: $kafkaBootstrapServers")
            println("   - Schema Registry: $schemaRegistryUrl")
        } catch (e: Exception) {
            // 컨테이너가 아직 초기화되지 않은 경우, 로그만 출력하고 넘어감
            println("⚠️ Containers not yet initialized, properties will be set when containers start")
        }
    }
}

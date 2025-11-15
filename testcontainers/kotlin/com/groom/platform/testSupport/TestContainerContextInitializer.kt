package com.groom.platform.testSupport

import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.test.context.support.TestPropertySourceUtils

/**
 * Testcontainers 환경의 동적 프로퍼티를 Spring ApplicationContext에 주입합니다.
 *
 * 주입되는 프로퍼티:
 * - Kafka Bootstrap Servers
 * - Schema Registry URL
 *
 * PostgreSQL과 Redis는 TestDataSourceConfig에서 Bean으로 직접 생성합니다.
 */
class TestContainerContextInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
    override fun initialize(applicationContext: ConfigurableApplicationContext) {
        try {
            // BaseContainerExtension에서 컨테이너 정보를 가져와서 Spring 프로퍼티에 등록
            val kafkaBootstrapServers = BaseContainerExtension.getKafkaBootstrapServers()
            val schemaRegistryUrl = BaseContainerExtension.getSchemaRegistryUrl()

            val properties =
                arrayOf(
                    "KAFKA_BOOTSTRAP_SERVERS=$kafkaBootstrapServers",
                    "kafka.bootstrap-servers=$kafkaBootstrapServers",
                    "kafka.schema-registry.url=$schemaRegistryUrl",
                )

            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(applicationContext, *properties)

            println("✅ Test container properties configured:")
            println("   - Kafka: $kafkaBootstrapServers")
            println("   - Schema Registry: $schemaRegistryUrl")
        } catch (e: Exception) {
            // 컨테이너가 아직 초기화되지 않은 경우, 로그만 출력하고 넘어감
            println("⚠️ Testcontainers not yet initialized, properties will be set when containers start")
        }
    }
}

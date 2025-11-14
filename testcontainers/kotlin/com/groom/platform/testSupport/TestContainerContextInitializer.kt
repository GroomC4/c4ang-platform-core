package com.groom.platform.testSupport

import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.test.context.support.TestPropertySourceUtils

/**
 * Testcontainers 환경의 동적 프로퍼티를 Spring ApplicationContext에 주입합니다.
 *
 * - PostgreSQL Primary/Replica 연결 정보
 * - Redis 연결 정보
 * - Kafka Bootstrap Servers
 */
class TestContainerContextInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
    override fun initialize(applicationContext: ConfigurableApplicationContext) {
        try {
            // BaseContainerExtension에서 컨테이너 정보를 가져와서 Spring 프로퍼티에 등록
            val properties =
                arrayOf(
                    "spring.datasource.url=${BaseContainerExtension.getPrimaryJdbcUrl()}",
                    "spring.datasource.username=test",
                    "spring.datasource.password=test",
                    "spring.r2dbc.url=${BaseContainerExtension.getReplicaJdbcUrl().replace("jdbc:", "r2dbc:")}",
                    "spring.r2dbc.username=test",
                    "spring.r2dbc.password=test",
                    "spring.data.redis.host=${BaseContainerExtension.getRedisHost()}",
                    "spring.data.redis.port=${BaseContainerExtension.getRedisPort()}",
                    "KAFKA_BOOTSTRAP_SERVERS=${BaseContainerExtension.getKafkaBootstrapServers()}",
                )

            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(applicationContext, *properties)

            println("✅ Test container properties configured:")
            println("   - Primary DB: ${BaseContainerExtension.getPrimaryJdbcUrl()}")
            println("   - Replica DB: ${BaseContainerExtension.getReplicaJdbcUrl()}")
            println("   - Redis: ${BaseContainerExtension.getRedisHost()}:${BaseContainerExtension.getRedisPort()}")
            println("   - Kafka: ${BaseContainerExtension.getKafkaBootstrapServers()}")
        } catch (e: Exception) {
            // 컨테이너가 아직 초기화되지 않은 경우, 로그만 출력하고 넘어감
            println("⚠️ Testcontainers not yet initialized, properties will be set when containers start")
        }
    }
}

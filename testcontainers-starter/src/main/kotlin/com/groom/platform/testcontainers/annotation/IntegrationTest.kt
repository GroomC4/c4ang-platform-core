package com.groom.platform.testcontainers.annotation

import com.groom.platform.testcontainers.initializer.TestContainerContextInitializer
import org.junit.jupiter.api.Tag
import org.springframework.test.context.ContextConfiguration

/**
 * 통합 테스트를 위한 공통 어노테이션
 *
 * **제공 기능:**
 * - Kafka 동적 포트 자동 주입 (TestContainerContextInitializer)
 * - Schema Registry 동적 포트 자동 주입
 * - integration-test 태그 추가
 *
 * **사용법 (IntegrationTestBase와 함께):**
 * ```kotlin
 * @IntegrationTest
 * @SpringBootTest(
 *     properties = [
 *         "testcontainers.postgres.enabled=true",
 *         "testcontainers.postgres.schema-location=project:sql/schema.sql",
 *     ]
 * )
 * abstract class IntegrationTestBase
 * ```
 *
 * 테스트 클래스는 IntegrationTestBase만 상속하면 됩니다:
 * ```kotlin
 * class MyRepositoryTest : IntegrationTestBase() {
 *     // @IntegrationTest가 자동으로 적용됨!
 * }
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Tag("integration-test")
@ContextConfiguration(initializers = [TestContainerContextInitializer::class])
annotation class IntegrationTest

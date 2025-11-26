package com.groom.platform.testcontainers.annotation

import com.groom.platform.testcontainers.initializer.TestContainerContextInitializer
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ContextConfiguration
import org.springframework.transaction.annotation.Transactional
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.CLASS

/**
 * 통합 테스트를 위한 복합 어노테이션
 *
 * 이 어노테이션은 다음을 자동으로 설정합니다:
 * - @SpringBootTest: Spring Boot 통합 테스트 환경
 * - @Transactional: 각 테스트 메서드를 트랜잭션으로 감싸고 롤백 (데이터 격리)
 * - @ContextConfiguration: Testcontainers 초기화
 *
 * **Testcontainers 자동 시작:**
 * - PostgreSQL (Primary + Replica)
 * - Redis
 * - Kafka (옵션)
 *
 * **DataSource 라우팅:**
 * - @Transactional(readOnly = false) → MASTER DB
 * - @Transactional(readOnly = true) → REPLICA DB
 *
 * **사용 예시:**
 * ```kotlin
 * @IntegrationTest
 * class UserServiceIntegrationTest {
 *
 *     @Autowired
 *     private lateinit var userService: UserService
 *
 *     @Test
 *     @Transactional(readOnly = false)  // MASTER DB 사용
 *     fun `사용자 생성 테스트`() {
 *         val user = userService.createUser("홍길동")
 *         assertNotNull(user.id)
 *     }
 *
 *     @Test
 *     @Transactional(readOnly = true)  // REPLICA DB 사용
 *     fun `사용자 조회 테스트`() {
 *         val users = userService.findAllUsers()
 *         assertTrue(users.isNotEmpty())
 *     }
 * }
 * ```
 *
 * **설정 커스터마이징 (application-test.yml):**
 * ```yaml
 * testcontainers:
 *   postgres:
 *     enabled: true
 *     replica-enabled: true
 *     schema-location: project:customer-api/src/main/resources/db/schema.sql
 *   redis:
 *     enabled: true
 *   kafka:
 *     enabled: false  # Kafka 불필요 시
 * ```
 *
 * @see SpringBootTest
 * @see Transactional
 * @see TestContainerContextInitializer
 */
@Target(CLASS)
@Retention(RUNTIME)
@MustBeDocumented
@SpringBootTest
@Transactional
@ContextConfiguration(initializers = [TestContainerContextInitializer::class])
annotation class IntegrationTest

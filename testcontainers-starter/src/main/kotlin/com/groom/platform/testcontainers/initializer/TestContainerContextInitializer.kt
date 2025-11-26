package com.groom.platform.testcontainers.initializer

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
 * - Testcontainers AutoConfiguration이 동작하도록 보장
 * - 추가적인 초기화 로직 (필요 시)
 *
 * **동작 순서:**
 * 1. TestContainerContextInitializer 실행 (이 클래스)
 * 2. TestcontainersAutoConfiguration 로드
 * 3. SharedContainers 싱글톤에서 컨테이너 가져오기
 * 4. TestDataSourceAutoConfiguration 로드
 * 5. Spring ApplicationContext 초기화 완료
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

        // 현재는 특별한 초기화 로직이 필요 없음
        // TestcontainersAutoConfiguration이 자동으로 컨테이너를 시작하고
        // TestDataSourceAutoConfiguration이 DataSource를 구성함

        // 로깅 (디버깅용)
        val activeProfiles = environment.activeProfiles.joinToString(", ")
        println("🔍 TestContainerContextInitializer - Active profiles: $activeProfiles")
        println("🔍 TestContainerContextInitializer - Testcontainers will be started by AutoConfiguration")
    }
}

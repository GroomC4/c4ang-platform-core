# 🚀 도메인 서버 Platform Core 통합 가이드

## 📋 목차
1. [빠른 시작 (5분)](#빠른-시작-5분)
2. [상세 설정](#상세-설정)
3. [테스트 환경 구성](#테스트-환경-구성)
4. [마이그레이션 체크리스트](#마이그레이션-체크리스트)
5. [트러블슈팅](#트러블슈팅)

---

## 빠른 시작 (5분)

### 1️⃣ 의존성 추가 (build.gradle.kts)

```kotlin
dependencies {
    // Platform Core DataSource (Production)
    implementation("com.groom.platform:datasource-starter:최신버전")

    // Platform Core TestContainers (Test)
    testImplementation("com.groom.platform:testcontainers-starter:최신버전")

    // PostgreSQL Driver
    runtimeOnly("org.postgresql:postgresql")
}
```

### 2️⃣ application.yml 설정

```yaml
spring:
  datasource:
    master:
      url: jdbc:postgresql://master-db:5432/mydb
      username: ${DB_USERNAME}
      password: ${DB_PASSWORD}
      hikari:
        maximum-pool-size: 10
        connection-timeout: 30000

    replica:
      url: jdbc:postgresql://replica-db:5432/mydb
      username: ${DB_USERNAME}
      password: ${DB_PASSWORD}
      hikari:
        maximum-pool-size: 20
        connection-timeout: 30000

# 옵션: Replica 비활성화 (개발 환경)
# replica를 설정하지 않으면 자동으로 master 사용
```

### 3️⃣ 테스트 설정 (application-test.yml)

```yaml
# TestContainers가 자동으로 구성하므로 DataSource 설정 불필요!
spring:
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true

testcontainers:
  postgres:
    enabled: true
    image: postgres:15-alpine
  redis:
    enabled: true
    image: redis:7-alpine
```

### 4️⃣ 사용 예제

```kotlin
@Service
class UserService {

    @Transactional(readOnly = false)  // Master DB 사용
    fun createUser(user: User): User {
        return userRepository.save(user)
    }

    @Transactional(readOnly = true)   // Replica DB 사용
    fun findUser(id: Long): User? {
        return userRepository.findById(id).orElse(null)
    }
}
```

### 5️⃣ 테스트 작성

```kotlin
@SpringBootTest
@IntegrationTest  // TestContainers 자동 시작
class UserServiceTest {

    @Autowired
    private lateinit var userService: UserService

    @Test
    fun `should create and find user`() {
        // Given
        val user = User(name = "테스트")

        // When
        val created = userService.createUser(user)
        val found = userService.findUser(created.id)

        // Then
        assertThat(found).isNotNull
        assertThat(found?.name).isEqualTo("테스트")
    }
}
```

**🎉 완료! 이제 Master-Replica 패턴과 TestContainers가 자동으로 동작합니다!**

---

## 상세 설정

### DataSource 커스터마이징

특별한 설정이 필요한 경우에만 사용:

```kotlin
@Configuration
class CustomDataSourceConfig {

    // Master만 커스터마이징 (Replica는 자동 구성)
    @Bean
    fun masterDataSource(): DataSource {
        return HikariDataSource().apply {
            jdbcUrl = "jdbc:postgresql://custom-host:5432/db"
            username = "custom-user"
            password = "custom-pass"
            maximumPoolSize = 50  // 커스텀 풀 사이즈
            connectionTimeout = 60000
            // 추가 설정...
        }
    }
}
```

### JPA 설정

```kotlin
@Configuration
@EnableJpaRepositories(
    basePackages = ["com.groom.myservice.repository"]
)
@EnableTransactionManagement
class JpaConfig {

    @Bean
    fun entityManagerFactory(
        dataSource: DataSource,  // Platform Core가 제공하는 Primary DataSource
        builder: EntityManagerFactoryBuilder
    ): LocalContainerEntityManagerFactoryBean {
        return builder
            .dataSource(dataSource)
            .packages("com.groom.myservice.entity")
            .persistenceUnit("default")
            .build()
    }

    @Bean
    fun transactionManager(
        @Qualifier("entityManagerFactory") entityManagerFactory: EntityManagerFactory
    ): PlatformTransactionManager {
        return JpaTransactionManager(entityManagerFactory)
    }
}
```

---

## 테스트 환경 구성

### 통합 테스트

```kotlin
@SpringBootTest
@IntegrationTest  // 이 어노테이션 하나로 모든 설정 완료!
@TestPropertySource(properties = [
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=true"
])
class IntegrationTest {
    // PostgreSQL, Redis 컨테이너 자동 시작
    // Master/Replica DataSource 자동 구성
}
```

### 레포지토리 테스트

```kotlin
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersAutoConfiguration::class)
class UserRepositoryTest {

    @Autowired
    private lateinit var userRepository: UserRepository

    @Test
    @Transactional
    @Rollback
    fun `should save and find user`() {
        // 테스트 코드
    }
}
```

### 커스텀 TestContainers 설정

```kotlin
@TestConfiguration
class CustomTestConfig {

    @Bean
    @Primary
    fun customPostgresContainer(): PostgreSQLContainer<*> {
        return PostgreSQLContainer("postgres:15-alpine").apply {
            withDatabaseName("testdb")
            withUsername("test")
            withPassword("test")
            withInitScript("init.sql")  // 초기화 스크립트
            start()
        }
    }
}
```

---

## 마이그레이션 체크리스트

### 기존 DataSource 설정 제거

```kotlin
// ❌ 제거해야 할 코드
@Configuration
class DataSourceConfig {
    @Bean
    fun dataSource(): DataSource { ... }

    @Bean
    fun masterDataSource(): DataSource { ... }

    @Bean
    fun replicaDataSource(): DataSource { ... }
}

// ✅ Platform Core가 자동으로 처리!
```

### application.yml 마이그레이션

**Before:**
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/mydb
    username: user
    password: pass
```

**After:**
```yaml
spring:
  datasource:
    master:
      url: jdbc:postgresql://master:5432/mydb
      username: user
      password: pass
    replica:  # 선택사항
      url: jdbc:postgresql://replica:5432/mydb
      username: user
      password: pass
```

### 테스트 코드 간소화

**Before:**
```kotlin
@SpringBootTest
@Testcontainers
class MyTest {
    companion object {
        @Container
        val postgres = PostgreSQLContainer("postgres:15")

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            // ... 복잡한 설정
        }
    }
}
```

**After:**
```kotlin
@SpringBootTest
@IntegrationTest  // 한 줄로 끝!
class MyTest {
    // 모든 설정 자동화
}
```

---

## 트러블슈팅

### 🔴 순환 참조 오류

**증상:**
```
The dependencies of some of the beans in the application context form a cycle
```

**해결:**
1. Platform Core 최신 버전 사용 (fix/datasource-circular-reference 브랜치 이후)
2. 커스텀 DataSource 설정 제거

### 🔴 TestContainers 시작 안 됨

**증상:**
```
Could not find a valid Docker environment
```

**해결:**
1. Docker Desktop 실행 확인
2. `@IntegrationTest` 어노테이션 확인
3. testcontainers-starter 의존성 확인

### 🔴 Replica로 라우팅 안 됨

**증상:**
- `@Transactional(readOnly = true)`인데도 Master 사용

**해결:**
1. `spring.datasource.replica` 설정 확인
2. Transaction 경계 확인 (Service 레이어에서 사용)
3. Debug 로그 활성화:
```yaml
logging:
  level:
    com.groom.platform.datasource: DEBUG
```

### 🔴 Connection Pool 부족

**증상:**
```
Connection is not available, request timed out
```

**해결:**
```yaml
spring:
  datasource:
    master:
      hikari:
        maximum-pool-size: 20  # 증가
        minimum-idle: 5
        connection-timeout: 60000  # 60초
    replica:
      hikari:
        maximum-pool-size: 30  # Read 트래픽이 많으면 더 크게
```

---

## 📚 참고 자료

### Platform Core 소스 코드
- Repository: `https://github.com/GroomC4/c4ang-platform-core`
- Branch: `fix/datasource-circular-reference`

### 관련 파일
- [DataSourceAutoConfiguration.kt](datasource-starter/src/main/kotlin/com/groom/platform/datasource/autoconfigure/DataSourceAutoConfiguration.kt)
- [DataSourceDefaultConfiguration.kt](datasource-starter/src/main/kotlin/com/groom/platform/datasource/autoconfigure/DataSourceDefaultConfiguration.kt)
- [TestDataSourceAutoConfiguration.kt](testcontainers-starter/src/main/kotlin/com/groom/platform/testcontainers/autoconfigure/TestDataSourceAutoConfiguration.kt)

### 예제 프로젝트
```bash
# Customer Service 예제 확인
git clone https://github.com/GroomC4/c4ang-customer-service
cd c4ang-customer-service
git checkout feature/platform-core-integration
```

---

## 💬 지원 및 문의

### 문제 발생 시
1. 이 가이드의 트러블슈팅 섹션 확인
2. Platform Core GitHub Issues 확인
3. Slack #platform-core 채널 문의

### 기여하기
- 버그 리포트: GitHub Issues
- 기능 제안: GitHub Discussions
- 코드 기여: Pull Request (main 브랜치)

---

**작성일**: 2024-11-19
**버전**: 1.0.0
**작성자**: Platform Core Team
# 공유 라이브러리 설계 기획안 실현 가능성 분석

## 현재 아키텍처 분석

### 배포 방식
```
✅ Git Submodule 사용 중
- c4ang-store-service에 c4ang-platform-core가 submodule로 포함됨
- 각 서비스가 동일한 방식으로 사용 중

❌ GitHub Package 미사용
- c4ang-platform-core에 build.gradle.kts 없음
- 컴파일된 아티팩트가 아닌 소스코드를 직접 공유
```

### 소스 참조 방식
```kotlin
// store-api/build.gradle.kts
sourceSets {
    test {
        kotlin {
            srcDir("../c4ang-platform-core/testcontainers/kotlin")
        }
    }
}
```

**의미:**
- 각 서비스가 platform-core의 Kotlin 소스를 **직접 컴파일**
- 별도의 JAR 파일이 아닌 소스코드 레벨 의존성
- 각 서비스의 Gradle 빌드 시점에 platform-core 코드가 함께 컴파일됨

### BaseContainerExtension 패턴
```kotlin
abstract class BaseContainerExtension : BeforeAllCallback {
    companion object {
        @Volatile
        private var initialized = false
        private lateinit var composeContainer: DockerComposeContainer<*>
        // ...
    }

    abstract fun getComposeFile(): File
    open fun getSchemaFile(): File? = null

    override fun beforeAll(context: ExtensionContext) {
        // 컨테이너 초기화 로직
    }
}
```

**특징:**
1. **JUnit Jupiter Extension** - Spring ApplicationContext 외부에서 동작
2. **Companion Object 패턴** - 정적 싱글톤으로 컨테이너 관리
3. **추상 메서드** - 각 서비스가 상속하여 구현
4. **beforeAll 콜백** - 테스트 시작 전 컨테이너 초기화

### TestContainerContextInitializer 패턴
```kotlin
class TestContainerContextInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
    override fun initialize(applicationContext: ConfigurableApplicationContext) {
        val kafkaBootstrapServers = BaseContainerExtension.getKafkaBootstrapServers()
        // Spring 프로퍼티에 동적 값 주입
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(...)
    }
}
```

**특징:**
1. **ApplicationContextInitializer** - Spring Context 초기화 시점에 실행
2. **정적 메서드 호출** - BaseContainerExtension의 companion object 메서드 사용
3. **프로퍼티 주입** - 동적으로 생성된 값을 Spring Environment에 추가

---

## 제안한 설계의 실현 가능성 분석

### ❌ 문제 1: ConfigurationProperties와 JUnit Extension의 타이밍 이슈

#### 제안한 설계
```kotlin
@ConfigurationProperties(prefix = "platform.test")
data class PlatformTestProperties(...)

class TestContainerContextInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
    override fun initialize(applicationContext: ConfigurableApplicationContext) {
        val properties = Binder.get(environment)
            .bindOrCreate("platform.test", PlatformTestProperties::class.java)

        BaseContainerExtension.initialize(properties)  // ❌ 문제!
    }
}
```

#### 왜 작동하지 않는가?

**실행 순서:**
```
1. @ExtendWith(ServiceContainerExtension::class)
   → JUnit이 Extension의 beforeAll() 호출
   → BaseContainerExtension.beforeAll() 실행
   → 이 시점에 getComposeFile()과 getSchemaFile() 필요!

2. @ContextConfiguration(initializers = [TestContainerContextInitializer::class])
   → Spring ApplicationContext 초기화 시작
   → TestContainerContextInitializer.initialize() 호출
   → 이 시점에 properties를 읽을 수 있음

❌ Extension은 Initializer보다 먼저 실행됨!
```

**근본적 문제:**
- `@ExtendWith`는 Spring과 무관하게 JUnit이 직접 실행
- `@ContextConfiguration`은 Spring TestContext Framework가 실행
- **JUnit Extension이 Spring Context보다 먼저 실행됨**
- Extension의 beforeAll()이 호출될 때는 아직 application-test.yml을 읽을 수 없음

#### 증명
```kotlin
// 현재 BaseContainerExtension.kt:170
override fun beforeAll(context: ExtensionContext) {
    val composeFile = getComposeFile()  // 추상 메서드 호출
    val schemaFile = getSchemaFile()    // 추상 메서드 호출
    // 이 시점에는 Spring이 시작되지 않았으므로 yml 프로퍼티 접근 불가!
}
```

---

### ⚠️ 문제 2: PlatformTestAutoConfiguration의 작동 조건

#### 제안한 설계
```kotlin
@Configuration
@EnableConfigurationProperties(PlatformTestProperties::class)
class PlatformTestAutoConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "platform.test", name = ["postgres-enabled"])
    fun postgresContainer(properties: PlatformTestProperties): PostgresTestContainer {
        return PostgresTestContainer(properties)
    }
}
```

#### 문제점

1. **@Configuration이 스캔되지 않음**
   - platform-core는 서비스의 `test` sourceSets에만 포함됨
   - 서비스의 컴포넌트 스캔 범위: `com.groom.{service}` 패키지
   - platform-core의 패키지: `com.groom.platform.testSupport`
   - **자동으로 스캔되지 않음!**

2. **수동 Import 필요**
   ```kotlin
   @IntegrationTest
   @Import(PlatformTestAutoConfiguration::class)  // 모든 테스트에 추가 필요
   class MyTest { ... }
   ```
   - 사용성 저하 (각 서비스가 명시적으로 Import 해야 함)

3. **Spring Bean으로 Container 관리의 한계**
   - 현재는 JUnit Extension이 컨테이너 라이프사이클 관리
   - Spring Bean으로 변경하면 JUnit Extension 패턴과 충돌
   - **전체 아키텍처 재설계 필요**

---

### ⚠️ 문제 3: PropertyBasedSchemaLoader의 실행 시점

#### 제안한 설계
```kotlin
class PropertyBasedSchemaLoader(
    private val properties: PlatformTestProperties
) : SchemaLoader {
    override fun loadSchema(): File? {
        val schemaPath = properties.schemaPath ?: return null
        // classpath: 또는 file: 프로토콜 처리
    }
}
```

#### 문제점

1. **properties를 어디서 받는가?**
   ```kotlin
   override fun beforeAll(context: ExtensionContext) {
       val schemaLoader = PropertyBasedSchemaLoader(properties)  // ❌ properties는 어디서?
       // Spring이 아직 시작되지 않았으므로 yml에서 읽을 수 없음
   }
   ```

2. **classpath: 프로토콜의 한계**
   - Docker Compose는 호스트 파일 시스템의 절대 경로 필요
   - 클래스패스 리소스는 JAR 내부에 있을 수 있음
   - 임시 파일로 복사하는 로직 필요 (제안에 포함되어 있음)
   - 하지만 이 역시 Spring ResourceLoader 필요 → Spring 시작 후에만 가능

---

### ❌ 문제 4: GitHub Packages 배포의 전제 조건

#### 제안한 설계
```kotlin
// testcontainers/build.gradle.kts (신규 파일)
plugins {
    kotlin("jvm") version "1.9.20"
    `maven-publish`
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.testcontainers:testcontainers:1.19.3")
    // ...
}
```

#### 문제점

1. **현재 구조와의 불일치**
   - 현재: 각 서비스가 소스코드를 직접 컴파일
   - 제안: JAR로 패키징하여 배포
   - **근본적으로 다른 접근 방식**

2. **버전 관리 복잡성**
   ```kotlin
   // 현재 방식 (Submodule)
   git submodule update  // 최신 코드 즉시 반영

   // 제안 방식 (GitHub Packages)
   testImplementation("com.groom.platform:platform-core:1.0.1")  // 버전 업데이트 필요
   git submodule update  // 여전히 필요 (소스 참조 방식 유지 시)
   ```

3. **의존성 전이 문제**
   - platform-core가 Spring Boot 의존성을 가짐
   - 서비스와 버전 충돌 가능성
   - 예: platform-core가 Spring Boot 3.3.4, 서비스가 3.4.0 사용 시

4. **기존 방식의 장점 상실**
   - 현재: 각 서비스가 자신의 Spring Boot 버전에 맞춰 컴파일
   - 제안: platform-core의 고정된 Spring Boot 버전 사용
   - **유연성 감소**

---

### ✅ 문제 5: 조건부 컨테이너 시작 - 가능하지만 제한적

#### 제안한 설계
```yaml
platform:
  test:
    postgres-enabled: true
    redis-enabled: false
    kafka-enabled: true
```

#### 실현 가능한 방식

**Option A: 환경 변수 사용 (가능)**
```kotlin
abstract class BaseContainerExtension : BeforeAllCallback {
    override fun beforeAll(context: ExtensionContext) {
        val postgresEnabled = System.getenv("POSTGRES_ENABLED")?.toBoolean() ?: true
        val redisEnabled = System.getenv("REDIS_ENABLED")?.toBoolean() ?: true

        if (postgresEnabled) {
            initializePostgres()
        }
        if (redisEnabled) {
            initializeRedis()
        }
    }
}
```

**사용법:**
```bash
POSTGRES_ENABLED=true REDIS_ENABLED=false ./gradlew test
```

**문제점:**
- yml 파일이 아닌 환경 변수 사용 (제안과 다름)
- IDE에서 실행 시 환경 변수 설정 필요
- 사용성 저하

**Option B: System Properties 사용 (가능)**
```kotlin
override fun beforeAll(context: ExtensionContext) {
    val postgresEnabled = System.getProperty("platform.test.postgres-enabled", "true").toBoolean()
}
```

**사용법:**
```kotlin
// build.gradle.kts
tasks.test {
    systemProperty("platform.test.postgres-enabled", "true")
    systemProperty("platform.test.redis-enabled", "false")
}
```

**문제점:**
- 여전히 yml이 아닌 Gradle 설정
- 각 서비스의 build.gradle.kts 수정 필요

---

## 근본적인 제약 사항

### 1. JUnit Extension의 실행 시점
```
JUnit Extension (@ExtendWith)
  ↓
  beforeAll() 호출
  ↓
  [이 시점에서 컨테이너 시작 필요]
  ↓
  Spring TestContext 초기화 시작
  ↓
  ApplicationContextInitializer 실행
  ↓
  [이 시점에서 yml 파일 읽기 가능]
  ↓
  Spring Bean 생성
  ↓
  @ConfigurationProperties 바인딩
```

**결론: JUnit Extension에서는 yml 프로퍼티를 읽을 수 없음**

### 2. Docker Compose의 파일 시스템 요구사항
- Docker Compose는 **호스트 파일 시스템의 절대 경로** 필요
- 클래스패스 리소스는 직접 마운트 불가
- 임시 파일 복사 필요 → 추가 복잡성

### 3. Testcontainers의 초기화 시점
- Testcontainers는 Spring 시작 **전에** 초기화되어야 함
- 이유: Spring DataSource Bean 생성 시 DB URL 필요
- 따라서 JUnit Extension 패턴 불가피

---

## 실현 가능한 대안 설계

### 대안 1: 환경 변수 기반 설정 (실현 가능 ✅)

#### 구조
```kotlin
// BaseContainerExtension.kt
companion object {
    private fun loadConfig(): ContainerConfig {
        return ContainerConfig(
            postgresEnabled = getEnvOrProperty("PLATFORM_TEST_POSTGRES_ENABLED", "true").toBoolean(),
            redisEnabled = getEnvOrProperty("PLATFORM_TEST_REDIS_ENABLED", "true").toBoolean(),
            kafkaEnabled = getEnvOrProperty("PLATFORM_TEST_KAFKA_ENABLED", "true").toBoolean(),
            schemaPath = getEnvOrProperty("PLATFORM_TEST_SCHEMA_PATH", null)
        )
    }

    private fun getEnvOrProperty(key: String, default: String?): String? {
        return System.getenv(key) ?: System.getProperty(key) ?: default
    }
}

data class ContainerConfig(
    val postgresEnabled: Boolean,
    val redisEnabled: Boolean,
    val kafkaEnabled: Boolean,
    val schemaPath: String?
)
```

#### 사용법
```kotlin
// 서비스의 build.gradle.kts
tasks.test {
    systemProperty("PLATFORM_TEST_POSTGRES_ENABLED", "true")
    systemProperty("PLATFORM_TEST_REDIS_ENABLED", "true")
    systemProperty("PLATFORM_TEST_KAFKA_ENABLED", "true")
    systemProperty("PLATFORM_TEST_SCHEMA_PATH", "store-api/sql/schema.sql")
}
```

#### 장점
✅ JUnit Extension 실행 시점에 읽기 가능
✅ yml 파일 없이도 설정 가능
✅ 환경별로 다른 설정 적용 가능 (CI/Local)

#### 단점
❌ yml이 아닌 System Properties 사용
❌ build.gradle.kts 수정 필요
❌ IDE 실행 설정에도 추가 필요

---

### 대안 2: Convention-Based 설정 (실현 가능 ✅)

#### 구조
```kotlin
abstract class BaseContainerExtension : BeforeAllCallback {

    // 각 서비스가 오버라이드 가능
    open fun getContainerConfig(): ContainerConfig {
        return ContainerConfig(
            postgresEnabled = true,
            redisEnabled = true,
            kafkaEnabled = true
        )
    }

    override fun beforeAll(context: ExtensionContext) {
        val config = getContainerConfig()

        if (config.postgresEnabled) {
            initializePostgres()
        }
        // ...
    }
}
```

```kotlin
// 서비스의 SharedContainerExtension
class SharedContainerExtension : BaseContainerExtension() {
    override fun getComposeFile(): File =
        resolveComposeFile("c4ang-platform-core/docker-compose/test/docker-compose-integration-test.yml")

    override fun getSchemaFile(): File =
        resolveComposeFile("store-api/sql/schema.sql")

    // 새로운 메서드
    override fun getContainerConfig(): ContainerConfig {
        return ContainerConfig(
            postgresEnabled = true,
            redisEnabled = true,
            kafkaEnabled = false  // Kafka 비활성화
        )
    }
}
```

#### 장점
✅ 타입 안전성
✅ IDE 자동완성 지원
✅ 컴파일 타임 검증
✅ 현재 패턴과 일관성 유지

#### 단점
❌ 여전히 코드 작성 필요 (yml 불가)
❌ 기존 방식과 크게 다르지 않음

---

### 대안 3: 스키마 경로 자동 탐색 (실현 가능 ✅✅✅)

#### 아이디어
yml 프로퍼티 대신 **Convention over Configuration** 원칙 적용

```kotlin
abstract class BaseContainerExtension : BeforeAllCallback {

    open fun getSchemaFile(): File? {
        // 1. 명시적으로 지정된 경로 우선
        val explicitPath = System.getProperty("platform.test.schema-path")
        if (explicitPath != null) {
            return File(explicitPath).takeIf { it.exists() }
        }

        // 2. Convention: {service-name}/sql/schema.sql
        val serviceName = detectServiceName()
        val conventionPaths = listOf(
            "$serviceName/sql/schema.sql",
            "$serviceName-api/sql/schema.sql",
            "src/test/resources/db/schema.sql",
            "sql/schema.sql"
        )

        return conventionPaths
            .map { resolveComposeFile(it) }
            .firstOrNull { it.exists() }
    }

    private fun detectServiceName(): String {
        // 현재 작업 디렉토리에서 서비스 이름 추론
        val currentDir = File(System.getProperty("user.dir"))
        return currentDir.name.replace("-service", "")
    }
}
```

#### 사용법
```
store-service/
├── store-api/
│   └── sql/
│       └── schema.sql  ← 자동 탐지!
└── c4ang-platform-core/ (submodule)
```

**각 서비스에서:**
```kotlin
// 더 이상 getSchemaFile() 오버라이드 불필요!
class SharedContainerExtension : BaseContainerExtension() {
    override fun getComposeFile(): File =
        resolveComposeFile("c4ang-platform-core/docker-compose/test/docker-compose-integration-test.yml")

    // getSchemaFile()은 자동으로 store-api/sql/schema.sql 탐지
}
```

#### 장점
✅ **코드 작성 불필요** - 진짜 간소화!
✅ Convention 기반 - 일관된 프로젝트 구조 유도
✅ 명시적 경로도 지원 (System Property로)
✅ 현재 아키텍처와 호환

#### 단점
⚠️ 디렉토리 구조 강제 (하지만 이미 대부분 일관됨)

---

### 대안 4: Compose 파일도 자동 탐색 (실현 가능 ✅✅✅)

#### 구조
```kotlin
abstract class BaseContainerExtension : BeforeAllCallback {

    open fun getComposeFile(): File {
        // 1. 명시적 경로
        val explicitPath = System.getProperty("platform.test.compose-path")
        if (explicitPath != null) {
            return File(explicitPath)
        }

        // 2. Convention: c4ang-platform-core/docker-compose/test/docker-compose-integration-test.yml
        return resolveComposeFile("c4ang-platform-core/docker-compose/test/docker-compose-integration-test.yml")
    }
}
```

#### 최종 결과
```kotlin
// 각 서비스에서 더 이상 Extension 클래스 작성 불필요!
// @IntegrationTest 어노테이션만 사용
@IntegrationTest
@SpringBootTest
class StoreServiceIntegrationTest {
    // 테스트 코드
}
```

**하지만 현실적으로:**
- @IntegrationTest 안에 @ExtendWith가 필요
- 따라서 최소한의 Extension 클래스는 여전히 필요

```kotlin
// 최소한의 Extension (모든 서비스가 동일)
class SharedContainerExtension : BaseContainerExtension() {
    // 모든 메서드가 기본 구현 사용
}
```

---

## 최종 실현 가능성 평가

### ❌ 불가능한 기획 요소

1. **yml 프로퍼티 기반 자원 활성화/비활성화**
   - 이유: JUnit Extension이 Spring보다 먼저 실행
   - 대안: System Properties 또는 코드 기반 설정

2. **yml에서 스키마 경로 지정**
   - 이유: 동일 (Extension 실행 시점에 yml 접근 불가)
   - 대안: System Properties 또는 Convention 기반 자동 탐색

3. **@ConfigurationProperties 기반 설정**
   - 이유: Spring Bean 초기화 시점 문제
   - 대안: 환경 변수 또는 System Properties

4. **GitHub Packages로 배포**
   - 이유: 현재 소스코드 직접 공유 방식과 근본적으로 다름
   - 영향: 버전 관리 복잡성, 의존성 충돌 위험
   - 대안: Git Submodule 유지 (현재 방식이 더 유연함)

### ⚠️ 가능하지만 제한적인 요소

1. **PropertyBasedSchemaLoader**
   - 가능 조건: System Property 사용 시
   - 제한: classpath: 프로토콜은 Spring 시작 후에만 가능

2. **조건부 컨테이너 시작**
   - 가능 조건: System Property 또는 코드 기반
   - 제한: yml 프로퍼티는 사용 불가

### ✅ 실현 가능한 요소

1. **Convention over Configuration**
   - 스키마 파일 자동 탐색: `{service}/sql/schema.sql`
   - Compose 파일 기본 경로: `c4ang-platform-core/docker-compose/test/...`

2. **System Properties 기반 오버라이드**
   ```properties
   platform.test.postgres-enabled=true
   platform.test.schema-path=custom/path/schema.sql
   ```

3. **타입 안전한 설정 클래스**
   ```kotlin
   data class ContainerConfig(
       val postgresEnabled: Boolean = true,
       val redisEnabled: Boolean = true,
       val kafkaEnabled: Boolean = true
   )
   ```

4. **Git Submodule 방식 유지**
   - 현재 방식이 이미 잘 작동함
   - 버전 충돌 없음
   - 각 서비스가 자신의 Spring Boot 버전으로 컴파일

---

## 권장 대안 설계

### 목표 재정의
기존: "yml 프로퍼티로 모든 것을 설정"
**개선: "Convention으로 기본 제공, System Property로 오버라이드 가능"**

### 구현 계획

#### Phase 1: Convention over Configuration (추천 ✅✅✅)

```kotlin
// BaseContainerExtension.kt
abstract class BaseContainerExtension : BeforeAllCallback {

    companion object {
        // 설정 로딩 (환경 변수 > System Property > 기본값)
        private fun loadConfig(): ContainerConfig {
            return ContainerConfig(
                postgresEnabled = getBooleanConfig("PLATFORM_POSTGRES_ENABLED", true),
                redisEnabled = getBooleanConfig("PLATFORM_REDIS_ENABLED", true),
                kafkaEnabled = getBooleanConfig("PLATFORM_KAFKA_ENABLED", true),
                replicaEnabled = getBooleanConfig("PLATFORM_REPLICA_ENABLED", false),
                schemaPath = getStringConfig("PLATFORM_SCHEMA_PATH")
            )
        }

        private fun getBooleanConfig(key: String, default: Boolean): Boolean {
            return System.getenv(key)?.toBoolean()
                ?: System.getProperty(key)?.toBoolean()
                ?: default
        }

        private fun getStringConfig(key: String): String? {
            return System.getenv(key) ?: System.getProperty(key)
        }
    }

    // Convention 기반 스키마 탐색
    open fun getSchemaFile(): File? {
        val config = loadConfig()

        // 1. 명시적 경로 우선
        if (config.schemaPath != null) {
            return File(config.schemaPath).takeIf { it.exists() }
        }

        // 2. Convention 경로 시도
        val serviceName = detectServiceName()
        val candidates = listOf(
            "$serviceName-api/sql/schema.sql",
            "$serviceName/sql/schema.sql",
            "sql/schema.sql"
        )

        return candidates
            .map { resolveComposeFile(it) }
            .firstOrNull { it.exists() }
    }

    private fun detectServiceName(): String {
        val currentDir = File(System.getProperty("user.dir"))
        return currentDir.name.replace(Regex("-(service|api)$"), "")
    }

    override fun beforeAll(context: ExtensionContext) {
        synchronized(BaseContainerExtension::class.java) {
            if (!initialized) {
                val config = loadConfig()

                println("🚀 Container Configuration:")
                println("   - PostgreSQL: ${if (config.postgresEnabled) "✅" else "❌"}")
                println("   - Redis: ${if (config.redisEnabled) "✅" else "❌"}")
                println("   - Kafka: ${if (config.kafkaEnabled) "✅" else "❌"}")

                if (config.kafkaEnabled) {
                    initializeKafka()
                }

                if (config.postgresEnabled || config.redisEnabled) {
                    initializeDockerCompose(config)
                }

                initialized = true
            }
        }
    }

    private fun initializeKafka() {
        // 기존 Kafka 초기화 로직
    }

    private fun initializeDockerCompose(config: ContainerConfig) {
        val composeFile = getComposeFile()
        val schemaFile = getSchemaFile()

        // 환경 변수 설정
        val envVars = mutableMapOf<String, String>()
        envVars["INFRA_CONFIG_PATH"] = composeFile.parentFile.parentFile.parentFile.absolutePath

        if (schemaFile != null && schemaFile.exists()) {
            envVars["SCHEMA_PATH"] = schemaFile.absolutePath
            println("   - Schema: ${schemaFile.absolutePath}")
        } else {
            println("   - Schema: Not found (will use existing DB)")
        }

        // 조건부 서비스 시작
        composeContainer = DockerComposeContainer(composeFile)

        if (config.postgresEnabled) {
            composeContainer.withExposedService(
                POSTGRES_MASTER_SERVICE,
                POSTGRES_PORT,
                Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(60))
            )

            if (config.replicaEnabled) {
                composeContainer.withExposedService(
                    POSTGRES_REPLICA_SERVICE,
                    POSTGRES_PORT,
                    Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(60))
                )
            }
        }

        if (config.redisEnabled) {
            composeContainer.withExposedService(
                REDIS_SERVICE,
                REDIS_PORT,
                Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(30))
            )
        }

        composeContainer
            .withOptions("--compatibility")
            .withEnv(envVars)
            .start()
    }
}

data class ContainerConfig(
    val postgresEnabled: Boolean,
    val redisEnabled: Boolean,
    val kafkaEnabled: Boolean,
    val replicaEnabled: Boolean,
    val schemaPath: String?
)
```

#### 사용법

**기본 사용 (Convention - 코드 0줄):**
```
store-service/
├── store-api/
│   └── sql/
│       └── schema.sql  ← 자동 탐지!
└── c4ang-platform-core/
```

```kotlin
// 최소한의 Extension (모든 서비스 동일)
class SharedContainerExtension : BaseContainerExtension() {
    override fun getComposeFile(): File =
        resolveComposeFile("c4ang-platform-core/docker-compose/test/docker-compose-integration-test.yml")
}
```

**커스터마이징 (System Properties):**
```kotlin
// build.gradle.kts
tasks.test {
    systemProperty("PLATFORM_POSTGRES_ENABLED", "true")
    systemProperty("PLATFORM_REDIS_ENABLED", "false")
    systemProperty("PLATFORM_KAFKA_ENABLED", "true")
    systemProperty("PLATFORM_SCHEMA_PATH", "custom/schema.sql")
}
```

또는 환경 변수:
```bash
PLATFORM_POSTGRES_ENABLED=false ./gradlew test
```

---

## 최종 결론

### ❌ 원래 기획안은 실현 불가능

**근본적 문제:**
1. JUnit Extension은 Spring보다 먼저 실행 → yml 프로퍼티 읽기 불가
2. @ConfigurationProperties는 Spring Bean → Extension에서 사용 불가
3. GitHub Packages 배포는 현재 아키텍처와 근본적으로 다름

### ✅ 하지만 대안이 있음

**실현 가능한 개선안:**
1. **Convention over Configuration** - 스키마/Compose 파일 자동 탐색
2. **System Properties 기반 설정** - yml 대신 더 유연한 방식
3. **조건부 컨테이너 시작** - 환경 변수로 제어
4. **Git Submodule 유지** - 현재 방식이 실제로 더 나음

**개선 효과:**
- 기존: 각 서비스가 40줄의 Extension 코드 작성
- 개선: Convention 덕분에 코드 대폭 감소 (10줄 미만)
- 스키마 파일 자동 탐지로 getSchemaFile() 오버라이드 불필요

### 🎯 권장 방향

**yml 프로퍼티 기반 설정 포기 대신:**
→ **Convention + System Properties** 조합으로 더 나은 개발자 경험 제공

**실제 사용 예시:**
```kotlin
// BEFORE (현재)
class SharedContainerExtension : BaseContainerExtension() {
    override fun getComposeFile(): File =
        resolveComposeFile("c4ang-platform-core/docker-compose/test/docker-compose-integration-test.yml")

    override fun getSchemaFile(): File =
        resolveComposeFile("store-api/sql/schema.sql")
}
```

```kotlin
// AFTER (개선안)
class SharedContainerExtension : BaseContainerExtension() {
    override fun getComposeFile(): File =
        resolveComposeFile("c4ang-platform-core/docker-compose/test/docker-compose-integration-test.yml")

    // getSchemaFile() 제거! 자동 탐지됨
}
```

**자원 활성화/비활성화:**
```bash
# CI 환경에서 Kafka 비활성화
PLATFORM_KAFKA_ENABLED=false ./gradlew test
```

**커스텀 스키마 경로:**
```bash
PLATFORM_SCHEMA_PATH=custom/path/schema.sql ./gradlew test
```

---

## 다음 단계

### 제안 1: Convention 기반 개선 구현 (추천)
- yml 기획 포기
- Convention over Configuration 적용
- System Properties로 오버라이드 지원
- 실현 가능성: ✅✅✅
- 개발 기간: 1주

### 제안 2: 현재 구조 유지
- 현재도 충분히 잘 작동함
- 추가 개발 없음
- 실현 가능성: ✅✅✅
- 개발 기간: 0일

### 제안 3: 하이브리드 접근 (실험적)
- ApplicationContext 시작 후 컨테이너 재설정
- 복잡도 매우 높음
- 실현 가능성: ⚠️
- 권장하지 않음

**추천: 제안 1 (Convention 기반 개선)**

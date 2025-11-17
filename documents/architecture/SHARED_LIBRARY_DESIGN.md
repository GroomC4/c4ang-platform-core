# Platform Core 공유 라이브러리 설계 기획안

## 목표

다른 도메인 서비스들이 서브모듈 또는 GitHub 패키지로 이 레포를 가져가서:
- **yml 프로퍼티만으로** 필요한 자원(PostgreSQL, Redis, Kafka)을 선택적으로 활성화
- **스키마 파일 경로를 프로퍼티로 지정**하여 자동으로 DB 초기화
- **최소한의 코드 작성**으로 통합 테스트 환경 구성

---

## 현재 구조 분석

### 장점
✅ BaseContainerExtension이 잘 설계되어 있음 (PostgreSQL, Redis, Kafka 지원)
✅ Docker Compose 구성이 모듈화되어 있음
✅ TestContainerContextInitializer가 동적 프로퍼티 주입 지원
✅ K8s/Helm 마이그레이션 준비 완료

### 개선 필요 사항
❌ 자원 활성화/비활성화가 코드 레벨에서만 가능 (yml 프로퍼티 지원 X)
❌ 스키마 파일 경로를 하드코딩하거나 getSchemaFile() 오버라이드 필요
❌ 다른 서비스에서 BaseContainerExtension을 상속해야만 사용 가능
❌ 프로퍼티 기반 설정이 없어 유연성 부족

---

## 설계 개선안

### 1단계: Spring Boot ConfigurationProperties 추가

#### 1.1 PlatformTestProperties 클래스 생성

```kotlin
// testcontainers/kotlin/com/groom/platform/testSupport/config/PlatformTestProperties.kt
package com.groom.platform.testSupport.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties(prefix = "platform.test")
data class PlatformTestProperties(
    /**
     * PostgreSQL 컨테이너 활성화 여부
     */
    val postgresEnabled: Boolean = true,

    /**
     * Redis 컨테이너 활성화 여부
     */
    val redisEnabled: Boolean = true,

    /**
     * Kafka 컨테이너 활성화 여부
     */
    val kafkaEnabled: Boolean = true,

    /**
     * PostgreSQL Replica 활성화 여부
     */
    val replicaEnabled: Boolean = false,

    /**
     * Schema Registry 활성화 여부
     */
    val schemaRegistryEnabled: Boolean = true,

    /**
     * 스키마 파일 경로 (절대 경로 또는 클래스패스 경로)
     * 예: "classpath:db/schema.sql", "file:/path/to/schema.sql"
     */
    val schemaPath: String? = null,

    /**
     * Docker Compose 파일 경로 (커스텀 compose 사용 시)
     */
    val composeFilePath: String? = null,

    /**
     * 컨테이너 시작 대기 시간 (초)
     */
    val startupTimeout: Int = 300,

    /**
     * 네트워크 이름 (기본값: ecommerce-network)
     */
    val networkName: String = "ecommerce-network",

    /**
     * PostgreSQL 설정
     */
    val postgres: PostgresConfig = PostgresConfig(),

    /**
     * Redis 설정
     */
    val redis: RedisConfig = RedisConfig(),

    /**
     * Kafka 설정
     */
    val kafka: KafkaConfig = KafkaConfig()
)

data class PostgresConfig(
    val primaryPort: Int? = null,  // null이면 랜덤 포트
    val replicaPort: Int? = null,
    val database: String = "groom",
    val username: String = "groom_app",
    val password: String = "groom123!",
    val maxConnections: Int = 100
)

data class RedisConfig(
    val port: Int? = null,  // null이면 랜덤 포트
    val password: String? = null
)

data class KafkaConfig(
    val port: Int? = null,  // null이면 랜덤 포트
    val autoCreateTopics: Boolean = true,
    val topics: List<String> = emptyList()  // 자동 생성할 토픽 리스트
)
```

#### 1.2 PlatformTestAutoConfiguration 클래스 생성

```kotlin
// testcontainers/kotlin/com/groom/platform/testSupport/config/PlatformTestAutoConfiguration.kt
package com.groom.platform.testSupport.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(PlatformTestProperties::class)
class PlatformTestAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "platform.test", name = ["postgres-enabled"], havingValue = "true", matchIfMissing = true)
    fun postgresContainer(properties: PlatformTestProperties): PostgresTestContainer {
        return PostgresTestContainer(properties)
    }

    @Bean
    @ConditionalOnProperty(prefix = "platform.test", name = ["redis-enabled"], havingValue = "true", matchIfMissing = true)
    fun redisContainer(properties: PlatformTestProperties): RedisTestContainer {
        return RedisTestContainer(properties)
    }

    @Bean
    @ConditionalOnProperty(prefix = "platform.test", name = ["kafka-enabled"], havingValue = "true", matchIfMissing = true)
    fun kafkaContainer(properties: PlatformTestProperties): KafkaTestContainer {
        return KafkaTestContainer(properties)
    }
}
```

---

### 2단계: 스키마 자동 로딩 메커니즘 구현

#### 2.1 SchemaLoader 인터페이스 정의

```kotlin
// testcontainers/kotlin/com/groom/platform/testSupport/schema/SchemaLoader.kt
package com.groom.platform.testSupport.schema

import java.io.File

interface SchemaLoader {
    /**
     * 스키마 파일을 로드하여 반환
     */
    fun loadSchema(): File?
}
```

#### 2.2 PropertyBasedSchemaLoader 구현

```kotlin
// testcontainers/kotlin/com/groom/platform/testSupport/schema/PropertyBasedSchemaLoader.kt
package com.groom.platform.testSupport.schema

import com.groom.platform.testSupport.config.PlatformTestProperties
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.core.io.Resource
import java.io.File
import java.nio.file.Files

class PropertyBasedSchemaLoader(
    private val properties: PlatformTestProperties
) : SchemaLoader {

    private val resourceLoader = DefaultResourceLoader()

    override fun loadSchema(): File? {
        val schemaPath = properties.schemaPath ?: return null

        return when {
            // classpath: 프로토콜 지원
            schemaPath.startsWith("classpath:") -> {
                loadFromClasspath(schemaPath)
            }
            // file: 프로토콜 지원
            schemaPath.startsWith("file:") -> {
                File(schemaPath.substring(5))
            }
            // 상대 경로 또는 절대 경로
            else -> {
                File(schemaPath).takeIf { it.exists() }
            }
        }
    }

    private fun loadFromClasspath(path: String): File? {
        val resource: Resource = resourceLoader.getResource(path)
        if (!resource.exists()) return null

        // 클래스패스 리소스를 임시 파일로 복사 (Docker 볼륨 마운트를 위해)
        val tempFile = Files.createTempFile("schema", ".sql").toFile()
        tempFile.deleteOnExit()
        resource.inputStream.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return tempFile
    }
}
```

#### 2.3 BaseContainerExtension 개선

```kotlin
// testcontainers/kotlin/com/groom/platform/testSupport/BaseContainerExtension.kt (수정)
abstract class BaseContainerExtension : BeforeAllCallback, AfterAllCallback {

    companion object {
        // 기존 코드...

        // 새로운: 프로퍼티 기반 초기화
        private var properties: PlatformTestProperties? = null

        fun initialize(props: PlatformTestProperties) {
            properties = props
            initializeContainers()
        }

        private fun initializeContainers() {
            val props = properties ?: return

            // PostgreSQL 활성화 여부 확인
            if (props.postgresEnabled) {
                initializePostgres(props)
            }

            // Redis 활성화 여부 확인
            if (props.redisEnabled) {
                initializeRedis(props)
            }

            // Kafka 활성화 여부 확인
            if (props.kafkaEnabled) {
                initializeKafka(props)
            }
        }

        private fun initializePostgres(props: PlatformTestProperties) {
            // 스키마 로더 사용
            val schemaLoader = PropertyBasedSchemaLoader(props)
            val schemaFile = schemaLoader.loadSchema()

            // Docker Compose 환경 변수 설정
            val envVars = mutableMapOf(
                "PRIMARY_POSTGRES_DB" to props.postgres.database,
                "PRIMARY_POSTGRES_USER" to props.postgres.username,
                "PRIMARY_POSTGRES_PASSWORD" to props.postgres.password
            )

            // 스키마 파일이 있으면 SCHEMA_PATH 설정
            if (schemaFile != null) {
                envVars["SCHEMA_PATH"] = schemaFile.absolutePath
            }

            // Docker Compose 초기화 (envVars 전달)
            // 기존 로직 수정 필요
        }
    }
}
```

---

### 3단계: 향상된 TestContainerContextInitializer

#### 3.1 프로퍼티 기반 초기화 지원

```kotlin
// testcontainers/kotlin/com/groom/platform/testSupport/TestContainerContextInitializer.kt (수정)
package com.groom.platform.testSupport

import com.groom.platform.testSupport.config.PlatformTestProperties
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.test.context.support.TestPropertySourceUtils

class TestContainerContextInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {

    override fun initialize(applicationContext: ConfigurableApplicationContext) {
        val environment: ConfigurableEnvironment = applicationContext.environment

        // application-test.yml에서 PlatformTestProperties 바인딩
        val properties = Binder.get(environment)
            .bindOrCreate("platform.test", PlatformTestProperties::class.java)

        // BaseContainerExtension 초기화
        BaseContainerExtension.initialize(properties)

        // 동적 프로퍼티 주입
        val propertyList = mutableListOf<String>()

        // PostgreSQL이 활성화된 경우
        if (properties.postgresEnabled) {
            val primaryJdbcUrl = BaseContainerExtension.getPrimaryJdbcUrl()
            propertyList.add("spring.datasource.url=$primaryJdbcUrl")
            propertyList.add("spring.datasource.username=${properties.postgres.username}")
            propertyList.add("spring.datasource.password=${properties.postgres.password}")

            if (properties.replicaEnabled) {
                val replicaJdbcUrl = BaseContainerExtension.getReplicaJdbcUrl()
                propertyList.add("spring.datasource.replica.url=$replicaJdbcUrl")
            }
        }

        // Redis가 활성화된 경우
        if (properties.redisEnabled) {
            val redisHost = BaseContainerExtension.getRedisHost()
            val redisPort = BaseContainerExtension.getRedisPort()
            propertyList.add("spring.data.redis.host=$redisHost")
            propertyList.add("spring.data.redis.port=$redisPort")
        }

        // Kafka가 활성화된 경우
        if (properties.kafkaEnabled) {
            val kafkaBootstrapServers = BaseContainerExtension.getKafkaBootstrapServers()
            propertyList.add("spring.kafka.bootstrap-servers=$kafkaBootstrapServers")

            if (properties.schemaRegistryEnabled) {
                val schemaRegistryUrl = BaseContainerExtension.getSchemaRegistryUrl()
                propertyList.add("spring.kafka.properties.schema.registry.url=$schemaRegistryUrl")
            }
        }

        // 프로퍼티 주입
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
            applicationContext,
            *propertyList.toTypedArray()
        )
    }
}
```

---

### 4단계: 다른 서비스에서의 사용법

#### 4.1 서브모듈로 추가

```bash
# 다른 서비스 레포에서
git submodule add https://github.com/GroomC4/c4ang-platform-core.git libs/platform-core
git submodule update --init --recursive
```

#### 4.2 Gradle 설정 (build.gradle.kts)

```kotlin
// settings.gradle.kts
includeBuild("libs/platform-core")

// build.gradle.kts
dependencies {
    testImplementation(project(":platform-core:testcontainers"))
}
```

#### 4.3 application-test.yml 설정

```yaml
# src/test/resources/application-test.yml
platform:
  test:
    # 필요한 자원만 활성화
    postgres-enabled: true
    redis-enabled: true
    kafka-enabled: true
    replica-enabled: false  # Replica는 필요 없으면 비활성화
    schema-registry-enabled: true

    # 스키마 파일 경로 지정 (클래스패스 또는 파일 경로)
    schema-path: "classpath:db/schema.sql"
    # schema-path: "file:/absolute/path/to/schema.sql"
    # schema-path: "src/test/resources/db/schema.sql"

    # 선택적: 커스텀 Docker Compose 파일
    # compose-file-path: "custom-docker-compose.yml"

    # PostgreSQL 설정
    postgres:
      database: "my_service_db"
      username: "my_app"
      password: "my_password"
      primary-port: null  # null이면 랜덤 포트 (권장)
      max-connections: 100

    # Redis 설정
    redis:
      port: null  # 랜덤 포트
      password: null

    # Kafka 설정
    kafka:
      port: null  # 랜덤 포트
      auto-create-topics: true
      topics:
        - "order-events"
        - "payment-events"
        - "user-events"

# Spring Boot 자동 설정 (자동으로 주입됨)
spring:
  # DataSource는 자동 설정됨
  # datasource:
  #   url: jdbc:postgresql://localhost:xxxxx/my_service_db  (자동 주입)
  #   username: my_app  (자동 주입)
  #   password: my_password  (자동 주입)

  # Redis는 자동 설정됨
  # data:
  #   redis:
  #     host: localhost  (자동 주입)
  #     port: xxxxx  (자동 주입)

  # Kafka는 자동 설정됨
  # kafka:
  #   bootstrap-servers: localhost:xxxxx  (자동 주입)
```

#### 4.4 통합 테스트 작성 (간소화된 버전)

```kotlin
// src/test/kotlin/com/example/myservice/MyServiceIntegrationTest.kt
package com.example.myservice

import com.groom.platform.testSupport.IntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate

@IntegrationTest  // 이 어노테이션 하나로 모든 설정 완료!
@SpringBootTest
class MyServiceIntegrationTest {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `PostgreSQL 연결 테스트`() {
        val result = jdbcTemplate.queryForObject("SELECT 1", Int::class.java)
        assert(result == 1)
    }

    // Redis, Kafka 테스트도 동일하게 작성
}
```

**기존 방식과 비교:**

**BEFORE (기존 방식):**
```kotlin
// 1. BaseContainerExtension 상속 필요
class MyServiceContainerExtension : BaseContainerExtension() {
    override fun getComposeFile(): File {
        return File("path/to/compose.yml")
    }

    override fun getSchemaFile(): File {
        return File("path/to/schema.sql")
    }
}

// 2. 커스텀 어노테이션 생성 필요
@ExtendWith(MyServiceContainerExtension::class)
@ContextConfiguration(initializers = [TestContainerContextInitializer::class])
annotation class MyServiceIntegrationTest

// 3. 테스트 클래스에서 사용
@MyServiceIntegrationTest
class MyTest { ... }
```

**AFTER (개선 후):**
```yaml
# application-test.yml에 설정만 추가
platform:
  test:
    postgres-enabled: true
    schema-path: "classpath:db/schema.sql"
```

```kotlin
// 테스트 클래스에서 바로 사용
@IntegrationTest  // 끝!
class MyTest { ... }
```

---

### 5단계: GitHub Package 배포 (선택 사항)

#### 5.1 build.gradle.kts 추가

```kotlin
// testcontainers/build.gradle.kts (신규 파일)
plugins {
    kotlin("jvm") version "1.9.20"
    `maven-publish`
}

group = "com.groom.platform"
version = "1.0.0"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.testcontainers:testcontainers:1.19.3")
    implementation("org.testcontainers:kafka:1.19.3")
    implementation("org.testcontainers:postgresql:1.19.3")
    // 기타 의존성...
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "platform-core-testcontainers"
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/GroomC4/c4ang-platform-core")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
```

#### 5.2 다른 서비스에서 사용

```kotlin
// 다른 서비스의 build.gradle.kts
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/GroomC4/c4ang-platform-core")
        credentials {
            username = System.getenv("GITHUB_ACTOR")
            password = System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    testImplementation("com.groom.platform:platform-core-testcontainers:1.0.0")
}
```

---

## 구현 우선순위

### Phase 1: 핵심 기능 (1-2주)
1. ✅ PlatformTestProperties 클래스 생성
2. ✅ PropertyBasedSchemaLoader 구현
3. ✅ TestContainerContextInitializer 개선
4. ✅ BaseContainerExtension 프로퍼티 기반 초기화 지원

### Phase 2: 편의 기능 (1주)
1. PlatformTestAutoConfiguration 추가
2. 조건부 Bean 생성 (@ConditionalOnProperty)
3. Kafka 토픽 자동 생성 기능
4. 문서화 및 예제 작성

### Phase 3: 배포 및 검증 (1주)
1. Gradle 빌드 설정
2. GitHub Packages 배포 설정
3. 실제 도메인 서비스에서 검증
4. 마이그레이션 가이드 작성

---

## 예상 효과

### 개발자 경험 개선
- ✅ **yml 파일 수정만으로** 테스트 환경 구성 (코드 작성 불필요)
- ✅ **스키마 파일 경로만 지정**하면 자동으로 DB 초기화
- ✅ **선택적 자원 활성화**로 테스트 실행 속도 개선
- ✅ **서브모듈 또는 패키지** 선택지 제공

### 유지보수성 향상
- ✅ **중앙화된 설정**으로 일관된 테스트 환경
- ✅ **프로퍼티 검증**으로 설정 오류 조기 발견
- ✅ **버전 관리**로 안정적인 의존성 관리

### 확장성 강화
- ✅ **새로운 자원 추가**가 쉬움 (MongoDB, ElasticSearch 등)
- ✅ **커스텀 설정** 가능 (포트, 타임아웃 등)
- ✅ **K8s/Helm 마이그레이션** 준비 완료

---

## 사용 예시 비교

### 시나리오: Order Service 통합 테스트

**BEFORE (기존):**

```kotlin
// 1. Extension 클래스 작성 (30줄)
class OrderServiceContainerExtension : BaseContainerExtension() {
    override fun getComposeFile(): File = File("...")
    override fun getSchemaFile(): File = File("...")
}

// 2. 커스텀 어노테이션 작성 (10줄)
@ExtendWith(OrderServiceContainerExtension::class)
annotation class OrderIntegrationTest

// 3. 테스트 작성
@OrderIntegrationTest
class OrderRepositoryTest { ... }
```

**총 40줄 이상의 보일러플레이트 코드 필요**

---

**AFTER (개선):**

```yaml
# application-test.yml (5줄)
platform:
  test:
    postgres-enabled: true
    schema-path: "classpath:db/order-schema.sql"
```

```kotlin
// 테스트 작성 (기존 어노테이션 재사용)
@IntegrationTest
class OrderRepositoryTest { ... }
```

**총 5줄의 yml 설정만 필요 (코드 0줄)**

---

## 마이그레이션 전략

### 단계별 마이그레이션

1. **Phase 1: 신규 서비스부터 적용**
   - 새로 생성되는 도메인 서비스에 우선 적용
   - 피드백 수집 및 개선

2. **Phase 2: 기존 서비스 점진적 마이그레이션**
   - 기존 BaseContainerExtension 상속 방식과 호환성 유지
   - 서비스별로 순차적으로 마이그레이션

3. **Phase 3: 레거시 방식 Deprecation**
   - 모든 서비스 마이그레이션 완료 후
   - 기존 방식 deprecated 처리

---

## 참고 문서

- [Testcontainers 공식 문서](https://www.testcontainers.org/)
- [Spring Boot ConfigurationProperties](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config.typesafe-configuration-properties)
- [Docker Compose 환경 변수](https://docs.docker.com/compose/environment-variables/)
- [GitHub Packages Maven](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry)

---

## 결론

이 설계를 통해:
- **개발자는 yml 파일만 수정**하여 테스트 환경 구성
- **스키마 파일 경로만 지정**하면 자동으로 DB 초기화
- **코드 작성 없이** 통합 테스트 실행 가능
- **서브모듈 또는 패키지** 형태로 쉽게 배포

다른 도메인 서비스들이 최소한의 노력으로 강력한 테스트 인프라를 활용할 수 있게 됩니다.

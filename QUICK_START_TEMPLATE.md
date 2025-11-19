# ⚡ Platform Core 빠른 시작 템플릿

> 이 파일의 코드를 복사하여 도메인 서버에 적용하세요!

## 🎯 복사용 코드 스니펫

### 1. build.gradle.kts에 추가

```kotlin
// 의존성 추가 (dependencies 블록에 추가)
dependencies {
    // 기존 의존성들...

    // Platform Core DataSource & TestContainers
    implementation("com.groom.platform:datasource-starter:1.0.0")
    testImplementation("com.groom.platform:testcontainers-starter:1.0.0")

    // PostgreSQL Driver (이미 있으면 생략)
    runtimeOnly("org.postgresql:postgresql")
}

// GitHub Packages 저장소 추가 (repositories 블록에 추가)
repositories {
    // 기존 저장소들...

    maven {
        url = uri("https://maven.pkg.github.com/GroomC4/c4ang-platform-core")
        credentials {
            username = System.getenv("GITHUB_ACTOR") ?: project.findProperty("gpr.user") as String?
            password = System.getenv("GITHUB_TOKEN") ?: project.findProperty("gpr.token") as String?
        }
    }
}
```

### 2. src/main/resources/application.yml

```yaml
spring:
  application:
    name: your-service-name

  # DataSource 설정 (Platform Core가 자동 구성)
  datasource:
    master:
      url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:mydb}
      username: ${DB_USERNAME:postgres}
      password: ${DB_PASSWORD:password}
      hikari:
        maximum-pool-size: ${DB_POOL_SIZE:10}
        connection-timeout: 30000
        idle-timeout: 600000
        max-lifetime: 1800000

    # Replica 설정 (선택사항 - 없으면 master 사용)
    replica:
      url: jdbc:postgresql://${DB_REPLICA_HOST:localhost}:${DB_REPLICA_PORT:5432}/${DB_NAME:mydb}
      username: ${DB_USERNAME:postgres}
      password: ${DB_PASSWORD:password}
      hikari:
        maximum-pool-size: ${DB_REPLICA_POOL_SIZE:20}
        connection-timeout: 30000

  # JPA 설정
  jpa:
    database-platform: org.hibernate.dialect.PostgreSQLDialect
    hibernate:
      ddl-auto: ${JPA_DDL_AUTO:validate}
    properties:
      hibernate:
        format_sql: true
        use_sql_comments: true
    show-sql: ${JPA_SHOW_SQL:false}

# 로깅 설정
logging:
  level:
    com.groom.platform.datasource: INFO
    org.springframework.transaction: DEBUG  # Transaction 디버깅용
```

### 3. src/main/resources/application-local.yml

```yaml
spring:
  datasource:
    master:
      url: jdbc:postgresql://localhost:5432/local_db
      username: local_user
      password: local_pass
      hikari:
        maximum-pool-size: 5

    # 로컬에서는 replica 없이 개발 가능
    # replica 설정 생략 시 master 사용

  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true

logging:
  level:
    com.groom: DEBUG
```

### 4. src/test/resources/application-test.yml

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true
    properties:
      hibernate:
        format_sql: true

# TestContainers 설정 (자동으로 Docker 컨테이너 시작)
testcontainers:
  postgres:
    enabled: true
    image: postgres:15-alpine
    database-name: testdb

  redis:
    enabled: true
    image: redis:7-alpine

logging:
  level:
    com.groom: DEBUG
    org.testcontainers: INFO
    org.springframework.test: DEBUG
```

### 5. 기본 JPA Config (src/main/kotlin/.../config/JpaConfig.kt)

```kotlin
package com.groom.yourservice.config

import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.transaction.annotation.EnableTransactionManagement

@Configuration
@EnableJpaRepositories(
    basePackages = ["com.groom.yourservice.repository"]
)
@EnableJpaAuditing
@EnableTransactionManagement
class JpaConfig
```

### 6. 테스트용 어노테이션 (src/test/kotlin/.../annotation/IntegrationTestBase.kt)

```kotlin
package com.groom.yourservice.test

import com.groom.platform.testcontainers.annotation.IntegrationTest
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/**
 * 통합 테스트 베이스 어노테이션
 * - TestContainers 자동 시작
 * - DataSource 자동 구성
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@SpringBootTest
@IntegrationTest
@ActiveProfiles("test")
annotation class IntegrationTestBase
```

### 7. 서비스 예제 (src/main/kotlin/.../service/ExampleService.kt)

```kotlin
package com.groom.yourservice.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ExampleService(
    private val exampleRepository: ExampleRepository
) {

    /**
     * 쓰기 작업 - Master DB 사용
     */
    @Transactional(readOnly = false)
    fun create(data: CreateRequest): ExampleEntity {
        val entity = ExampleEntity(
            name = data.name,
            description = data.description
        )
        return exampleRepository.save(entity)
    }

    /**
     * 읽기 작업 - Replica DB 사용
     */
    @Transactional(readOnly = true)
    fun findById(id: Long): ExampleEntity? {
        return exampleRepository.findById(id).orElse(null)
    }

    /**
     * 읽기 작업 - Replica DB 사용
     */
    @Transactional(readOnly = true)
    fun findAll(): List<ExampleEntity> {
        return exampleRepository.findAll()
    }

    /**
     * 수정 작업 - Master DB 사용
     */
    @Transactional(readOnly = false)
    fun update(id: Long, data: UpdateRequest): ExampleEntity {
        val entity = findById(id)
            ?: throw EntityNotFoundException("Entity not found: $id")

        entity.apply {
            name = data.name
            description = data.description
        }

        return exampleRepository.save(entity)
    }
}
```

### 8. 통합 테스트 예제 (src/test/kotlin/.../ExampleServiceTest.kt)

```kotlin
package com.groom.yourservice.service

import com.groom.yourservice.test.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

@IntegrationTestBase
class ExampleServiceTest {

    @Autowired
    private lateinit var exampleService: ExampleService

    @Test
    fun `should create and find entity`() {
        // Given
        val request = CreateRequest(
            name = "테스트",
            description = "설명"
        )

        // When
        val created = exampleService.create(request)
        val found = exampleService.findById(created.id!!)

        // Then
        assertThat(found).isNotNull
        assertThat(found?.name).isEqualTo("테스트")
        assertThat(found?.description).isEqualTo("설명")
    }

    @Test
    fun `should use different datasources for read and write`() {
        // Given - Master DB에 쓰기
        val entity = exampleService.create(
            CreateRequest("Master 테스트", "Master에 저장")
        )

        // When - Replica DB에서 읽기
        val allEntities = exampleService.findAll()

        // Then
        assertThat(allEntities).isNotEmpty
        assertThat(allEntities).anyMatch { it.id == entity.id }
    }
}
```

### 9. 환경변수 설정 (.env.example)

```bash
# Database Configuration
DB_HOST=localhost
DB_PORT=5432
DB_NAME=myservice_db
DB_USERNAME=postgres
DB_PASSWORD=your_password

# Replica Database (Optional)
DB_REPLICA_HOST=localhost
DB_REPLICA_PORT=5433

# Connection Pool
DB_POOL_SIZE=10
DB_REPLICA_POOL_SIZE=20

# JPA Settings
JPA_DDL_AUTO=validate
JPA_SHOW_SQL=false

# GitHub Packages (for Gradle)
GITHUB_ACTOR=your_github_username
GITHUB_TOKEN=your_github_token
```

### 10. Docker Compose (개발용) - docker-compose.yml

```yaml
version: '3.8'

services:
  postgres-master:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: myservice_db
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: password
    ports:
      - "5432:5432"
    volumes:
      - postgres_master_data:/var/lib/postgresql/data

  postgres-replica:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: myservice_db
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: password
    ports:
      - "5433:5432"
    volumes:
      - postgres_replica_data:/var/lib/postgresql/data

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data

volumes:
  postgres_master_data:
  postgres_replica_data:
  redis_data:
```

## 🚀 실행 명령어

```bash
# 1. 로컬 개발 환경 실행
docker-compose up -d
./gradlew bootRun --args='--spring.profiles.active=local'

# 2. 테스트 실행 (TestContainers 자동 실행)
./gradlew test

# 3. 통합 테스트만 실행
./gradlew test --tests "*IntegrationTest*"

# 4. 빌드
./gradlew clean build

# 5. JAR 실행 (Production)
java -jar build/libs/your-service.jar \
  --spring.profiles.active=prod \
  --DB_HOST=production-db-host \
  --DB_USERNAME=prod_user \
  --DB_PASSWORD=prod_password
```

## ✅ 체크리스트

- [ ] build.gradle.kts에 의존성 추가
- [ ] GitHub Packages 인증 정보 설정 (.env 또는 gradle.properties)
- [ ] application.yml 설정 복사
- [ ] application-test.yml 설정 복사
- [ ] 기존 DataSource 설정 클래스 제거
- [ ] @Transactional(readOnly) 어노테이션 확인
- [ ] 테스트 실행 확인
- [ ] Docker 실행 확인 (TestContainers용)

## 📌 주의사항

1. **GitHub Packages 인증**: gradle.properties 또는 환경변수 설정 필요
2. **Docker 필수**: TestContainers 실행을 위해 Docker Desktop 필요
3. **Profile 구분**: local, test, dev, prod 등 환경별 설정 분리
4. **Transaction 어노테이션**: Service 레이어에서 @Transactional 사용

## 🆘 문제 해결

### GitHub Packages 인증 실패
```bash
# ~/.gradle/gradle.properties 생성
gpr.user=your_github_username
gpr.token=your_github_personal_access_token
```

### TestContainers 실행 실패
```bash
# Docker 실행 확인
docker version

# Docker Desktop 시작
open -a Docker  # macOS
```

### 순환 참조 오류
- Platform Core 최신 버전 사용 (1.0.0 이상)
- 기존 DataSource @Configuration 클래스 모두 제거

---

**이 템플릿을 복사하여 사용하시고, 문제가 있으면 Platform Core 팀에 문의하세요!**
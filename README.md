# c4ang-platform-core

Spring Boot 기반 마이크로서비스를 위한 공통 플랫폼 라이브러리입니다.

## 주요 기능

- **Primary-Replica DataSource 라우팅**: `@Transactional(readOnly)` 기반 자동 라우팅
- **Local 개발 환경**: Docker Compose 자동 시작 및 동적 포트 주입
- **테스트 환경**: Testcontainers 기반 통합 테스트 지원

## 모듈 구조

```
c4ang-platform-core/
├── platform-core/           # 메인 모듈 (DataSource, Local 환경)
└── testcontainers-starter/  # 테스트용 모듈 (Testcontainers)
```

## 빠른 시작

### 1. 프로젝트에 연동하기

#### settings.gradle.kts

```kotlin
// Git Submodule로 추가된 경우
includeBuild("c4ang-platform-core")
```

#### build.gradle.kts

```kotlin
dependencies {
    // 메인 모듈 (Local 개발 + Production)
    implementation("com.groom.platform:platform-core")

    // 테스트용 모듈
    testImplementation("com.groom.platform:testcontainers-starter")
}
```

### 2. 프로필 설정

#### Local 개발 환경 (application-local.yml)

Local 프로필에서는 Docker Compose가 자동으로 시작되며, 동적 포트가 자동 주입됩니다.
**별도 설정 없이** 바로 사용 가능합니다.

```yaml
# application-local.yml
# 기본값 사용 시 설정 불필요!

# 커스터마이징이 필요한 경우:
platform:
  infrastructure:
    docker-compose-enabled: true    # Docker Compose 자동 시작 (기본: true)
    health-check-timeout: PT1M      # 헬스체크 타임아웃 (기본: 1분)
    postgres:
      enabled: true                 # PostgreSQL 활성화 (기본: true)
      database: groom               # 데이터베이스명 (기본: groom)
      username: application         # 사용자명 (기본: application)
      password: application         # 비밀번호 (기본: application)
    redis:
      enabled: true                 # Redis 활성화 (기본: true)
    kafka:
      enabled: true                 # Kafka 활성화 (기본: true)
```

#### Production 환경 (application-prod.yml)

```yaml
# application-prod.yml
spring:
  datasource:
    master:
      url: jdbc:postgresql://primary-db:5432/mydb
      username: ${DB_USERNAME}
      password: ${DB_PASSWORD}
    replica:
      url: jdbc:postgresql://replica-db:5432/mydb
      username: ${DB_USERNAME}
      password: ${DB_PASSWORD}

  data:
    redis:
      host: ${REDIS_HOST}
      port: ${REDIS_PORT}

  kafka:
    bootstrap-servers: ${KAFKA_SERVERS}
```

### 3. 실행

```bash
# Local 개발 (Docker Compose 자동 시작)
./gradlew bootRun --args='--spring.profiles.active=local'

# Production
./gradlew bootRun --args='--spring.profiles.active=prod'
```

## 테스트 작성

### 통합 테스트

```kotlin
import com.groom.platform.testcontainers.annotation.IntegrationTest

@IntegrationTest
class MyServiceIntegrationTest {

    @Autowired
    private lateinit var myService: MyService

    @Test
    @Transactional(readOnly = false)  // MASTER DB 사용
    fun `쓰기 작업 테스트`() {
        // ...
    }

    @Test
    @Transactional(readOnly = true)   // REPLICA DB 사용
    fun `읽기 작업 테스트`() {
        // ...
    }
}
```

`@IntegrationTest` 어노테이션은 다음을 자동으로 설정합니다:
- PostgreSQL Primary/Replica 컨테이너
- Redis 컨테이너
- DataSource 라우팅

## 인프라 구성

Local 환경에서 자동으로 시작되는 인프라:

| 서비스 | 이미지 | 설명 |
|--------|--------|------|
| postgres-primary | postgres:17-alpine | Primary DB |
| postgres-replica | postgres:17-alpine | Replica DB |
| redis | redis:7-alpine | 캐시/세션 |
| kafka | confluentinc/cp-kafka:7.5.0 | 메시지 브로커 |

### Docker Compose 관리

```bash
# 컨테이너 상태 확인
docker compose -p c4ang-local ps

# 컨테이너 중지 (필요시)
docker compose -p c4ang-local down

# 볼륨 포함 삭제
docker compose -p c4ang-local down -v
```

## DataSource 라우팅

`@Transactional` 어노테이션의 `readOnly` 속성에 따라 자동 라우팅됩니다:

```kotlin
@Service
class MyService(
    private val repository: MyRepository
) {
    @Transactional(readOnly = false)  // → MASTER DB
    fun save(entity: MyEntity) {
        repository.save(entity)
    }

    @Transactional(readOnly = true)   // → REPLICA DB
    fun findAll(): List<MyEntity> {
        return repository.findAll()
    }
}
```

## 트러블슈팅

### Docker가 실행되지 않는 경우

```
Docker is not installed or not running!
```

Docker Desktop을 설치하고 실행해주세요.

### 포트 충돌

동적 포트 할당을 사용하므로 포트 충돌이 발생하지 않습니다.
기존에 실행 중인 컨테이너가 있으면 재사용합니다.

### 헬스체크 타임아웃

```yaml
platform:
  infrastructure:
    health-check-timeout: PT2M  # 2분으로 증가
```

## 라이선스

Internal use only - C4ANG Team

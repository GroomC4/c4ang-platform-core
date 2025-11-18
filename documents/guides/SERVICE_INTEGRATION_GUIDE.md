# 서비스 통합 가이드

이 문서는 **platform-core 패키지를 사용하는 서비스 개발자**를 위한 가이드입니다.

## 목차

1. [시작하기 전에](#시작하기-전에)
2. [로컬 환경 설정](#로컬-환경-설정)
3. [프로젝트 설정](#프로젝트-설정)
4. [테스트 환경 구성](#테스트-환경-구성)
5. [사용 예시](#사용-예시)
6. [트러블슈팅](#트러블슈팅)

---

## 시작하기 전에

### 이 패키지가 제공하는 것

**platform-core**는 마이크로서비스의 통합 테스트와 프로덕션 환경을 위한 공통 인프라를 제공합니다:

✅ **Primary-Replica 자동 라우팅**
- `@Transactional(readOnly = true)` → Replica DB
- `@Transactional(readOnly = false)` → Primary DB
- 코드 작성 없이 자동으로 작동

✅ **통합 테스트 자동화**
- PostgreSQL (Primary/Replica) 자동 시작
- Redis 자동 시작
- Kafka 자동 시작
- 스키마 자동 로딩

✅ **설정 간소화**
- yml 파일만으로 모든 설정 완료
- DataSource 설정 코드 불필요
- Testcontainers 설정 코드 불필요

### 필요한 것

- **Java:** JDK 21
- **Spring Boot:** 3.3.4
- **Kotlin:** 2.0.21
- **Docker:** Docker Desktop (통합 테스트 + 로컬 개발용)
- **GitHub Token:** 패키지 다운로드용

---

## 로컬 환경 설정

### 로컬 개발 환경 (선택사항)

서비스를 로컬에서 직접 실행하며 개발하려면 Docker Compose로 인프라를 실행할 수 있습니다.

**자세한 가이드:**
- **[로컬 개발 환경 가이드](../../local-dev/README.md)**
  - PostgreSQL Primary/Replica, Redis, Kafka 실행
  - 서비스 접속 정보
  - Kafka 관리 명령어

**빠른 시작:**
```bash
cd local-dev
docker compose -f docker-compose.local.yml up -d
```

### 1. Docker 설치 및 실행

통합 테스트와 로컬 개발 환경 모두 Docker가 필요합니다.

**macOS:**
1. [Docker Desktop 다운로드](https://www.docker.com/products/docker-desktop)
2. 설치 후 실행
3. 확인:
   ```bash
   docker ps
   ```

**Windows:**
1. [Docker Desktop 다운로드](https://www.docker.com/products/docker-desktop)
2. WSL2 설정 (자동으로 안내)
3. 확인:
   ```powershell
   docker ps
   ```

**Linux:**
```bash
# Ubuntu/Debian
sudo apt-get update
sudo apt-get install docker.io

# 확인
docker ps
```

### 2. GitHub Token 설정

platform-core 패키지는 GitHub Packages에 배포되어 있으므로 인증이 필요합니다.

#### 2.1. Token 발급 받기

팀 리더 또는 유지보수 담당자에게 요청:
- **필요한 권한:** `read:packages`
- **토큰 형식:** `ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx`

또는 직접 생성:
1. https://github.com/settings/tokens 접속
2. **Generate new token (classic)** 클릭
3. **Select scopes:**
   - ✅ `read:packages`
   - ✅ `repo` (Private repository인 경우)
4. **Generate token** 클릭
5. 토큰 복사

#### 2.2. 환경 변수 설정

**macOS/Linux:**

```bash
# ~/.zshrc 또는 ~/.bashrc에 추가
export GITHUB_ACTOR="your-github-username"
export GITHUB_TOKEN="ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"

# 적용
source ~/.zshrc

# 확인
echo $GITHUB_ACTOR
echo $GITHUB_TOKEN
```

**Windows (PowerShell):**

```powershell
# 시스템 환경 변수 설정
setx GITHUB_ACTOR "your-github-username"
setx GITHUB_TOKEN "ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"

# 터미널 재시작 후 확인
echo $env:GITHUB_ACTOR
echo $env:GITHUB_TOKEN
```

**IntelliJ IDEA 설정 (선택사항):**

1. **Run → Edit Configurations**
2. **Environment variables** 클릭
3. 추가:
   ```
   GITHUB_ACTOR=your-username
   GITHUB_TOKEN=ghp_xxx...
   ```

---

## 프로젝트 설정

### 1. 의존성 추가

`build.gradle.kts` 파일 수정:

```kotlin
repositories {
    mavenCentral()

    // GitHub Packages 추가
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/GroomC4/c4ang-platform-core")
        credentials {
            username = System.getenv("GITHUB_ACTOR")
            password = System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    // 기존 의존성들...

    // Platform Core - 프로덕션용 (선택사항)
    implementation("com.groom.platform:datasource-starter:1.0.0-SNAPSHOT")

    // Platform Core - 테스트용 (필수)
    testImplementation("com.groom.platform:testcontainers-starter:1.0.0-SNAPSHOT")

    // PostgreSQL Driver (필수)
    runtimeOnly("org.postgresql:postgresql")
}
```

### 2. 의존성 다운로드 확인

```bash
./gradlew dependencies --refresh-dependencies

# 성공하면:
# BUILD SUCCESSFUL
```

---

## 테스트 환경 구성

### 1. 스키마 파일 준비

테스트용 데이터베이스 스키마를 준비합니다.

#### 디렉토리 구조:
```
your-service/
└── src/
    └── test/
        └── resources/
            └── db/
                └── schema.sql  ← 여기에 DDL 작성
```

#### schema.sql 예시:
```sql
-- 테이블 생성
CREATE TABLE IF NOT EXISTS orders (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    total_amount DECIMAL(10, 2) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 인덱스 생성
CREATE INDEX idx_orders_user_id ON orders(user_id);
CREATE INDEX idx_orders_status ON orders(status);

-- 초기 데이터 (선택사항)
INSERT INTO orders (user_id, total_amount, status)
VALUES (1, 10000.00, 'PENDING');
```

### 2. application-test.yml 설정

`src/test/resources/application-test.yml` 파일 생성:

```yaml
spring:
  profiles:
    active: test

# Testcontainers 설정
testcontainers:
  postgres:
    enabled: true                           # PostgreSQL 컨테이너 활성화
    replica-enabled: true                   # Replica 컨테이너 활성화
    schema-location: classpath:db/schema.sql  # 스키마 파일 경로
    database: testdb                        # 데이터베이스 이름
    username: test                          # 사용자 이름
    password: test                          # 비밀번호

  redis:
    enabled: true                           # Redis 컨테이너 활성화

  kafka:
    enabled: true                           # Kafka 컨테이너 활성화
    auto-create-topics: true                # 토픽 자동 생성 활성화 (기본값, 생략 가능)
    topics:                                 # 사전 정의 토픽 (선택사항)
      - name: order.created
        partitions: 3                       # 파티션 수
        replication-factor: 1               # 복제 계수 (Testcontainers는 1만 가능)
        config:
          retention.ms: 604800000           # 7일 보관
      - name: payment.processed
        partitions: 1
        replication-factor: 1
      - name: notification.sent
        partitions: 2
        replication-factor: 1

# JPA 설정
spring:
  jpa:
    hibernate:
      ddl-auto: validate                    # 스키마 검증만 (create/update 사용 금지)
    show-sql: true                          # SQL 로그 출력
    properties:
      hibernate:
        format_sql: true                    # SQL 포맷팅
        use_sql_comments: true              # SQL 주석 출력

# Logging
logging:
  level:
    com.groom: DEBUG                        # 애플리케이션 로그
    org.springframework.jdbc: DEBUG         # JDBC 로그
    org.hibernate.SQL: DEBUG                # Hibernate SQL
```

### 3. 기존 설정 파일 제거 (중요!)

다음 파일들이 있다면 **삭제**하세요:

❌ **삭제할 파일:**
```
src/test/kotlin/com/groom/yourservice/config/TestDataSourceConfig.kt
src/test/kotlin/com/groom/yourservice/config/TestRedisConfig.kt
src/test/kotlin/com/groom/yourservice/extension/ContainerExtension.kt
```

**이유:** platform-core가 자동으로 설정하므로 불필요합니다.

---

## 사용 예시

### 1. 통합 테스트 작성

#### 기본 테스트:

```kotlin
package com.groom.yourservice.repository

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
class OrderRepositoryTest {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    @Transactional(readOnly = false)  // MASTER DB 사용
    fun `주문 생성 테스트`() {
        // Given
        val sql = """
            INSERT INTO orders (user_id, total_amount, status)
            VALUES (?, ?, ?)
        """

        // When
        val result = jdbcTemplate.update(sql, 1L, 10000.00, "PENDING")

        // Then
        assert(result == 1)
    }

    @Test
    @Transactional(readOnly = true)  // REPLICA DB 사용
    fun `주문 조회 테스트`() {
        // When
        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM orders",
            Int::class.java
        )

        // Then
        assert(count >= 0)
    }
}
```

#### Repository 테스트:

```kotlin
package com.groom.yourservice.repository

import com.groom.yourservice.domain.Order
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
class JpaRepositoryTest {

    @Autowired
    private lateinit var orderRepository: OrderRepository

    @Test
    @Transactional(readOnly = false)
    fun `JPA save 테스트`() {
        // Given
        val order = Order(
            userId = 1L,
            totalAmount = 10000.00,
            status = "PENDING"
        )

        // When
        val saved = orderRepository.save(order)

        // Then
        assert(saved.id != null)
    }

    @Test
    @Transactional(readOnly = true)
    fun `JPA findAll 테스트`() {
        // When
        val orders = orderRepository.findAll()

        // Then
        assert(orders is List)
    }
}
```

#### Redis 테스트:

```kotlin
package com.groom.yourservice.cache

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.RedisTemplate

@SpringBootTest
class RedisCacheTest {

    @Autowired
    private lateinit var redisTemplate: RedisTemplate<String, String>

    @Test
    fun `Redis 저장 및 조회 테스트`() {
        // Given
        val key = "test:key"
        val value = "test value"

        // When
        redisTemplate.opsForValue().set(key, value)
        val result = redisTemplate.opsForValue().get(key)

        // Then
        assert(result == value)
    }
}
```

#### Kafka 테스트:

```kotlin
package com.groom.yourservice.messaging

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.core.KafkaTemplate
import java.util.concurrent.TimeUnit

@SpringBootTest
class KafkaProducerTest {

    @Autowired
    private lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @Test
    fun `Kafka 메시지 발행 테스트`() {
        // Given
        val topic = "order.created"
        val message = "Order created: 12345"

        // When
        val future = kafkaTemplate.send(topic, message)

        // Then
        val result = future.get(10, TimeUnit.SECONDS)
        assert(result.recordMetadata.topic() == topic)
    }
}
```

### 4. Kafka 토픽 설정 상세

RC8부터 Kafka 토픽 설정이 대폭 강화되었습니다.

#### 옵션 1: 자동 생성만 사용 (가장 간단)

```yaml
testcontainers:
  kafka:
    enabled: true
    # auto-create-topics: true (기본값)
    # Producer가 존재하지 않는 토픽에 메시지를 보내면 자동으로 생성됩니다.
```

**장점:**
- 설정 불필요
- 테스트마다 다른 토픽 사용 가능
- 빠른 프로토타이핑

**단점:**
- 토픽 설정 제어 불가 (파티션=1, replication-factor=1 고정)

#### 옵션 2: 사전 정의 토픽 사용 (권장)

```yaml
testcontainers:
  kafka:
    enabled: true
    auto-create-topics: true  # 기본값, 생략 가능
    topics:
      - name: order.created
        partitions: 3
        replication-factor: 1
        config:
          retention.ms: 604800000    # 7일 보관
          max.message.bytes: 1048576 # 1MB
          compression.type: gzip     # 압축 방식
```

**장점:**
- 중요 토픽은 운영 환경과 동일하게 설정
- 예상치 못한 토픽은 자동 생성 (백업)
- 토픽별 상세 설정 가능

**사용 예시:**
```yaml
testcontainers:
  kafka:
    enabled: true
    topics:
      # 주문 이벤트 - 높은 처리량
      - name: order.created
        partitions: 6
        replication-factor: 1

      # 결제 이벤트 - 순서 보장 중요
      - name: payment.processed
        partitions: 1
        replication-factor: 1
        config:
          retention.ms: 2592000000  # 30일

      # 알림 이벤트 - 일시적 데이터
      - name: notification.sent
        partitions: 3
        replication-factor: 1
        config:
          retention.ms: 86400000    # 1일
```

#### 옵션 3: 엄격한 제어 (운영 환경 시뮬레이션)

```yaml
testcontainers:
  kafka:
    enabled: true
    auto-create-topics: false  # 자동 생성 비활성화
    topics:
      - name: order.created
        partitions: 3
        replication-factor: 1
      - name: payment.processed
        partitions: 1
        replication-factor: 1
    # 목록에 없는 토픽 사용 시 TimeoutException 발생
    # → 운영 환경과 동일한 제약 조건 테스트
```

**장점:**
- 운영 환경과 동일한 토픽 정책
- 잘못된 토픽 사용 방지

**단점:**
- 모든 토픽을 명시해야 함
- 토픽 누락 시 테스트 실패

#### Kafka 토픽 설정 옵션

| 설정 | 설명 | 기본값 | 예시 |
|-----|------|-------|-----|
| `retention.ms` | 메시지 보관 시간 (밀리초) | 604800000 (7일) | 86400000 (1일) |
| `retention.bytes` | 파티션당 최대 크기 (바이트) | -1 (무제한) | 1073741824 (1GB) |
| `max.message.bytes` | 최대 메시지 크기 (바이트) | 1048576 (1MB) | 10485760 (10MB) |
| `compression.type` | 압축 방식 | producer | gzip, snappy, lz4, zstd |
| `cleanup.policy` | 정리 정책 | delete | delete, compact |

#### 실전 예시: Store API

```yaml
# store-api/src/test/resources/application-test.yml
testcontainers:
  postgres:
    enabled: true
    replica-enabled: true
    schema-location: project:store-api/sql/schema.sql

  kafka:
    enabled: true
    topics:
      - name: store.info.updated
        partitions: 3
        replication-factor: 1
        config:
          retention.ms: 604800000  # 7일
      - name: store.deleted
        partitions: 1
        replication-factor: 1
```

### 2. 테스트 실행

```bash
# 전체 테스트 실행
./gradlew test

# 특정 테스트만 실행
./gradlew test --tests OrderRepositoryTest

# 통합 테스트만 실행 (태그 사용 시)
./gradlew test --tests "*IntegrationTest"
```

### 3. 프로덕션 설정 (선택사항)

Primary-Replica 라우팅을 프로덕션에서도 사용하려면:

#### application.yml:
```yaml
spring:
  datasource:
    master:
      url: jdbc:postgresql://master-db-host:5432/production_db
      username: app_user
      password: ${DB_PASSWORD}
      hikari:
        maximum-pool-size: 10
        minimum-idle: 5

    replica:
      url: jdbc:postgresql://replica-db-host:5432/production_db
      username: app_user
      password: ${DB_PASSWORD}
      hikari:
        maximum-pool-size: 20
        minimum-idle: 10

platform:
  datasource:
    replica-enabled: true
```

#### DataSourceConfig.kt:
```kotlin
package com.groom.yourservice.config

import com.zaxxer.hikari.HikariDataSource
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@Profile("!test")  // 테스트 환경 제외
class DataSourceConfig {

    @Bean
    @ConfigurationProperties("spring.datasource.master")
    fun masterDataSourceProperties() = DataSourceProperties()

    @Bean
    fun masterDataSource(masterDataSourceProperties: DataSourceProperties): HikariDataSource {
        return masterDataSourceProperties
            .initializeDataSourceBuilder()
            .type(HikariDataSource::class.java)
            .build()
    }

    @Bean
    @ConfigurationProperties("spring.datasource.replica")
    fun replicaDataSourceProperties() = DataSourceProperties()

    @Bean
    fun replicaDataSource(replicaDataSourceProperties: DataSourceProperties): HikariDataSource {
        return replicaDataSourceProperties
            .initializeDataSourceBuilder()
            .type(HikariDataSource::class.java)
            .build()
    }
}
```

**주의:** routingDataSource와 dataSource Bean은 **datasource-starter**가 자동으로 생성합니다!

---

## 트러블슈팅

### 문제 1: "Could not resolve dependency" 에러

**증상:**
```
Could not resolve com.groom.platform:testcontainers-starter:1.0.0-SNAPSHOT.
```

**원인:**
- GitHub Token이 설정되지 않음
- Token 권한 부족

**해결:**

1. 환경 변수 확인:
   ```bash
   echo $GITHUB_ACTOR
   echo $GITHUB_TOKEN
   ```

2. 환경 변수가 없으면 [로컬 환경 설정](#2-github-token-설정) 참고

3. Gradle 캐시 삭제 후 재시도:
   ```bash
   ./gradlew build --refresh-dependencies
   ```

### 문제 2: Docker 컨테이너 시작 실패

**증상:**
```
org.testcontainers.containers.ContainerLaunchException: Container startup failed
```

**원인:**
- Docker가 실행되지 않음

**해결:**

1. Docker 실행 확인:
   ```bash
   docker ps
   ```

2. Docker Desktop 시작:
   - macOS: Applications에서 Docker 실행
   - Windows: Docker Desktop 실행

3. Docker 메모리 설정 확인:
   - Docker Desktop → Settings → Resources
   - Memory: 최소 4GB 권장

### 문제 3: 스키마 파일을 찾을 수 없음

**증상:**
```
Schema file not found: classpath:db/schema.sql
```

**원인:**
- 스키마 파일 경로가 잘못됨
- 파일이 존재하지 않음

**해결:**

1. 파일 위치 확인:
   ```
   src/test/resources/db/schema.sql
   ```

2. application-test.yml 경로 확인:
   ```yaml
   testcontainers:
     postgres:
       schema-location: classpath:db/schema.sql  # classpath: 접두사 필수
   ```

3. 파일이 없으면 생성:
   ```bash
   mkdir -p src/test/resources/db
   touch src/test/resources/db/schema.sql
   ```

### 문제 4: "Multiple DataSource beans found" 에러

**증상:**
```
Expected single matching bean but found 2: masterDataSource, replicaDataSource
```

**원인:**
- 기존 TestDataSourceConfig.kt 파일이 남아있음

**해결:**

다음 파일들 삭제:
```
src/test/kotlin/.../config/TestDataSourceConfig.kt
src/test/kotlin/.../config/TestRedisConfig.kt
```

### 문제 5: 테스트 실행 시 느림

**원인:**
- 매번 컨테이너를 새로 시작함

**해결:**

`withReuse(true)`가 활성화되어 있는지 확인 (기본값):
```yaml
# 컨테이너 재사용 활성화됨 (기본)
testcontainers:
  postgres:
    enabled: true
```

### 문제 6: Primary/Replica 라우팅이 작동하지 않음

**증상:**
- `@Transactional(readOnly=true)`를 사용해도 Primary DB 사용

**원인:**
- @Transactional이 적용되지 않음
- Proxy 문제

**해결:**

1. @Transactional 위치 확인:
   ```kotlin
   // ❌ 잘못된 위치 (private 메서드)
   @Transactional(readOnly = true)
   private fun getOrders() { ... }

   // ✅ 올바른 위치 (public 메서드)
   @Transactional(readOnly = true)
   fun getOrders() { ... }
   ```

2. 로그 확인:
   ```yaml
   logging:
     level:
       org.springframework.jdbc: DEBUG
   ```

3. 실제 라우팅 확인:
   ```kotlin
   @Test
   @Transactional(readOnly = true)
   fun test() {
       val connection = dataSource.connection
       println("JDBC URL: ${connection.metaData.url}")
       // Replica URL이 출력되어야 함
   }
   ```

---

## 추가 도움말

### 로그 레벨 조정

개발 중 디버깅이 필요하면:

```yaml
# application-test.yml
logging:
  level:
    com.groom: DEBUG
    org.springframework.jdbc: DEBUG
    org.hibernate.SQL: DEBUG
    org.testcontainers: INFO
```

### 컨테이너 정보 확인

테스트 실행 중 컨테이너 정보 확인:

```bash
# 실행 중인 컨테이너 확인
docker ps

# 컨테이너 로그 확인
docker logs <container-id>

# PostgreSQL 접속
docker exec -it <postgres-container-id> psql -U test -d testdb
```

### IDE 설정 (IntelliJ IDEA)

1. **환경 변수 설정:**
   - Run → Edit Configurations
   - Environment variables: `GITHUB_ACTOR=...;GITHUB_TOKEN=...`

2. **Docker 플러그인 설치:**
   - Settings → Plugins → "Docker" 검색 및 설치

3. **Gradle 설정:**
   - Settings → Build, Execution, Deployment → Build Tools → Gradle
   - Build and run using: IntelliJ IDEA (권장)

---

## 연락처

문제가 해결되지 않으면:
- **GitHub Issues:** https://github.com/GroomC4/c4ang-platform-core/issues
- **팀 채널:** Slack #platform-support
- **유지보수 담당자:** [담당자 이름]

---

## 참고 자료

- [Testcontainers 공식 문서](https://www.testcontainers.org/)
- [Spring Boot Testing](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.testing)
- [Platform Core GitHub](https://github.com/GroomC4/c4ang-platform-core)
- [유지보수 가이드](./MAINTAINER_GUIDE.md)

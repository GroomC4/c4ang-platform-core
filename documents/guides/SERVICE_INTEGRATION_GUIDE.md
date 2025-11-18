# 서비스 통합 가이드 (실전편)

**도메인 서비스에서 testcontainers-starter를 적용하는 완벽 가이드**

> ⚠️ **중요**: 이 가이드는 **멀티 모듈 Gradle 프로젝트**를 기준으로 작성되었습니다.
> 단일 모듈 프로젝트는 설정이 더 간단하므로 필요한 부분만 참고하세요.

---

## 목차

1. [시작하기 전에](#시작하기-전에)
2. [필수 준비 사항](#필수-준비-사항)
3. [통합 테스트 환경 구축 (3단계)](#통합-테스트-환경-구축-3단계)
4. [테스트 작성](#테스트-작성)
5. [트러블슈팅](#트러블슈팅)

---

## 시작하기 전에

### testcontainers-starter가 제공하는 것

✅ **자동으로 시작되는 인프라**
- PostgreSQL (Primary/Replica)
- Redis
- Kafka
- Schema Registry

✅ **자동으로 설정되는 Bean**
- DataSource (Primary-Replica 라우팅 포함)
- RedisConnectionFactory
- KafkaTemplate

✅ **코드 제로 통합 테스트**
- 설정 파일만으로 모든 인프라 자동 시작
- `@Transactional(readOnly=true)` → Replica DB 자동 라우팅
- `@Transactional(readOnly=false)` → Primary DB 자동 라우팅

---

## 필수 준비 사항

### 1. Docker 실행

Testcontainers는 Docker가 필요합니다.

```bash
# Docker 실행 확인
docker ps

# Docker가 실행되지 않으면 Docker Desktop 실행
```

### 2. GitHub Token 설정

platform-core 패키지는 GitHub Packages에 배포되어 있습니다.

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

### 3. 프로젝트 구조 확인

**멀티 모듈 Gradle 프로젝트 구조:**

```
프로젝트 루트/
├── settings.gradle.kts          ← 프로젝트 루트를 나타내는 파일
├── build.gradle.kts
├── store-api/                    ← 도메인 서비스 모듈
│   ├── build.gradle.kts
│   ├── sql/
│   │   └── schema.sql            ← 스키마 파일 (권장 위치)
│   └── src/
│       ├── main/
│       └── test/
│           ├── kotlin/
│           │   └── com/groom/store/
│           │       └── common/
│           │           └── IntegrationTestBase.kt  ← 필수!
│           └── resources/
│               └── application-test.yml            ← 선택사항
└── order-api/                    ← 다른 도메인 서비스 모듈
```

**단일 모듈 프로젝트 구조:**

```
프로젝트 루트/
├── build.gradle.kts
└── src/
    ├── main/
    └── test/
        ├── kotlin/
        │   └── com/groom/yourservice/
        │       └── common/
        │           └── IntegrationTestBase.kt
        └── resources/
            ├── db/
            │   └── schema.sql
            └── application-test.yml
```

---

## 통합 테스트 환경 구축 (3단계)

### Step 1: 의존성 추가

**build.gradle.kts** (모듈 또는 루트):

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
    // 테스트용 (필수)
    testImplementation("com.groom.platform:testcontainers-starter:1.2.2-RC10")

    // PostgreSQL Driver (필수)
    runtimeOnly("org.postgresql:postgresql")
}
```

**의존성 다운로드:**

```bash
./gradlew build --refresh-dependencies
```

---

### Step 2: IntegrationTestBase 클래스 생성 ⭐ **필수!**

**위치:** `src/test/kotlin/com/groom/yourservice/common/IntegrationTestBase.kt`

```kotlin
package com.groom.yourservice.common

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/**
 * 모든 통합 테스트가 상속받을 Base 클래스
 *
 * 이 클래스는 Testcontainers 자동 구성을 위해 필수입니다.
 * application-test.yml이 없어도 작동합니다.
 */
@SpringBootTest(
    properties = [
        // ===== PostgreSQL 설정 =====
        "testcontainers.postgres.enabled=true",
        "testcontainers.postgres.replica-enabled=true",

        // 스키마 파일 경로 (3가지 방법 중 선택)
        // 1️⃣ 멀티 모듈: project: 스킴 (권장!)
        "testcontainers.postgres.schema-location=project:store-api/sql/schema.sql",
        //    ↑ 프로젝트 루트 기준 (settings.gradle.kts가 있는 위치)

        // 2️⃣ 단일 모듈: classpath: 스킴
        // "testcontainers.postgres.schema-location=classpath:db/schema.sql",
        //    ↑ src/test/resources/db/schema.sql

        // 3️⃣ 절대 경로: file: 스킴
        // "testcontainers.postgres.schema-location=file:/absolute/path/to/schema.sql",

        // ===== Redis 설정 =====
        "testcontainers.redis.enabled=true",

        // ===== Kafka 설정 =====
        "testcontainers.kafka.enabled=true",
        "testcontainers.kafka.auto-create-topics=true",

        // Kafka 토픽 사전 정의 (선택사항)
        "testcontainers.kafka.topics[0].name=store.info.updated",
        "testcontainers.kafka.topics[0].partitions=3",
        "testcontainers.kafka.topics[0].replication-factor=1",

        "testcontainers.kafka.topics[1].name=store.deleted",
        "testcontainers.kafka.topics[1].partitions=1",
        "testcontainers.kafka.topics[1].replication-factor=1",

        // ===== Schema Registry 설정 (Kafka Avro 사용 시) =====
        // "testcontainers.schema-registry.enabled=true",
    ]
)
@ActiveProfiles("test")
abstract class IntegrationTestBase
```

**⚠️ 주의사항:**

1. **스키마 파일 경로는 프로젝트에 맞게 수정하세요**
   - 멀티 모듈: `project:{모듈명}/sql/schema.sql`
   - 단일 모듈: `classpath:db/schema.sql`

2. **Kafka 토픽은 실제 사용하는 토픽으로 변경하세요**

3. **필요 없는 인프라는 `enabled=false`로 비활성화하세요**
   ```kotlin
   "testcontainers.redis.enabled=false",   // Redis 사용 안 함
   "testcontainers.kafka.enabled=false",   // Kafka 사용 안 함
   ```

---

### Step 3: 스키마 파일 준비

**멀티 모듈 프로젝트 (권장 위치):**

```
store-api/
└── sql/
    └── schema.sql
```

**단일 모듈 프로젝트:**

```
src/test/resources/
└── db/
    └── schema.sql
```

**schema.sql 예시:**

```sql
-- 테이블 생성
CREATE TABLE IF NOT EXISTS stores (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    address VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 인덱스 생성
CREATE INDEX idx_stores_name ON stores(name);

-- 초기 데이터 (선택사항)
INSERT INTO stores (name, address) VALUES ('Test Store', 'Seoul');
```

---

## 테스트 작성

### 1. 기본 Repository 테스트

```kotlin
package com.groom.store.repository

import com.groom.store.common.IntegrationTestBase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Transactional

class StoreRepositoryTest : IntegrationTestBase() {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    @Transactional(readOnly = false)  // PRIMARY DB 사용
    fun `스토어를 생성한다`() {
        // Given
        val sql = """
            INSERT INTO stores (name, address)
            VALUES (?, ?)
        """

        // When
        val result = jdbcTemplate.update(sql, "New Store", "Busan")

        // Then
        assert(result == 1)
    }

    @Test
    @Transactional(readOnly = true)  // REPLICA DB 사용
    fun `스토어 목록을 조회한다`() {
        // When
        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM stores",
            Int::class.java
        )

        // Then
        assert(count != null)
        assert(count >= 0)
    }
}
```

### 2. JPA Repository 테스트

```kotlin
package com.groom.store.repository

import com.groom.store.common.IntegrationTestBase
import com.groom.store.domain.Store
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional

class StoreJpaRepositoryTest : IntegrationTestBase() {

    @Autowired
    private lateinit var storeRepository: StoreRepository

    @Test
    @Transactional(readOnly = false)
    fun `JPA로 스토어를 저장한다`() {
        // Given
        val store = Store(
            name = "JPA Store",
            address = "Seoul"
        )

        // When
        val saved = storeRepository.save(store)

        // Then
        assert(saved.id != null)
    }

    @Test
    @Transactional(readOnly = true)
    fun `JPA로 스토어를 조회한다`() {
        // When
        val stores = storeRepository.findAll()

        // Then
        assert(stores is List)
    }
}
```

### 3. Redis 테스트

```kotlin
package com.groom.store.cache

import com.groom.store.common.IntegrationTestBase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.RedisTemplate

class RedisCacheTest : IntegrationTestBase() {

    @Autowired
    private lateinit var redisTemplate: RedisTemplate<String, String>

    @Test
    fun `Redis에 데이터를 저장하고 조회한다`() {
        // Given
        val key = "test:store:1"
        val value = "Store Name"

        // When
        redisTemplate.opsForValue().set(key, value)
        val result = redisTemplate.opsForValue().get(key)

        // Then
        assert(result == value)
    }
}
```

### 4. Kafka 테스트

```kotlin
package com.groom.store.messaging

import com.groom.store.common.IntegrationTestBase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.core.KafkaTemplate
import java.util.concurrent.TimeUnit

class KafkaProducerTest : IntegrationTestBase() {

    @Autowired
    private lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @Test
    fun `Kafka로 메시지를 발행한다`() {
        // Given
        val topic = "store.info.updated"
        val message = "Store updated: 12345"

        // When
        val future = kafkaTemplate.send(topic, message)

        // Then
        val result = future.get(10, TimeUnit.SECONDS)
        assert(result.recordMetadata.topic() == topic)
    }
}
```

---

## 테스트 실행

```bash
# 전체 테스트 실행
./gradlew test

# 특정 테스트만 실행
./gradlew test --tests StoreRepositoryTest

# 통합 테스트만 실행 (패턴 매칭)
./gradlew test --tests "*IntegrationTest"
```

**예상 로그:**

```
🚀 Initializing shared PostgreSQL container...
✅ PostgreSQL Primary container started and ready (jdbc:postgresql://localhost:xxxxx/testdb)
📄 PostgreSQL Replica: Using same container as Primary (single container mode)

🚀 Starting shared Redis container...
✅ Redis container started and ready (localhost:xxxxx)

🚀 Starting shared Kafka container...
✅ Kafka container started and ready (PLAINTEXT://localhost:xxxxx)
   - Auto Create Topics: Enabled
   - Default Partitions: 1
   - Replication Factor: 1

✅ Kafka predefined topics created:
   - store.info.updated (partitions=3, replication-factor=1)
   - store.deleted (partitions=1, replication-factor=1)

✅ Master DataSource configured: jdbc:postgresql://localhost:xxxxx/testdb
✅ Replica DataSource configured: jdbc:postgresql://localhost:xxxxx/testdb
✅ Routing DataSource configured (MASTER/REPLICA)
```

---

## 트러블슈팅

### 문제 1: 컨테이너가 시작되지 않음

**증상:**

```
Could not initialize class com.groom.platform.testcontainers.container.SharedContainers
```

**원인:**
- Docker가 실행되지 않음

**해결:**

```bash
# Docker 실행 확인
docker ps

# Docker Desktop 실행 (macOS/Windows)
# 또는
sudo systemctl start docker  # Linux
```

---

### 문제 2: DataSource Bean을 찾을 수 없음

**증상:**

```
No qualifying bean of type 'javax.sql.DataSource' available
```

**원인:**
- IntegrationTestBase를 상속받지 않음
- `testcontainers.postgres.enabled=true` 설정 누락

**해결:**

```kotlin
// ❌ 잘못된 코드
@SpringBootTest
class StoreRepositoryTest {  // IntegrationTestBase 미상속!
    // ...
}

// ✅ 올바른 코드
class StoreRepositoryTest : IntegrationTestBase() {  // 상속 필수!
    // ...
}
```

---

### 문제 3: 스키마 파일을 찾을 수 없음

**증상:**

```
java.io.FileNotFoundException: class path resource [db/schema.sql] cannot be opened
```

**원인:**
- 스키마 파일 경로가 잘못됨
- 멀티 모듈에서 classpath: 스킴 사용

**해결:**

**멀티 모듈 프로젝트:**

```kotlin
// ❌ 잘못된 경로
"testcontainers.postgres.schema-location=classpath:db/schema.sql"
//   ↑ 멀티 모듈에서는 classpath가 모호함!

// ✅ 올바른 경로
"testcontainers.postgres.schema-location=project:store-api/sql/schema.sql"
//   ↑ 프로젝트 루트 기준 명시적 경로
```

**단일 모듈 프로젝트:**

```
src/test/resources/
└── db/
    └── schema.sql  ← 이 위치에 파일이 있어야 함!
```

---

### 문제 4: Kafka TimeoutException

**증상:**

```
org.apache.kafka.common.errors.TimeoutException:
Topic my-topic not present in metadata after 60000 ms.
```

**원인:**
- 토픽이 자동 생성되지 않음
- `auto-create-topics=false` 설정

**해결:**

```kotlin
// 옵션 1: 자동 생성 활성화 (기본값)
"testcontainers.kafka.enabled=true",
"testcontainers.kafka.auto-create-topics=true",  // 명시적으로 true

// 옵션 2: 토픽 사전 정의
"testcontainers.kafka.topics[0].name=my-topic",
"testcontainers.kafka.topics[0].partitions=1",
"testcontainers.kafka.topics[0].replication-factor=1",
```

---

### 문제 5: "Multiple DataSource beans found" 에러

**증상:**

```
Expected single matching bean but found 2: masterDataSource, replicaDataSource
```

**원인:**
- 기존 테스트 설정 파일과 충돌

**해결:**

다음 파일들을 **삭제**하세요:

```
src/test/kotlin/.../config/TestDataSourceConfig.kt
src/test/kotlin/.../config/TestRedisConfig.kt
src/test/kotlin/.../extension/ContainerExtension.kt
```

testcontainers-starter가 자동으로 모든 Bean을 생성합니다!

---

### 문제 6: GitHub Packages 의존성 다운로드 실패

**증상:**

```
Could not resolve com.groom.platform:testcontainers-starter:1.2.2-RC10
```

**원인:**
- GitHub Token이 설정되지 않음

**해결:**

```bash
# 환경 변수 확인
echo $GITHUB_ACTOR
echo $GITHUB_TOKEN

# 환경 변수가 없으면 설정
export GITHUB_ACTOR="your-username"
export GITHUB_TOKEN="ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"

# Gradle 캐시 삭제 후 재시도
./gradlew build --refresh-dependencies
```

---

### 문제 7: 테스트 실행이 느림

**원인:**
- 매번 컨테이너를 새로 시작함

**해결:**

컨테이너는 **JVM 전역 싱글톤**으로 자동 재사용됩니다.
첫 테스트만 느리고, 이후 테스트는 빠르게 실행됩니다.

```
첫 테스트:   30초 (컨테이너 시작)
두 번째 테스트: 2초 (컨테이너 재사용!)
세 번째 테스트: 2초 (컨테이너 재사용!)
```

**추가 최적화:**

```bash
# Gradle Daemon 활성화
echo "org.gradle.daemon=true" >> gradle.properties

# 병렬 테스트 실행
echo "org.gradle.parallel=true" >> gradle.properties
```

---

## application-test.yml 사용 (선택사항)

IntegrationTestBase 패턴 대신 YAML 설정을 사용할 수도 있습니다.

**⚠️ 주의: 멀티 모듈에서는 IntegrationTestBase 패턴이 더 안정적입니다!**

**src/test/resources/application-test.yml:**

```yaml
spring:
  profiles:
    active: test

testcontainers:
  postgres:
    enabled: true
    replica-enabled: true
    schema-location: project:store-api/sql/schema.sql

  redis:
    enabled: true

  kafka:
    enabled: true
    auto-create-topics: true
    topics:
      - name: store.info.updated
        partitions: 3
        replication-factor: 1
      - name: store.deleted
        partitions: 1
        replication-factor: 1

# JPA 설정
spring:
  jpa:
    hibernate:
      ddl-auto: validate  # 스키마 검증만 (create 사용 금지!)
    show-sql: true

# 로깅
logging:
  level:
    com.groom: DEBUG
    org.springframework.jdbc: DEBUG
```

**테스트 클래스:**

```kotlin
@SpringBootTest
@ActiveProfiles("test")  // application-test.yml 로드
class StoreRepositoryTest {
    // IntegrationTestBase 상속 없이 사용 가능
}
```

---

## 스키마 파일 경로 가이드

### project: 스킴 (멀티 모듈 권장!)

```kotlin
"testcontainers.postgres.schema-location=project:store-api/sql/schema.sql"
```

**동작 방식:**
- 프로젝트 루트 = `System.getProperty("user.dir")` = settings.gradle.kts가 있는 위치
- 프로젝트 루트 + `store-api/sql/schema.sql`

**프로젝트 구조:**
```
프로젝트 루트/              ← project: 시작점
├── settings.gradle.kts
└── store-api/
    └── sql/
        └── schema.sql
```

### classpath: 스킴 (단일 모듈 권장!)

```kotlin
"testcontainers.postgres.schema-location=classpath:db/schema.sql"
```

**동작 방식:**
- `src/test/resources/db/schema.sql`

**프로젝트 구조:**
```
src/test/resources/
└── db/
    └── schema.sql
```

### file: 스킴 (특수한 경우)

```kotlin
"testcontainers.postgres.schema-location=file:/absolute/path/to/schema.sql"
```

**동작 방식:**
- 파일 시스템 절대 경로

---

## 추가 도움말

### 컨테이너 정보 확인

```bash
# 실행 중인 컨테이너 확인
docker ps

# PostgreSQL 접속
docker exec -it <postgres-container-id> psql -U test -d testdb

# Redis 접속
docker exec -it <redis-container-id> redis-cli

# Kafka 토픽 목록 확인
docker exec -it <kafka-container-id> kafka-topics --list --bootstrap-server localhost:9092
```

### 로그 레벨 조정

```kotlin
// IntegrationTestBase.kt
@SpringBootTest(
    properties = [
        // ... 기존 설정 ...

        // 로그 레벨
        "logging.level.com.groom=DEBUG",
        "logging.level.org.springframework.jdbc=DEBUG",
        "logging.level.org.hibernate.SQL=DEBUG",
        "logging.level.org.testcontainers=INFO",
    ]
)
```

---

## 연락처

문제가 해결되지 않으면:
- **GitHub Issues:** https://github.com/GroomC4/c4ang-platform-core/issues
- **팀 채널:** Slack #platform-support
- **유지보수 담당자:** @hayden-han

---

## 참고 자료

- [Testcontainers 공식 문서](https://www.testcontainers.org/)
- [Spring Boot Testing](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.testing)
- [Platform Core GitHub](https://github.com/GroomC4/c4ang-platform-core)
- [CHANGELOG](../../CHANGELOG.md)

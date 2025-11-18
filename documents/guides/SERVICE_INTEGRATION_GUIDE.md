# 서비스 통합 가이드 (실전편)

**도메인 서비스에서 platform-core 패키지를 적용하는 완벽 가이드**

> ⚠️ **중요**: 이 가이드는 **멀티 모듈 Gradle 프로젝트**를 기준으로 작성되었습니다.
> 단일 모듈 프로젝트는 설정이 더 간단하므로 필요한 부분만 참고하세요.

---

## 목차

### 테스트 환경
1. [시작하기 전에 (테스트)](#시작하기-전에-테스트)
2. [필수 준비 사항](#필수-준비-사항)
3. [통합 테스트 환경 구축 (3단계)](#통합-테스트-환경-구축-3단계)
4. [테스트 작성](#테스트-작성)

### 프로덕션 환경
5. [프로덕션 환경 구축 (3단계)](#프로덕션-환경-구축-3단계)
6. [프로덕션 사용법](#프로덕션-사용법)

### 공통
7. [트러블슈팅](#트러블슈팅)
8. [추가 도움말](#추가-도움말)

---

## 시작하기 전에 (테스트)

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
│           └── kotlin/
│               └── com/groom/store/
│                   └── common/
│                       └── IntegrationTestBase.kt  ← 필수! (이것만 있으면 됨)
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
        │           └── IntegrationTestBase.kt  ← 필수! (이것만 있으면 됨)
        └── resources/
            └── db/
                └── schema.sql  ← classpath: 스킴 사용 시
```

> 📝 **참고**: `application-test.yml`은 더 이상 필요하지 않습니다!
> 모든 설정은 `IntegrationTestBase`에서 관리합니다.

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

/**
 * 모든 통합 테스트가 상속받을 Base 클래스
 *
 * ⚠️ 중요: 이 클래스만으로 모든 설정이 완료됩니다.
 * application-test.yml이 없어도 작동합니다.
 *
 * @ActiveProfiles를 사용하지 않는 이유:
 * - @SpringBootTest properties로 모든 설정을 명시적으로 제공
 * - application-test.yml과 중복 설정 방지
 * - 설정이 한 곳에만 있어 명확함
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

**스키마 파일 위치 (멀티 모듈):**

```
프로젝트 루트/
├── settings.gradle.kts
└── store-api/              ← 모듈 디렉토리
    ├── sql/
    │   └── schema.sql      ← 여기에 DDL 작성!
    └── src/
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

**IntegrationTestBase에서 경로 지정:**

```kotlin
@SpringBootTest(
    properties = [
        // ✅ 올바른 경로 (프로젝트 루트 기준)
        "testcontainers.postgres.schema-location=project:store-api/sql/schema.sql",

        // ❌ 잘못된 경로
        // "testcontainers.postgres.schema-location=classpath:sql/schema.sql"
        // ↑ 멀티 모듈에서는 동작하지 않음!
    ]
)
abstract class IntegrationTestBase
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

# 프로덕션 환경

## 프로덕션 환경 구축 (3단계)

프로덕션 환경에서 Primary-Replica 자동 라우팅을 사용하려면 `datasource-starter`를 적용합니다.

---

### Step 1: 의존성 추가

**build.gradle.kts:**

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
    // ✅ 프로덕션용 DataSource 라우팅
    implementation("com.groom.platform:datasource-starter:1.2.2-RC10")

    // PostgreSQL Driver (필수)
    runtimeOnly("org.postgresql:postgresql")

    // ⚠️ 주의: testcontainers-starter와 함께 사용하지 마세요!
    // testImplementation("com.groom.platform:testcontainers-starter:...")  ← 충돌!
}
```

---

### Step 2: application.yml 설정

**src/main/resources/application.yml:**

```yaml
spring:
  datasource:
    master:
      jdbc-url: jdbc:postgresql://master-db-host:5432/production_db
      username: ${DB_USERNAME}
      password: ${DB_PASSWORD}
      driver-class-name: org.postgresql.Driver
      hikari:
        maximum-pool-size: 10
        minimum-idle: 5
        connection-timeout: 30000
        idle-timeout: 600000
        max-lifetime: 1800000

    replica:
      jdbc-url: jdbc:postgresql://replica-db-host:5432/production_db
      username: ${DB_USERNAME}
      password: ${DB_PASSWORD}
      driver-class-name: org.postgresql.Driver
      hikari:
        maximum-pool-size: 20
        minimum-idle: 10
        connection-timeout: 30000
        idle-timeout: 600000
        max-lifetime: 1800000

# JPA 설정
spring:
  jpa:
    hibernate:
      ddl-auto: validate  # 프로덕션에서는 validate만!
    show-sql: false       # 프로덕션에서는 false
    properties:
      hibernate:
        format_sql: true
        default_batch_fetch_size: 100
```

**⚠️ 주의사항:**

1. **DB 비밀번호는 환경 변수로 관리**
   ```yaml
   password: ${DB_PASSWORD}  # 환경 변수
   ```

2. **HikariCP 설정 최적화**
   - Replica는 Primary보다 더 많은 연결 허용 (읽기 부하가 더 큼)
   - connection-timeout, idle-timeout 적절히 조정

3. **ddl-auto는 validate만 사용**
   ```yaml
   ddl-auto: validate  # create, update 사용 금지!
   ```

---

### Step 3: DataSourceConfig 클래스 생성 (선택사항)

**기본 설정으로 충분한 경우 생략 가능!**

`datasource-starter`가 자동으로 다음 Bean을 생성합니다:
- `masterDataSource`
- `replicaDataSource`
- `routingDataSource`
- `dataSource` (Primary Bean)

**커스텀 설정이 필요한 경우:**

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
    fun masterDataSource(
        masterDataSourceProperties: DataSourceProperties
    ): HikariDataSource {
        return masterDataSourceProperties
            .initializeDataSourceBuilder()
            .type(HikariDataSource::class.java)
            .build()
            .apply {
                // 추가 설정 (선택사항)
                poolName = "MasterHikariPool"
                isReadOnly = false
            }
    }

    @Bean
    @ConfigurationProperties("spring.datasource.replica")
    fun replicaDataSourceProperties() = DataSourceProperties()

    @Bean
    fun replicaDataSource(
        replicaDataSourceProperties: DataSourceProperties
    ): HikariDataSource {
        return replicaDataSourceProperties
            .initializeDataSourceBuilder()
            .type(HikariDataSource::class.java)
            .build()
            .apply {
                // 추가 설정 (선택사항)
                poolName = "ReplicaHikariPool"
                isReadOnly = true  // Replica는 읽기 전용
            }
    }
}
```

**⚠️ 주의:**
- `routingDataSource`와 `dataSource` Bean은 **절대 직접 생성하지 마세요!**
- `datasource-starter`가 자동으로 생성합니다

---

## 프로덕션 사용법

### 1. 기본 사용법

**자동 라우팅:**

```kotlin
@Service
class StoreService(
    private val storeRepository: StoreRepository
) {
    @Transactional(readOnly = false)  // PRIMARY DB
    fun createStore(name: String): Store {
        return storeRepository.save(Store(name = name))
    }

    @Transactional(readOnly = true)  // REPLICA DB
    fun getStore(id: Long): Store? {
        return storeRepository.findById(id).orElse(null)
    }

    @Transactional(readOnly = true)  // REPLICA DB
    fun getAllStores(): List<Store> {
        return storeRepository.findAll()
    }
}
```

**동작 방식:**
- `@Transactional(readOnly = false)` → **PRIMARY DB** 사용
- `@Transactional(readOnly = true)` → **REPLICA DB** 사용
- `@Transactional` (기본값) → **PRIMARY DB** 사용

---

### 2. Controller 예시

```kotlin
@RestController
@RequestMapping("/api/stores")
class StoreController(
    private val storeService: StoreService
) {
    @PostMapping
    fun createStore(@RequestBody request: CreateStoreRequest): StoreResponse {
        val store = storeService.createStore(request.name)  // PRIMARY DB
        return StoreResponse.from(store)
    }

    @GetMapping("/{id}")
    fun getStore(@PathVariable id: Long): StoreResponse {
        val store = storeService.getStore(id)  // REPLICA DB
            ?: throw NotFoundException("Store not found")
        return StoreResponse.from(store)
    }

    @GetMapping
    fun getAllStores(): List<StoreResponse> {
        return storeService.getAllStores()  // REPLICA DB
            .map { StoreResponse.from(it) }
    }
}
```

---

### 3. 복잡한 트랜잭션

**쓰기 작업 후 바로 읽기:**

```kotlin
@Transactional(readOnly = false)  // PRIMARY 사용 (일관성 보장)
fun createAndReturn(name: String): Store {
    val store = storeRepository.save(Store(name = name))

    // 같은 트랜잭션 내에서는 PRIMARY DB 사용
    return storeRepository.findById(store.id!!).get()
}
```

**읽기 전용 트랜잭션 내에서 쓰기 시도 (에러!):**

```kotlin
@Transactional(readOnly = true)  // REPLICA DB
fun wrongUsage(id: Long) {
    val store = storeRepository.findById(id).get()
    store.name = "Updated"
    storeRepository.save(store)  // ❌ 에러 발생! (REPLICA는 읽기 전용)
}
```

---

### 4. 주의사항

#### ⚠️ Replication Lag

**문제:**
```kotlin
@Transactional(readOnly = false)
fun updateStore(id: Long, name: String) {
    val store = storeRepository.findById(id).get()
    store.name = name
    storeRepository.save(store)  // PRIMARY에 저장
}

@Transactional(readOnly = true)
fun getUpdatedStore(id: Long): Store {
    // REPLICA에서 조회 - 복제 지연으로 인해 이전 데이터가 나올 수 있음!
    return storeRepository.findById(id).get()
}
```

**해결:**
```kotlin
// 방법 1: 쓰기 후 바로 읽어야 하면 readOnly = false 사용
@Transactional(readOnly = false)
fun updateAndReturn(id: Long, name: String): Store {
    val store = storeRepository.findById(id).get()
    store.name = name
    return storeRepository.save(store)  // PRIMARY에서 저장 및 조회
}

// 방법 2: 적절한 대기 시간 또는 비동기 처리
```

#### ⚠️ @Transactional 누락

```kotlin
// ❌ 잘못된 코드
fun getStore(id: Long): Store {  // @Transactional 없음!
    return storeRepository.findById(id).get()
    // PRIMARY DB 사용! (기본값)
}

// ✅ 올바른 코드
@Transactional(readOnly = true)
fun getStore(id: Long): Store {
    return storeRepository.findById(id).get()
    // REPLICA DB 사용
}
```

#### ⚠️ Private 메서드

```kotlin
class StoreService {
    // ❌ @Transactional이 적용되지 않음!
    @Transactional(readOnly = true)
    private fun getStore(id: Long): Store {
        return storeRepository.findById(id).get()
    }

    // ✅ Public 메서드에만 적용됨
    @Transactional(readOnly = true)
    fun getStore(id: Long): Store {
        return storeRepository.findById(id).get()
    }
}
```

---

### 5. 모니터링

**HikariCP 메트릭 확인:**

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
```

**주요 메트릭:**
- `hikaricp.connections.active` - 활성 연결 수
- `hikaricp.connections.idle` - 유휴 연결 수
- `hikaricp.connections.pending` - 대기 중인 연결 수

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

## 추가 Spring 설정 (JPA, 로깅)

IntegrationTestBase에서 JPA, 로깅 등의 설정도 함께 지정할 수 있습니다.

### 방법 1: IntegrationTestBase에 모두 포함 (권장!)

```kotlin
@SpringBootTest(
    properties = [
        // Testcontainers 설정
        "testcontainers.postgres.enabled=true",
        "testcontainers.postgres.schema-location=project:store-api/sql/schema.sql",

        // JPA 설정
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.show-sql=true",
        "spring.jpa.properties.hibernate.format_sql=true",

        // 로깅 설정
        "logging.level.com.groom=DEBUG",
        "logging.level.org.springframework.jdbc=DEBUG",
        "logging.level.org.hibernate.SQL=DEBUG",
    ]
)
abstract class IntegrationTestBase
```

**장점:**
- ✅ 모든 설정이 한 곳에 (가장 명확!)
- ✅ Git으로 설정 추적 용이
- ✅ application-test.yml 불필요

---

### 방법 2: application-test.yml 분리 (설정이 매우 많은 경우만)

**IntegrationTestBase.kt:**
```kotlin
@SpringBootTest(
    properties = [
        "testcontainers.postgres.enabled=true",
        // Testcontainers 설정만
    ]
)
@ActiveProfiles("test")  // application-test.yml 로드
abstract class IntegrationTestBase
```

**src/test/resources/application-test.yml:**
```yaml
# JPA, 로깅 등 Testcontainers와 무관한 설정만
spring:
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: true

logging:
  level:
    com.groom: DEBUG
```

**⚠️ 주의:**
- Testcontainers 설정은 IntegrationTestBase에만!
- application-test.yml에 `testcontainers:` 섹션을 쓰면 중복 발생!

---

## 스키마 파일 경로 가이드

### project: 스킴 (멀티 모듈 - 권장!)

**사용법:**
```kotlin
"testcontainers.postgres.schema-location=project:your-module-name/sql/schema.sql"
```

**동작 방식:**
- `project:` = 프로젝트 루트 (`System.getProperty("user.dir")`)
- 프로젝트 루트 = settings.gradle.kts가 있는 위치
- 경로: 프로젝트 루트 + `your-module-name/sql/schema.sql`

**프로젝트 구조:**
```
프로젝트 루트/              ← project: 시작점
├── settings.gradle.kts     ← 프로젝트 루트를 나타냄
├── store-api/
│   └── sql/
│       └── schema.sql      ← project:store-api/sql/schema.sql
└── order-api/
    └── sql/
        └── schema.sql      ← project:order-api/sql/schema.sql
```

**예시:**
```kotlin
// store-api 모듈
"testcontainers.postgres.schema-location=project:store-api/sql/schema.sql"

// order-api 모듈
"testcontainers.postgres.schema-location=project:order-api/sql/schema.sql"
```

---

### file: 스킴 (절대 경로가 필요한 경우)

**사용법:**
```kotlin
"testcontainers.postgres.schema-location=file:/absolute/path/to/schema.sql"
```

**동작 방식:**
- 파일 시스템 절대 경로

**사용 사례:**
- CI/CD에서 특정 위치의 스키마 파일 사용
- 외부에서 생성된 스키마 파일 참조

---

### ⚠️ classpath: 스킴 (비권장)

**문제점:**
```kotlin
// ❌ 멀티 모듈에서 동작하지 않음!
"testcontainers.postgres.schema-location=classpath:sql/schema.sql"
```

**이유:**
- 멀티 모듈에서 classpath가 모호함
- `src/test/resources`가 어느 모듈의 것인지 불명확
- 컴파일된 클래스패스에 의존

**대신 사용:**
- ✅ `project:` 스킴 사용 (명확함!)

---

### 권장 사항

| 프로젝트 타입 | 권장 스킴 | 스키마 위치 |
|--------------|----------|------------|
| **멀티 모듈** | `project:` | `your-module/sql/schema.sql` |
| **단일 모듈** | `project:` | `sql/schema.sql` |
| **특수한 경우** | `file:` | 절대 경로 |

> 💡 **팁**: 모든 경우에 `project:` 스킴을 사용하는 것이 가장 명확합니다!

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

IntegrationTestBase에서 직접 설정하세요:

```kotlin
@SpringBootTest(
    properties = [
        // Testcontainers 설정
        "testcontainers.postgres.enabled=true",
        // ...

        // 로그 레벨 (원하는 대로 조정)
        "logging.level.com.groom=DEBUG",
        "logging.level.org.springframework.jdbc=DEBUG",
        "logging.level.org.hibernate.SQL=DEBUG",
        "logging.level.org.testcontainers=INFO",
    ]
)
abstract class IntegrationTestBase
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

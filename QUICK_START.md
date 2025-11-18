# 빠른 시작 가이드 (3분 완성!)

**도메인 서비스에 testcontainers-starter 적용하기**

> 🎯 **목표**: 3단계로 통합 테스트 환경 구축
> ⏱️ **소요 시간**: 약 3분

---

## Step 1: 의존성 추가 (1분)

**build.gradle.kts:**

```kotlin
repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.github.com/GroomC4/c4ang-platform-core")
        credentials {
            username = System.getenv("GITHUB_ACTOR")
            password = System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    testImplementation("com.groom.platform:testcontainers-starter:1.2.2-RC10")
    runtimeOnly("org.postgresql:postgresql")
}
```

**의존성 다운로드:**

```bash
./gradlew build --refresh-dependencies
```

---

## Step 2: IntegrationTestBase 생성 (1분)

**위치:** `src/test/kotlin/com/groom/{yourservice}/common/IntegrationTestBase.kt`

```kotlin
package com.groom.yourservice.common

import org.springframework.boot.test.context.SpringBootTest

/**
 * ⚠️ 중요: 이 클래스만으로 모든 설정 완료!
 * @ActiveProfiles를 사용하지 않아 application-test.yml과 중복 설정 방지
 */
@SpringBootTest(
    properties = [
        // PostgreSQL
        "testcontainers.postgres.enabled=true",
        "testcontainers.postgres.replica-enabled=true",
        "testcontainers.postgres.schema-location=project:sql/schema.sql",
        //                                               ↑ 모듈 내 sql/schema.sql

        // Redis (필요 없으면 false)
        "testcontainers.redis.enabled=true",

        // Kafka (필요 없으면 false)
        "testcontainers.kafka.enabled=true",
        "testcontainers.kafka.auto-create-topics=true",

        // Kafka 토픽 (사용하는 토픽으로 변경)
        "testcontainers.kafka.topics[0].name=your.topic.name",
        "testcontainers.kafka.topics[0].partitions=1",
        "testcontainers.kafka.topics[0].replication-factor=1",
    ]
)
abstract class IntegrationTestBase
```

**⚠️ 필수 수정 사항:**

1. `sql/schema.sql` → 실제 스키마 파일 경로 (모듈 내 위치)
2. `your.topic.name` → 실제 Kafka 토픽 이름

---

## Step 3: 스키마 파일 준비 (1분)

**스키마 파일 위치:**

```
프로젝트 루트/
├── settings.gradle.kts
└── your-module/           ← 모듈 디렉토리
    ├── build.gradle.kts
    ├── sql/
    │   └── schema.sql     ← 여기에 DDL 작성!
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
```

**IntegrationTestBase에서 경로 지정:**

```kotlin
// ✅ 올바른 경로 (자동 경로 탐색 - IntelliJ/Gradle 모두 지원)
"testcontainers.postgres.schema-location=project:sql/schema.sql",

// ❌ 잘못된 경로
// "testcontainers.postgres.schema-location=classpath:sql/schema.sql"
// ↑ 멀티 모듈에서는 동작하지 않음!
```

> 💡 **참고**: `project:` 스킴은 자동으로 경로를 탐색합니다 (IntelliJ 실행 시: 모듈 루트 기준, Gradle 명령어: 프로젝트 루트 기준).

---

## 끝! 테스트 작성

```kotlin
package com.groom.yourservice.repository

import com.groom.yourservice.common.IntegrationTestBase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Transactional

class YourRepositoryTest : IntegrationTestBase() {  // ← 상속 필수!

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    @Transactional(readOnly = false)  // PRIMARY DB
    fun `데이터를 저장한다`() {
        val result = jdbcTemplate.update(
            "INSERT INTO your_table (name) VALUES (?)",
            "Test"
        )
        assert(result == 1)
    }

    @Test
    @Transactional(readOnly = true)  // REPLICA DB
    fun `데이터를 조회한다`() {
        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM your_table",
            Int::class.java
        )
        assert(count != null)
    }
}
```

**테스트 실행:**

```bash
./gradlew test
```

---

## 문제 해결

### 1. GitHub Token 에러

```bash
export GITHUB_ACTOR="your-username"
export GITHUB_TOKEN="ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
source ~/.zshrc
```

### 2. Docker 에러

```bash
# Docker가 실행 중인지 확인
docker ps

# Docker Desktop을 실행하세요!
```

### 3. 스키마 파일 못 찾음

**올바른 경로 (자동 탐색):**

```kotlin
// ✅ 올바른 경로 (자동으로 IntelliJ/Gradle 환경 모두 지원)
"testcontainers.postgres.schema-location=project:sql/schema.sql"

// ❌ 잘못된 경로들
"testcontainers.postgres.schema-location=classpath:sql/schema.sql"  // 멀티 모듈에서 안 됨!
```

**파일 위치 확인:**
```
프로젝트 루트/
└── your-module/
    ├── build.gradle.kts
    └── sql/
        └── schema.sql  ← 이 파일이 존재해야 함!
```

### 4. DataSource Bean 못 찾음

```kotlin
// ❌ 잘못됨
@SpringBootTest
class MyTest {  // IntegrationTestBase 미상속!

// ✅ 올바름
class MyTest : IntegrationTestBase() {  // 상속 필수!
```

### 5. 기존 테스트 설정 파일 충돌

**다음 파일들을 삭제하세요:**

```
src/test/kotlin/.../config/TestDataSourceConfig.kt
src/test/kotlin/.../config/TestRedisConfig.kt
src/test/kotlin/.../extension/ContainerExtension.kt
```

testcontainers-starter가 자동으로 모든 Bean을 생성합니다!

---

## 더 자세한 가이드

- **[서비스 통합 가이드](documents/guides/SERVICE_INTEGRATION_GUIDE.md)** - 전체 가이드
- **[트러블슈팅](documents/guides/SERVICE_INTEGRATION_GUIDE.md#트러블슈팅)** - 문제 해결
- **[CHANGELOG](CHANGELOG.md)** - 버전별 변경 사항

---

## 주요 체크리스트

- [ ] GitHub Token 설정 (`$GITHUB_ACTOR`, `$GITHUB_TOKEN`)
- [ ] Docker 실행 중 (`docker ps`)
- [ ] 의존성 추가 (`testcontainers-starter:1.2.2-RC10`)
- [ ] IntegrationTestBase 생성
- [ ] 스키마 파일 경로 확인 (`project:` vs `classpath:`)
- [ ] 테스트 클래스에서 IntegrationTestBase 상속
- [ ] 기존 테스트 설정 파일 삭제

---

**Happy Testing! 🚀**

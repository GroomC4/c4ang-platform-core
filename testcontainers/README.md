# 통합 테스트 컨테이너 가이드

c4ang-platform-core에서 제공하는 Testcontainers 기반 통합 테스트 인프라 사용 가이드입니다.

## 개요

이 모듈은 MSA 서비스들이 통합 테스트에서 공통으로 사용하는 인프라(PostgreSQL Primary/Replica, Redis, Kafka)를 Docker Compose 기반 Testcontainers로 제공합니다.

### 제공되는 인프라

- **PostgreSQL Primary/Replica**: 마스터-슬레이브 복제 구조
- **Redis**: 캐시 및 분산 락
- **Kafka (KRaft Mode)**: 이벤트 스트리밍 (Zookeeper 불필요)

### 아키텍처

```
c4ang-platform-core/testcontainers
├── BaseContainerExtension          # Docker Compose 컨테이너 관리
├── TestContainerContextInitializer # Kafka 설정 주입
└── @BaseIntegrationTest           # 기본 통합 테스트 어노테이션

각 서비스 (예: store-api)
├── ServiceContainerExtension       # 서비스별 compose 파일 및 스키마 지정
├── TestDataSourceConfig           # PostgreSQL/Redis 연결 설정
├── TestRedissonConfig (선택)      # Redisson 클라이언트 설정
└── @IntegrationTest               # 서비스 전용 통합 테스트 어노테이션
```

## 전제조건

### 1. Gradle 의존성

서비스의 `build.gradle.kts`에 다음 의존성을 추가하세요:

```kotlin
dependencies {
    // Platform Core Testcontainers
    testImplementation(project(":c4ang-platform-core:testcontainers"))

    // Testcontainers
    testImplementation("org.testcontainers:testcontainers:1.19.0")
    testImplementation("org.testcontainers:postgresql:1.19.0")
    testImplementation("org.testcontainers:kafka:1.19.0")

    // Spring Boot Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
```

### 2. 스키마 파일 준비

서비스의 데이터베이스 스키마 파일을 준비합니다:

```
your-service/
└── sql/
    └── schema.sql    # PostgreSQL DDL
```

## 단계별 가이드

### Step 1: ServiceContainerExtension 작성

서비스별 Docker Compose 파일과 스키마 파일 경로를 제공하는 Extension을 작성합니다.

**위치**: `src/test/kotlin/com/groom/{service}/common/extension/{Service}ContainerExtension.kt`

```kotlin
package com.groom.yourservice.common.extension

import com.groom.platform.testSupport.BaseContainerExtension
import java.io.File

/**
 * YourService용 통합 테스트 컨테이너 Extension
 */
class YourServiceContainerExtension : BaseContainerExtension() {
    override fun getComposeFile(): File =
        resolveComposeFile("c4ang-platform-core/docker-compose/test/docker-compose-integration-test.yml")

    override fun getSchemaFile(): File {
        // 서비스의 PostgreSQL 스키마 파일 경로
        return resolveComposeFile("your-service/sql/schema.sql")
    }
}
```

### Step 2: TestDataSourceConfig 작성

PostgreSQL Primary/Replica와 Redis에 동적으로 연결하는 설정을 작성합니다.

**위치**: `src/test/kotlin/com/groom/{service}/common/config/TestDataSourceConfig.kt`

```kotlin
package com.groom.yourservice.common.config

import com.groom.platform.testSupport.BaseContainerExtension
import com.groom.yourservice.configuration.jpa.DataSourceType
import com.groom.yourservice.configuration.jpa.DynamicRoutingDataSource
import com.zaxxer.hikari.HikariDataSource
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.jdbc.DataSourceBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy
import javax.sql.DataSource

@Profile("test")
@Configuration
class TestDataSourceConfig {
    @Bean
    fun masterDataSource(): HikariDataSource =
        DataSourceBuilder
            .create()
            .type(HikariDataSource::class.java)
            .url(BaseContainerExtension.getPrimaryJdbcUrl())
            .driverClassName("org.postgresql.Driver")
            .username("test")
            .password("test")
            .build()

    @Bean
    fun replicaDataSource(): HikariDataSource =
        DataSourceBuilder
            .create()
            .type(HikariDataSource::class.java)
            .url(BaseContainerExtension.getReplicaJdbcUrl())
            .driverClassName("org.postgresql.Driver")
            .username("test")
            .password("test")
            .build()

    @Bean
    fun routingDataSource(
        @Qualifier("masterDataSource") masterDataSource: DataSource,
        @Qualifier("replicaDataSource") replicaDataSource: DataSource,
    ): DataSource {
        val dynamicRoutingDataSource = DynamicRoutingDataSource()
        val targetDataSources: Map<Any, Any> =
            mapOf(
                DataSourceType.MASTER to masterDataSource,
                DataSourceType.REPLICA to replicaDataSource,
            )
        dynamicRoutingDataSource.setTargetDataSources(targetDataSources)
        dynamicRoutingDataSource.setDefaultTargetDataSource(masterDataSource)

        return dynamicRoutingDataSource
    }

    @Primary
    @Bean
    fun dataSource(
        @Qualifier("routingDataSource") dataSource: DataSource,
    ): DataSource = LazyConnectionDataSourceProxy(dataSource)

    @Bean
    fun redisConnectionFactory(): RedisConnectionFactory =
        LettuceConnectionFactory(
            BaseContainerExtension.getRedisHost(),
            BaseContainerExtension.getRedisPort(),
        )
}
```

**주의사항**:
- `DataSourceType`과 `DynamicRoutingDataSource`는 서비스의 `src/main` 패키지에 위치해야 합니다
- Testcontainers 모듈은 테스트 전용이므로, 메인 소스에서 임포트할 수 없습니다

### Step 3: TestRedissonConfig 작성 (선택사항)

Redisson을 사용하는 경우, 테스트용 Redisson 설정을 작성합니다.

**위치**: `src/test/kotlin/com/groom/{service}/common/config/TestRedissonConfig.kt`

```kotlin
package com.groom.yourservice.common.config

import com.groom.platform.testSupport.BaseContainerExtension
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Profile("test")
@Configuration
class TestRedissonConfig {
    @Bean
    fun redissonClient(): RedissonClient {
        val config = Config()

        val host = BaseContainerExtension.getRedisHost()
        val port = BaseContainerExtension.getRedisPort()

        config
            .useSingleServer()
            .setAddress("redis://$host:$port")
            .setConnectionPoolSize(10)
            .setConnectionMinimumIdleSize(2)
            .setTimeout(3000)
            .setRetryAttempts(3)
            .setRetryInterval(1500)

        return Redisson.create(config)
    }
}
```

### Step 4: @IntegrationTest 어노테이션 작성

서비스 전용 통합 테스트 어노테이션을 작성합니다.

**위치**: `src/test/kotlin/com/groom/{service}/common/annotation/IntegrationTest.kt`

```kotlin
package com.groom.yourservice.common.annotation

import com.groom.yourservice.common.extension.YourServiceContainerExtension
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.context.SpringBootTest
import com.groom.platform.testSupport.IntegrationTest as BaseIntegrationTest

/**
 * YourService 통합 테스트용 어노테이션
 *
 * c4ang-platform-core의 BaseIntegrationTest를 상속받아 YourService에 필요한
 * 컨테이너 Extension을 추가합니다.
 *
 * 사용 예시:
 * ```kotlin
 * @IntegrationTest
 * @AutoConfigureMockMvc
 * class YourControllerIntegrationTest {
 *     @Test
 *     fun `통합 테스트`() {
 *         // 테스트 로직
 *     }
 * }
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@BaseIntegrationTest
@SpringBootTest
@ExtendWith(YourServiceContainerExtension::class)
annotation class IntegrationTest
```

### Step 5: 통합 테스트 작성

이제 `@IntegrationTest` 어노테이션을 사용하여 통합 테스트를 작성할 수 있습니다.

```kotlin
package com.groom.yourservice.application.service

import com.groom.yourservice.common.annotation.IntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc

@IntegrationTest
@AutoConfigureMockMvc
class YourServiceIntegrationTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `통합 테스트 예시`() {
        // Given
        // 테스트 데이터 준비

        // When
        // 비즈니스 로직 실행

        // Then
        // 결과 검증
    }
}
```

## Kafka 사용하기

Kafka를 사용하는 경우, 부트스트랩 서버는 자동으로 주입됩니다.

### application-test.yml 설정

```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    consumer:
      group-id: test-group
      auto-offset-reset: earliest
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
```

### 테스트 코드

```kotlin
@IntegrationTest
class KafkaIntegrationTest {
    @Autowired
    private lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @Test
    fun `Kafka 메시지 발행 및 소비 테스트`() {
        // Given
        val topic = "test-topic"
        val message = "test message"

        // When
        kafkaTemplate.send(topic, message)

        // Then
        // Consumer에서 메시지 수신 확인
    }
}
```

## DynamicRoutingDataSource 구현 (선택사항)

읽기/쓰기 분리가 필요한 경우, `DynamicRoutingDataSource`를 구현하세요.

**위치**: `src/main/kotlin/com/groom/{service}/configuration/jpa/DataSourceConfig.kt`

```kotlin
package com.groom.yourservice.configuration.jpa

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource
import org.springframework.transaction.support.TransactionSynchronizationManager

class DynamicRoutingDataSource : AbstractRoutingDataSource() {
    override fun determineCurrentLookupKey(): DataSourceType {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return DataSourceType.isReadOnlyTransaction(isTxReadOnly())
        }
        return DataSourceType.MASTER
    }

    private fun isTxReadOnly(): Boolean =
        TransactionSynchronizationManager.isCurrentTransactionReadOnly()
}

enum class DataSourceType {
    MASTER,
    REPLICA,
    ;
    companion object {
        fun isReadOnlyTransaction(txReadOnly: Boolean): DataSourceType =
            if (txReadOnly) REPLICA else MASTER
    }
}
```

## 트러블슈팅

### 1. "Failed to determine a suitable driver class" 에러

**원인**: TestDataSourceConfig가 컴포넌트 스캔되지 않음

**해결방법**: TestDataSourceConfig를 서비스의 `src/test/kotlin/com/groom/{service}/common/config/` 패키지에 위치시키세요.

### 2. "Unresolved reference: platform" 컴파일 에러

**원인**: 메인 소스(`src/main`)에서 testcontainers 모듈을 임포트하려 함

**해결방법**:
- `DataSourceType`과 `DynamicRoutingDataSource`는 `src/main`에 위치
- `TestDataSourceConfig`는 `src/test`에 위치하고 메인 소스의 클래스를 임포트

### 3. 스키마 파일이 적용되지 않음

**원인**: ServiceContainerExtension의 `getSchemaFile()` 경로가 잘못됨

**해결방법**:
- 경로는 프로젝트 루트 기준 상대 경로입니다
- 예: `your-service/sql/schema.sql`

### 4. Docker Compose 컨테이너가 시작되지 않음

**원인**: Docker Desktop이 실행되지 않았거나 권한 문제

**해결방법**:
- Docker Desktop 실행 확인
- `docker ps` 명령어로 Docker 연결 확인

## 실제 구현 예시

전체 구현 예시는 `store-api` 모듈을 참고하세요:

- `store-api/src/test/kotlin/com/groom/store/common/extension/SharedContainerExtension.kt`
- `store-api/src/test/kotlin/com/groom/store/common/config/TestDataSourceConfig.kt`
- `store-api/src/test/kotlin/com/groom/store/common/config/TestRedissonConfig.kt`
- `store-api/src/test/kotlin/com/groom/store/common/annotation/IntegrationTest.kt`

## 추가 정보

### BaseContainerExtension 제공 메서드

```kotlin
// PostgreSQL
BaseContainerExtension.getPrimaryJdbcUrl(): String
BaseContainerExtension.getReplicaJdbcUrl(): String

// Redis
BaseContainerExtension.getRedisHost(): String
BaseContainerExtension.getRedisPort(): Int

// Kafka
BaseContainerExtension.getKafkaBootstrapServers(): String
```

### 테스트 프로파일

모든 테스트 설정은 `@Profile("test")`로 격리되어 있으므로, 프로덕션/개발 환경과 충돌하지 않습니다.

### 컨테이너 재사용

통합 테스트 실행 시 Docker Compose 컨테이너는 모든 테스트에서 공유됩니다. 테스트가 끝나도 컨테이너는 종료되지 않으며, 다음 테스트 실행 시 재사용됩니다.

## 문의

궁금한 점이나 문제가 있으면 팀 채널에 문의하세요.

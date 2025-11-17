# Primary-Replica 라우팅 중앙화 비교: 제안 1 vs 제안 2

## 핵심 답변

**제안 2 (Spring Boot Starter)가 더 완벽하게 중앙화됩니다!**

| 중앙화 항목 | 제안 1 (Singleton) | 제안 2 (Starter) |
|------------|-------------------|------------------|
| DynamicRoutingDataSource | ✅ 중앙화 | ✅ 중앙화 |
| DataSourceType | ✅ 중앙화 | ✅ 중앙화 |
| Bean 생성 코드 | ❌ 각 서비스 작성 | ✅ 완전 자동화 |
| 프로덕션 설정 | ⚠️ 각 서비스 작성 | ✅ 자동 또는 최소화 |
| 테스트 설정 | ✅ 자동화 | ✅ 완전 자동화 |
| **총 코드량** | 20줄/서비스 | **0줄/서비스** |

---

## 제안 1 (Singleton 패턴) - 부분 중앙화

### 중앙화되는 것 ✅

**platform-core/src/main/kotlin/**
```kotlin
// DynamicRoutingDataSource - 중앙화!
class DynamicRoutingDataSource : AbstractRoutingDataSource() {
    override fun determineCurrentLookupKey(): DataSourceType {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return DataSourceType.isReadOnlyTransaction(
                TransactionSynchronizationManager.isCurrentTransactionReadOnly()
            )
        }
        return DataSourceType.MASTER
    }
}

// DataSourceType - 중앙화!
enum class DataSourceType {
    MASTER, REPLICA;
    companion object {
        fun isReadOnlyTransaction(txReadOnly: Boolean) = if (txReadOnly) REPLICA else MASTER
    }
}
```

### 각 서비스에서 여전히 작성해야 하는 코드 ❌

**프로덕션 (src/main/kotlin/DataSourceConfig.kt) - 각 서비스마다 작성!**
```kotlin
import com.groom.platform.datasource.DynamicRoutingDataSource  // import만
import com.groom.platform.datasource.DataSourceType

@Profile("!test")
@Configuration
class DataSourceConfig {

    @Bean
    @ConfigurationProperties("spring.datasource.master")
    fun masterDataSourceProperties() = DataSourceProperties()

    @Bean
    fun masterDataSource(
        @Qualifier("masterDataSourceProperties") properties: DataSourceProperties
    ): DataSource = properties.initializeDataSourceBuilder().type(HikariDataSource::class.java).build()

    @Bean
    @ConfigurationProperties("spring.datasource.replica")
    fun replicaDataSourceProperties() = DataSourceProperties()

    @Bean
    fun replicaDataSource(
        @Qualifier("replicaDataSourceProperties") properties: DataSourceProperties
    ): DataSource = properties.initializeDataSourceBuilder().type(HikariDataSource::class.java).build()

    @Bean
    fun routingDataSource(
        @Qualifier("masterDataSource") masterDataSource: DataSource,
        @Qualifier("replicaDataSource") replicaDataSource: DataSource
    ): DataSource {
        val router = DynamicRoutingDataSource()  // platform-core에서 가져옴
        router.setTargetDataSources(
            mapOf(
                DataSourceType.MASTER to masterDataSource,
                DataSourceType.REPLICA to replicaDataSource
            )
        )
        router.setDefaultTargetDataSource(masterDataSource)
        return router
    }

    @Primary
    @Bean
    fun dataSource(
        @Qualifier("routingDataSource") dataSource: DataSource
    ): DataSource = LazyConnectionDataSourceProxy(dataSource)
}
```

**문제점:**
- ❌ Bean 생성 코드를 각 서비스마다 작성 (약 60줄)
- ❌ masterDataSource, replicaDataSource Bean 생성 반복
- ❌ routingDataSource Bean 생성 반복
- ❌ 설정 변경 시 모든 서비스 수정 필요

**테스트 (src/test/kotlin) - 자동화됨!**
```kotlin
@SpringBootTest
class StoreTest : IntegrationTestBase() {  // 상속만!
    @Test
    @Transactional(readOnly = true)
    fun test() { ... }
}
```

### 요약

✅ **중앙화:**
- DynamicRoutingDataSource
- DataSourceType
- 테스트 DataSource 설정

❌ **각 서비스 작성:**
- 프로덕션 DataSourceConfig (60줄)
- masterDataSource Bean
- replicaDataSource Bean
- routingDataSource Bean

---

## 제안 2 (Spring Boot Starter) - 완전 중앙화

### 완전히 중앙화되는 것 ✅✅✅

**datasource-starter/src/main/kotlin/**

#### 1. DynamicRoutingDataSource.kt
```kotlin
package com.groom.platform.datasource

class DynamicRoutingDataSource : AbstractRoutingDataSource() {
    override fun determineCurrentLookupKey(): DataSourceType {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return DataSourceType.isReadOnlyTransaction(
                TransactionSynchronizationManager.isCurrentTransactionReadOnly()
            )
        }
        return DataSourceType.MASTER
    }
}

enum class DataSourceType {
    MASTER, REPLICA;
    companion object {
        fun isReadOnlyTransaction(txReadOnly: Boolean) = if (txReadOnly) REPLICA else MASTER
    }
}
```

#### 2. DataSourceAutoConfiguration.kt (프로덕션용 자동 설정!)
```kotlin
package com.groom.platform.datasource.autoconfigure

@AutoConfiguration
@ConditionalOnClass(DataSource::class)
@EnableConfigurationProperties(PlatformDataSourceProperties::class)
class DataSourceAutoConfiguration {

    /**
     * Master DataSource 자동 생성
     */
    @Bean
    @ConfigurationProperties("spring.datasource.master.hikari")
    fun masterDataSource(
        @Qualifier("masterDataSourceProperties") properties: DataSourceProperties
    ): DataSource {
        return properties.initializeDataSourceBuilder()
            .type(HikariDataSource::class.java)
            .build()
    }

    @Bean
    @ConfigurationProperties("spring.datasource.master")
    fun masterDataSourceProperties() = DataSourceProperties()

    /**
     * Replica DataSource 자동 생성
     */
    @Bean
    @ConfigurationProperties("spring.datasource.replica.hikari")
    @ConditionalOnProperty(prefix = "platform.datasource", name = ["replica-enabled"], matchIfMissing = true)
    fun replicaDataSource(
        @Qualifier("replicaDataSourceProperties") properties: DataSourceProperties
    ): DataSource {
        return properties.initializeDataSourceBuilder()
            .type(HikariDataSource::class.java)
            .build()
    }

    @Bean
    @ConfigurationProperties("spring.datasource.replica")
    fun replicaDataSourceProperties() = DataSourceProperties()

    /**
     * Routing DataSource 자동 생성
     *
     * @Transactional(readOnly = true) → REPLICA
     * @Transactional(readOnly = false) → MASTER
     */
    @Bean
    fun routingDataSource(
        @Qualifier("masterDataSource") masterDataSource: DataSource,
        @Qualifier("replicaDataSource") replicaDataSource: DataSource
    ): DataSource {
        val router = DynamicRoutingDataSource()
        router.setTargetDataSources(
            mapOf(
                DataSourceType.MASTER to masterDataSource,
                DataSourceType.REPLICA to replicaDataSource
            )
        )
        router.setDefaultTargetDataSource(masterDataSource)
        router.afterPropertiesSet()
        return router
    }

    /**
     * LazyConnectionDataSourceProxy 자동 생성
     */
    @Primary
    @Bean
    fun dataSource(
        @Qualifier("routingDataSource") routingDataSource: DataSource
    ): DataSource {
        return LazyConnectionDataSourceProxy(routingDataSource)
    }
}
```

#### 3. PlatformDataSourceProperties.kt
```kotlin
@ConfigurationProperties(prefix = "platform.datasource")
data class PlatformDataSourceProperties(
    /**
     * Replica DataSource 활성화 여부
     */
    var replicaEnabled: Boolean = true,

    /**
     * Routing 로깅 활성화
     */
    var loggingEnabled: Boolean = false
)
```

#### 4. META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```
com.groom.platform.datasource.autoconfigure.DataSourceAutoConfiguration
```

**testcontainers-starter/src/main/kotlin/**

#### 5. TestDataSourceAutoConfiguration.kt (테스트용 자동 설정!)
```kotlin
@AutoConfiguration
@ConditionalOnClass(PostgreSQLContainer::class)
@EnableConfigurationProperties(TestcontainersProperties::class)
class TestDataSourceAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "testcontainers.postgres", name = ["enabled"], matchIfMissing = true)
    fun postgresContainer(properties: TestcontainersProperties): PostgreSQLContainer<*> {
        val container = PostgreSQLContainer(properties.postgres.image)
            .withUsername("test")
            .withPassword("test")
            .withDatabaseName("testdb")
            .withReuse(true)

        // 스키마 자동 로딩
        properties.postgres.schemaLocation?.let {
            container.withInitScript(it.removePrefix("classpath:"))
        }

        container.start()
        return container
    }

    @Bean
    @ConditionalOnBean(PostgreSQLContainer::class)
    @ConditionalOnProperty(prefix = "testcontainers.postgres", name = ["replica-enabled"], matchIfMissing = true)
    fun postgresReplicaContainer(properties: TestcontainersProperties): PostgreSQLContainer<*> {
        val container = PostgreSQLContainer(properties.postgres.image)
            .withUsername("test")
            .withPassword("test")
            .withDatabaseName("testdb")
            .withReuse(true)

        properties.postgres.schemaLocation?.let {
            container.withInitScript(it.removePrefix("classpath:"))
        }

        container.start()
        return container
    }

    /**
     * Master DataSource 자동 생성
     */
    @Bean
    fun masterDataSource(postgresContainer: PostgreSQLContainer<*>): DataSource {
        return DataSourceBuilder.create()
            .url(postgresContainer.jdbcUrl)
            .username(postgresContainer.username)
            .password(postgresContainer.password)
            .driverClassName("org.postgresql.Driver")
            .build()
    }

    /**
     * Replica DataSource 자동 생성
     */
    @Bean
    fun replicaDataSource(
        @Qualifier("postgresReplicaContainer") replicaContainer: PostgreSQLContainer<*>
    ): DataSource {
        return DataSourceBuilder.create()
            .url(replicaContainer.jdbcUrl)
            .username(replicaContainer.username)
            .password(replicaContainer.password)
            .driverClassName("org.postgresql.Driver")
            .build()
    }

    /**
     * Routing DataSource 자동 생성
     * DataSourceAutoConfiguration의 Bean을 오버라이드
     */
    @Bean
    fun routingDataSource(
        @Qualifier("masterDataSource") master: DataSource,
        @Qualifier("replicaDataSource") replica: DataSource
    ): DataSource {
        val router = DynamicRoutingDataSource()
        router.setTargetDataSources(
            mapOf(
                DataSourceType.MASTER to master,
                DataSourceType.REPLICA to replica
            )
        )
        router.setDefaultTargetDataSource(master)
        router.afterPropertiesSet()
        return router
    }

    @Primary
    @Bean
    fun dataSource(
        @Qualifier("routingDataSource") routing: DataSource
    ): DataSource {
        return LazyConnectionDataSourceProxy(routing)
    }
}
```

### 각 서비스에서 작성하는 코드 = **0줄!** ✅✅✅

#### 프로덕션 (application.yml)
```yaml
spring:
  datasource:
    master:
      url: jdbc:postgresql://master-host:5432/db
      username: app_user
      password: password
    replica:
      url: jdbc:postgresql://replica-host:5432/db
      username: app_user
      password: password

platform:
  datasource:
    replica-enabled: true
```

**DataSourceConfig.kt 파일 자체가 불필요!** ❌ 삭제 가능!

#### 테스트 (application-test.yml)
```yaml
testcontainers:
  postgres:
    enabled: true
    replica-enabled: true
    schema-location: classpath:db/schema.sql
```

**TestDataSourceConfig.kt 파일 자체가 불필요!** ❌ 삭제 가능!

#### 테스트 코드
```kotlin
@SpringBootTest
class StoreTest {  // 상속조차 불필요!

    @Autowired
    private lateinit var dataSource: DataSource  // 자동 주입!

    @Test
    @Transactional(readOnly = false)  // MASTER 자동!
    fun testInsert() {
        // Primary로 자동 라우팅
    }

    @Test
    @Transactional(readOnly = true)  // REPLICA 자동!
    fun testSelect() {
        // Replica로 자동 라우팅
    }
}
```

### 요약

✅✅✅ **완전 중앙화:**
- DynamicRoutingDataSource
- DataSourceType
- masterDataSource Bean 생성
- replicaDataSource Bean 생성
- routingDataSource Bean 생성
- LazyConnectionDataSourceProxy Bean 생성
- 프로덕션 설정
- 테스트 설정

❌ **각 서비스 작성:**
- **아무것도 없음!** (yml만 설정)

---

## 상세 비교

### 프로덕션 코드 비교

#### 제안 1 (Singleton)

**각 서비스의 DataSourceConfig.kt (60줄)**
```kotlin
import com.groom.platform.datasource.DynamicRoutingDataSource
import com.groom.platform.datasource.DataSourceType

@Configuration
class DataSourceConfig {
    @Bean
    fun masterDataSource(...) { ... }  // 15줄

    @Bean
    fun replicaDataSource(...) { ... }  // 15줄

    @Bean
    fun routingDataSource(...) {  // 15줄
        val router = DynamicRoutingDataSource()
        router.setTargetDataSources(...)
        return router
    }

    @Primary
    @Bean
    fun dataSource(...) { ... }  // 5줄
}
```

**문제:**
- ❌ 모든 서비스가 동일한 60줄 작성
- ❌ 라우팅 로직 변경 시 모든 서비스 수정

---

#### 제안 2 (Starter)

**각 서비스의 코드: 없음!**

**application.yml만 작성 (5줄)**
```yaml
spring:
  datasource:
    master:
      url: jdbc:postgresql://master:5432/db
    replica:
      url: jdbc:postgresql://replica:5432/db
```

**장점:**
- ✅ 코드 0줄
- ✅ 설정 변경 시 yml만 수정
- ✅ 라우팅 로직 변경 시 Starter만 업데이트

---

### 테스트 코드 비교

#### 제안 1 (Singleton)

**각 서비스 (상속 필요)**
```kotlin
@SpringBootTest
class StoreTest : IntegrationTestBase() {  // 상속 필요
    @Test
    @Transactional(readOnly = true)
    fun test() { ... }
}
```

---

#### 제안 2 (Starter)

**각 서비스 (상속 불필요)**
```yaml
# application-test.yml
testcontainers:
  postgres:
    schema-location: classpath:db/schema.sql
```

```kotlin
@SpringBootTest
class StoreTest {  // 상속 불필요!
    @Test
    @Transactional(readOnly = true)
    fun test() { ... }
}
```

---

### Primary-Replica 라우팅 작동 방식 (동일)

#### 제안 1
```kotlin
@Service
class OrderService(private val orderRepository: OrderRepository) {

    @Transactional(readOnly = false)  // MASTER로 라우팅
    fun createOrder(order: Order) {
        orderRepository.save(order)
    }

    @Transactional(readOnly = true)  // REPLICA로 라우팅
    fun getOrders(): List<Order> {
        return orderRepository.findAll()
    }
}
```

#### 제안 2
```kotlin
@Service
class OrderService(private val orderRepository: OrderRepository) {

    @Transactional(readOnly = false)  // MASTER로 라우팅
    fun createOrder(order: Order) {
        orderRepository.save(order)
    }

    @Transactional(readOnly = true)  // REPLICA로 라우팅
    fun getOrders(): List<Order> {
        return orderRepository.findAll()
    }
}
```

**결과: 완전히 동일하게 작동!**

---

## 추가 기능 (제안 2만 가능)

### 1. Replica 비활성화 (yml만으로)

```yaml
platform:
  datasource:
    replica-enabled: false  # Replica 비활성화
```

→ masterDataSource만 사용, replicaDataSource Bean 생성 안됨

### 2. 라우팅 로깅

```yaml
platform:
  datasource:
    logging-enabled: true
```

```kotlin
// DataSourceAutoConfiguration에서
class DynamicRoutingDataSource : AbstractRoutingDataSource() {
    override fun determineCurrentLookupKey(): DataSourceType {
        val key = if (TransactionSynchronizationManager.isActualTransactionActive()) {
            DataSourceType.isReadOnlyTransaction(
                TransactionSynchronizationManager.isCurrentTransactionReadOnly()
            )
        } else {
            DataSourceType.MASTER
        }

        if (loggingEnabled) {
            logger.debug("Routing to: $key")
        }

        return key
    }
}
```

### 3. 커스텀 라우팅 전략

```yaml
platform:
  datasource:
    routing-strategy: RANDOM_REPLICA  # ROUND_ROBIN, WEIGHTED 등
```

---

## 코드량 비교 (서비스당)

| 항목 | 제안 1 (Singleton) | 제안 2 (Starter) |
|------|-------------------|------------------|
| **프로덕션** | | |
| DataSourceConfig.kt | 60줄 | **0줄** |
| application.yml | 10줄 | 10줄 |
| **테스트** | | |
| 테스트 Base 클래스 | 상속 필요 | **불필요** |
| application-test.yml | 0줄 (자동) | 5줄 |
| **총 코드량** | **70줄** | **15줄 (yml만)** |
| **절감률** | - | **78% 감소** |

---

## 유지보수성 비교

### 시나리오: Replica 연결 풀 크기 변경

#### 제안 1
```kotlin
// 모든 서비스의 DataSourceConfig.kt 수정 필요! (10개 서비스 = 10번 수정)
@Bean
fun replicaDataSource(...): DataSource {
    return properties.initializeDataSourceBuilder()
        .type(HikariDataSource::class.java)
        .build()
        .apply {
            maximumPoolSize = 20  // 변경!
        }
}
```

#### 제안 2
```yaml
# 각 서비스의 application.yml만 수정 (1줄)
spring:
  datasource:
    replica:
      hikari:
        maximum-pool-size: 20  # 변경!
```

또는 Starter에서 기본값 변경:
```kotlin
// DataSourceAutoConfiguration.kt (한 곳만 수정!)
@ConfigurationProperties("spring.datasource.replica.hikari")
fun replicaDataSource(...): DataSource {
    // 기본값 설정
    return HikariDataSource().apply {
        maximumPoolSize = 20  // 모든 서비스에 적용!
    }
}
```

---

## 결론

### 제안 1 (Singleton) - 부분 중앙화
✅ DynamicRoutingDataSource 중앙화
✅ DataSourceType 중앙화
❌ Bean 생성 코드는 각 서비스 작성 (60줄)
❌ 프로덕션 설정 각 서비스 작성

**코드량:** 70줄/서비스

---

### 제안 2 (Starter) - 완전 중앙화
✅ DynamicRoutingDataSource 중앙화
✅ DataSourceType 중앙화
✅ Bean 생성 완전 자동화
✅ 프로덕션 설정 자동화
✅ 테스트 설정 자동화

**코드량:** 15줄 (yml만)/서비스

---

## 핵심 요약

**질문: "제안 2로 할 경우에도 Primary-Replica 라우팅 중앙화가 가능한거야?"**

**답변: 네! 제안 1보다 훨씬 더 완벽하게 중앙화됩니다!**

| 중앙화 수준 | 제안 1 | 제안 2 |
|------------|--------|--------|
| 라우팅 로직 | ✅ | ✅ |
| Bean 생성 | ❌ | ✅ |
| 프로덕션 설정 | ❌ | ✅ |
| 테스트 설정 | ✅ | ✅ |
| **완전 자동화** | ❌ | ✅ |

**제안 2를 사용하면 각 서비스는 yml 파일만 수정하고, DataSourceConfig.kt 파일 자체가 불필요합니다!**

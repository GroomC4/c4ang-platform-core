# PostgreSQL 설정 방식 비교: Local vs Dev vs Prod vs Test 프로필

## 개요

`platform-core`와 `testcontainers-starter`에서 PostgreSQL DataSource가 구성되는 방식이 다릅니다.
이 문서는 각 환경의 차이점과 설계 의도를 설명합니다.

## 프로필 개요

| 프로필 | 환경 | 인프라 | 설명 |
|--------|------|--------|------|
| `local` | 개발자 로컬 | Docker Compose | 자동 시작, 동적 포트 주입 |
| `dev` | k3d (로컬 K8s) | Kubernetes Services | 외부 설정, prod와 동일 구조 |
| `prod` | EKS (AWS) | Kubernetes Services | 외부 설정, 환경변수 주입 |
| `test` | 테스트 | Testcontainers | 자동 시작, Bean 직접 주입 |

## 1. Local 프로필 (platform-core)

### 흐름도

```
┌─────────────────────────────────────────────────────────────────────────┐
│                     Local 프로필 PostgreSQL 흐름                          │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  LocalInfraEnvironmentPostProcessor                                      │
│  ┌─────────────────────────────────────────────────────────────────┐    │
│  │ 1. Docker Compose 시작 (postgres-primary, postgres-replica)      │    │
│  │ 2. 동적 포트 조회                                                 │    │
│  │ 3. 프로퍼티 주입:                                                 │    │
│  │    - spring.datasource.master.url                                │    │
│  │    - spring.datasource.master.username/password                  │    │
│  │    - spring.datasource.replica.url                               │    │
│  │    - spring.datasource.replica.username/password                 │    │
│  └─────────────────────────────────────────────────────────────────┘    │
│                              │                                           │
│                              ▼                                           │
│  DataSourceConfiguration (platform-core)                                 │
│  ┌─────────────────────────────────────────────────────────────────┐    │
│  │ @ConditionalOnProperty("spring.datasource.master.url") ✅ 활성화  │    │
│  │                                                                  │    │
│  │ • masterDataSource: @Value("${spring.datasource.master.url}")   │    │
│  │ • replicaDataSource: @Value("${spring.datasource.replica.url}") │    │
│  │ • routingDataSource: DynamicRoutingDataSource                   │    │
│  │ • dataSource: LazyConnectionDataSourceProxy                     │    │
│  └─────────────────────────────────────────────────────────────────┘    │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### 관련 파일

- `LocalInfraEnvironmentPostProcessor.kt`: Docker Compose 시작 및 프로퍼티 주입
- `DataSourceConfiguration.kt`: 프로퍼티 기반 DataSource 생성

### 주입되는 프로퍼티

```properties
spring.datasource.master.url=jdbc:postgresql://localhost:{동적포트}/groom
spring.datasource.master.username=application
spring.datasource.master.password=application
spring.datasource.master.driver-class-name=org.postgresql.Driver

spring.datasource.replica.url=jdbc:postgresql://localhost:{동적포트}/groom
spring.datasource.replica.username=application
spring.datasource.replica.password=application
spring.datasource.replica.driver-class-name=org.postgresql.Driver
```

### 활성화 조건

```kotlin
@ConditionalOnProperty(prefix = "spring.datasource.master", name = ["url"])
```

## 2. Test 프로필 (testcontainers-starter)

### 흐름도

```
┌─────────────────────────────────────────────────────────────────────────┐
│                     Test 프로필 PostgreSQL 흐름                           │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  TestcontainersAutoConfiguration                                         │
│  ┌─────────────────────────────────────────────────────────────────┐    │
│  │ 1. SharedContainers.postgresContainer 접근                       │    │
│  │ 2. 스키마 파일 설정 (schemaLocation)                              │    │
│  │ 3. container.start()                                             │    │
│  │ 4. Bean 등록: postgresContainer, postgresReplicaContainer        │    │
│  │                                                                  │    │
│  │ ⚠️ 프로퍼티 주입 없음! (spring.datasource.master.url 등)          │    │
│  └─────────────────────────────────────────────────────────────────┘    │
│                              │                                           │
│                              ▼                                           │
│  DataSourceConfiguration (platform-core)                                 │
│  ┌─────────────────────────────────────────────────────────────────┐    │
│  │ @ConditionalOnProperty("spring.datasource.master.url") ❌ 비활성화│    │
│  │ → 프로퍼티가 없어서 이 Configuration이 로드되지 않음!              │    │
│  └─────────────────────────────────────────────────────────────────┘    │
│                              │                                           │
│                              ▼                                           │
│  TestDataSourceAutoConfiguration (testcontainers-starter)               │
│  ┌─────────────────────────────────────────────────────────────────┐    │
│  │ @ConditionalOnBean("postgresContainer") ✅ 활성화                 │    │
│  │                                                                  │    │
│  │ • masterDataSource: postgresContainer.jdbcUrl 직접 사용          │    │
│  │ • replicaDataSource: postgresReplicaContainer.jdbcUrl 직접 사용  │    │
│  │ • routingDataSource: DynamicRoutingDataSource                   │    │
│  │ • dataSource: LazyConnectionDataSourceProxy                     │    │
│  └─────────────────────────────────────────────────────────────────┘    │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### 관련 파일

- `TestcontainersAutoConfiguration.kt`: PostgreSQL 컨테이너 시작 및 Bean 등록
- `TestDataSourceAutoConfiguration.kt`: 컨테이너 Bean 기반 DataSource 생성

### DataSource 생성 방식

```kotlin
@Bean
@ConditionalOnBean(name = ["postgresContainer"])
fun masterDataSource(
    @Qualifier("postgresContainer") postgresContainer: PostgreSQLContainer<*>,
): DataSource {
    val config = HikariConfig().apply {
        jdbcUrl = postgresContainer.jdbcUrl      // 컨테이너에서 직접 조회
        username = postgresContainer.username
        password = postgresContainer.password
        driverClassName = "org.postgresql.Driver"
    }
    return HikariDataSource(config)
}
```

### 활성화 조건

```kotlin
@ConditionalOnBean(name = ["postgresContainer"])
```

## 3. 비교 요약

| 항목 | Local 프로필 | Test 프로필 |
|------|-------------|-------------|
| **모듈** | platform-core | testcontainers-starter |
| **인프라 시작** | Docker Compose | Testcontainers |
| **프로퍼티 주입** | ✅ `spring.datasource.master/replica.*` | ❌ 없음 |
| **DataSource 생성** | `DataSourceConfiguration` | `TestDataSourceAutoConfiguration` |
| **조건** | `@ConditionalOnProperty` | `@ConditionalOnBean` |
| **연결 정보 획득** | `@Value` 어노테이션 | Container Bean 직접 주입 |

## 4. 설계 의도

### 왜 테스트 환경에서는 프로퍼티를 주입하지 않는가?

1. **분리된 책임**: 테스트 환경의 DataSource 설정은 `testcontainers-starter`가 전담
2. **충돌 방지**: `@ConditionalOnProperty` vs `@ConditionalOnBean`으로 명확히 분리
3. **단순성**: 컨테이너 Bean을 직접 주입받아 설정, 중간 프로퍼티 변환 불필요

### 조건부 활성화 동작

```
Local 프로필:
  - spring.datasource.master.url 프로퍼티 존재 → DataSourceConfiguration 활성화
  - postgresContainer Bean 없음 → TestDataSourceAutoConfiguration 비활성화

Test 프로필:
  - spring.datasource.master.url 프로퍼티 없음 → DataSourceConfiguration 비활성화
  - postgresContainer Bean 존재 → TestDataSourceAutoConfiguration 활성화
```

## 5. Redis/Kafka와의 차이점

PostgreSQL과 달리 Redis/Kafka는 테스트 환경에서도 프로퍼티 주입이 필요합니다.

| 인프라 | 테스트에서 프로퍼티 주입 | 이유 |
|--------|------------------------|------|
| **PostgreSQL** | ❌ 불필요 | `TestDataSourceAutoConfiguration`이 Bean 직접 생성 |
| **Redis** | ✅ 필요 | 서비스에서 `@Value("${spring.data.redis.host}")` 사용 |
| **Kafka** | ✅ 필요 | Spring Kafka가 `spring.kafka.bootstrap-servers` 자동 사용 |
| **Schema Registry** | ✅ 필요 | Kafka 설정의 일부로 프로퍼티 필요 |

### TestContainerContextInitializer에서 주입하는 프로퍼티

```kotlin
// Redis
dynamicProperties.add("spring.data.redis.host=$redisHost")
dynamicProperties.add("spring.data.redis.port=$redisPort")

// Kafka
dynamicProperties.add("spring.kafka.bootstrap-servers=$bootstrapServers")

// Schema Registry
dynamicProperties.add("spring.kafka.properties.schema.registry.url=$schemaRegistryUrl")
```

## 6. 결론

- **PostgreSQL**: 프로퍼티 주입 없이 Container Bean 직접 사용 (분리된 Configuration)
- **Redis/Kafka**: 프로퍼티 주입 필요 (서비스/Spring Boot 자동설정에서 참조)

이 설계는 각 환경의 특성에 맞게 최적화되어 있으며, 조건부 활성화를 통해 충돌 없이 동작합니다.

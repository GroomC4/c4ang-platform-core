# Testcontainers 중앙화 패턴 - 업계 표준 방식

## 개요

중앙화된 레포에서 Testcontainers를 제공하고, 여러 마이크로서비스에서 공유할 때 사용되는 일반적인 패턴들을 정리합니다.

---

## 패턴 1: Spring Boot Starter 패턴 ⭐⭐⭐⭐⭐

### 개념
Spring Boot의 Auto-Configuration 메커니즘을 활용하여 Testcontainers를 자동으로 설정

### 구조
```
testcontainers-starter/
├── src/main/java/
│   └── com/groom/testcontainers/autoconfigure/
│       ├── TestcontainersAutoConfiguration.java
│       ├── TestcontainersProperties.java
│       └── containers/
│           ├── PostgresTestContainer.java
│           ├── RedisTestContainer.java
│           └── KafkaTestContainer.java
├── src/main/resources/
│   └── META-INF/
│       └── spring/
│           └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
└── build.gradle.kts
```

### 핵심 코드

#### 1. TestcontainersProperties.java
```java
@ConfigurationProperties(prefix = "testcontainers")
public class TestcontainersProperties {
    private Postgres postgres = new Postgres();
    private Redis redis = new Redis();
    private Kafka kafka = new Kafka();

    public static class Postgres {
        private boolean enabled = true;
        private String image = "postgres:17";
        private String schemaLocation;  // classpath:db/schema.sql

        // getters/setters
    }

    public static class Redis {
        private boolean enabled = true;
        private String image = "redis:7-alpine";

        // getters/setters
    }

    public static class Kafka {
        private boolean enabled = true;
        private String image = "confluentinc/cp-kafka:7.5.1";
        private List<String> topics = new ArrayList<>();

        // getters/setters
    }
}
```

#### 2. TestcontainersAutoConfiguration.java
```java
@AutoConfiguration
@EnableConfigurationProperties(TestcontainersProperties.class)
@ConditionalOnProperty(prefix = "testcontainers", name = "enabled", matchIfMissing = true)
public class TestcontainersAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "testcontainers.postgres", name = "enabled", matchIfMissing = true)
    @ServiceConnection  // Spring Boot 3.1+ 자동 연결!
    public PostgreSQLContainer<?> postgresContainer(TestcontainersProperties properties) {
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>(properties.getPostgres().getImage())
            .withReuse(true);  // 컨테이너 재사용

        // 스키마 로딩
        String schemaLocation = properties.getPostgres().getSchemaLocation();
        if (schemaLocation != null) {
            container.withInitScript(schemaLocation.replace("classpath:", ""));
        }

        return container;
    }

    @Bean
    @ConditionalOnProperty(prefix = "testcontainers.redis", name = "enabled", matchIfMissing = true)
    @ServiceConnection
    public GenericContainer<?> redisContainer(TestcontainersProperties properties) {
        return new GenericContainer<>(properties.getRedis().getImage())
            .withExposedPorts(6379)
            .withReuse(true);
    }

    @Bean
    @ConditionalOnProperty(prefix = "testcontainers.kafka", name = "enabled", matchIfMissing = true)
    @ServiceConnection
    public KafkaContainer kafkaContainer(TestcontainersProperties properties) {
        KafkaContainer container = new KafkaContainer(DockerImageName.parse(properties.getKafka().getImage()))
            .withReuse(true);

        // 토픽 자동 생성
        container.withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "true");

        return container;
    }

    @Bean
    @ConditionalOnBean(KafkaContainer.class)
    public DynamicPropertyRegistry kafkaProperties(KafkaContainer kafka, DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        return registry;
    }
}
```

#### 3. META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```
com.groom.testcontainers.autoconfigure.TestcontainersAutoConfiguration
```

### 사용법 (서비스 측)

#### build.gradle.kts
```kotlin
dependencies {
    testImplementation("com.groom:testcontainers-starter:1.0.0")
}
```

#### application-test.yml
```yaml
testcontainers:
  postgres:
    enabled: true
    schema-location: classpath:db/schema.sql
  redis:
    enabled: true
  kafka:
    enabled: true
    topics:
      - order-events
      - payment-events

# Spring Boot가 자동으로 설정!
# spring.datasource.url: 자동 설정됨
# spring.data.redis.host: 자동 설정됨
# spring.kafka.bootstrap-servers: 자동 설정됨
```

#### 테스트 코드
```java
@SpringBootTest
class OrderServiceTest {
    // 그냥 사용! 설정 코드 0줄

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void test() {
        // PostgreSQL 자동으로 연결됨
        jdbcTemplate.execute("SELECT 1");
    }
}
```

### 장점
✅ **yml 프로퍼티로 완전 제어**
✅ **@ServiceConnection으로 자동 연결** (Spring Boot 3.1+)
✅ **코드 작성 불필요** - 의존성 추가만 하면 됨
✅ **Spring 생태계와 완벽 통합**
✅ **IDE 자동완성 지원**

### 단점
❌ Spring Boot 3.1+ 필요 (@ServiceConnection)
❌ Starter 패키지 빌드 필요 (GitHub Packages 또는 Maven Local)

### 실제 사례
- Spring Cloud Contract
- Testcontainers Spring Boot
- AWS LocalStack Spring Boot Starter

---

## 패턴 2: @RegisterExtension + Static Field 패턴 ⭐⭐⭐⭐

### 개념
JUnit 5의 @RegisterExtension을 사용하여 프로그래매틱하게 Extension 설정

### 구조
```java
// 중앙 레포
public class SharedContainersExtension implements BeforeAllCallback, AfterAllCallback {

    private final ContainerConfig config;

    public SharedContainersExtension(ContainerConfig config) {
        this.config = config;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private boolean enablePostgres = true;
        private boolean enableRedis = true;
        private boolean enableKafka = true;
        private String schemaPath;

        public Builder enablePostgres(boolean enable) {
            this.enablePostgres = enable;
            return this;
        }

        public Builder schemaPath(String path) {
            this.schemaPath = path;
            return this;
        }

        public SharedContainersExtension build() {
            ContainerConfig config = new ContainerConfig(
                enablePostgres, enableRedis, enableKafka, schemaPath
            );
            return new SharedContainersExtension(config);
        }
    }

    // beforeAll, afterAll 구현...
}
```

### 사용법 (서비스 측)
```java
@SpringBootTest
class OrderServiceTest {

    @RegisterExtension
    static SharedContainersExtension containers = SharedContainersExtension.builder()
        .enablePostgres(true)
        .enableRedis(true)
        .enableKafka(false)  // Kafka 비활성화
        .schemaPath("db/schema.sql")
        .build();

    @Test
    void test() {
        // 테스트 코드
    }
}
```

### 장점
✅ **타입 안전성**
✅ **Fluent API로 가독성 좋음**
✅ **Spring 버전 무관**
✅ **테스트마다 다른 설정 가능**

### 단점
❌ 각 테스트 클래스에 코드 작성 필요
❌ yml 프로퍼티 사용 불가

---

## 패턴 3: Testcontainers Singleton + DynamicPropertySource ⭐⭐⭐⭐⭐

### 개념
모든 테스트가 공유하는 싱글톤 컨테이너 + Spring의 DynamicPropertySource

### 구조

#### 중앙 레포
```java
public abstract class AbstractContainerBase {

    protected static final Network SHARED_NETWORK = Network.newNetwork();

    static class PostgresContainer {
        static final PostgreSQLContainer<?> INSTANCE;

        static {
            INSTANCE = new PostgreSQLContainer<>("postgres:17")
                .withNetwork(SHARED_NETWORK)
                .withNetworkAliases("postgres");

            INSTANCE.start();
        }
    }

    static class RedisContainer {
        static final GenericContainer<?> INSTANCE;

        static {
            INSTANCE = new GenericContainer<>("redis:7-alpine")
                .withExposedPorts(6379)
                .withNetwork(SHARED_NETWORK);

            INSTANCE.start();
        }
    }

    static class KafkaContainer {
        static final org.testcontainers.containers.KafkaContainer INSTANCE;

        static {
            INSTANCE = new org.testcontainers.containers.KafkaContainer(
                DockerImageName.parse("confluentinc/cp-kafka:7.5.1")
            ).withNetwork(SHARED_NETWORK);

            INSTANCE.start();
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL
        registry.add("spring.datasource.url", PostgresContainer.INSTANCE::getJdbcUrl);
        registry.add("spring.datasource.username", PostgresContainer.INSTANCE::getUsername);
        registry.add("spring.datasource.password", PostgresContainer.INSTANCE::getPassword);

        // Redis
        registry.add("spring.data.redis.host", RedisContainer.INSTANCE::getHost);
        registry.add("spring.data.redis.port", () -> RedisContainer.INSTANCE.getMappedPort(6379));

        // Kafka
        registry.add("spring.kafka.bootstrap-servers", KafkaContainer.INSTANCE::getBootstrapServers);
    }
}
```

### 사용법 (서비스 측)
```java
@SpringBootTest
class OrderServiceTest extends AbstractContainerBase {
    // 그냥 상속만 하면 끝!

    @Test
    void test() {
        // PostgreSQL, Redis, Kafka 자동 연결
    }
}
```

### 조건부 컨테이너 시작 (개선 버전)
```java
public abstract class AbstractContainerBase {

    // System Property로 제어
    private static final boolean POSTGRES_ENABLED =
        Boolean.parseBoolean(System.getProperty("testcontainers.postgres.enabled", "true"));

    private static final boolean REDIS_ENABLED =
        Boolean.parseBoolean(System.getProperty("testcontainers.redis.enabled", "true"));

    static class PostgresContainer {
        static final PostgreSQLContainer<?> INSTANCE;

        static {
            if (POSTGRES_ENABLED) {
                INSTANCE = new PostgreSQLContainer<>("postgres:17");
                INSTANCE.start();
            } else {
                INSTANCE = null;
            }
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        if (POSTGRES_ENABLED && PostgresContainer.INSTANCE != null) {
            registry.add("spring.datasource.url", PostgresContainer.INSTANCE::getJdbcUrl);
        }

        if (REDIS_ENABLED && RedisContainer.INSTANCE != null) {
            registry.add("spring.data.redis.host", RedisContainer.INSTANCE::getHost);
        }
    }
}
```

### 장점
✅ **극도로 간단** - 상속만 하면 됨
✅ **진짜 싱글톤** - JVM 전체에서 1개만 생성
✅ **Spring 3.x, 2.x 모두 지원**
✅ **DynamicPropertySource로 자동 주입**

### 단점
❌ 모든 테스트가 동일한 컨테이너 사용 (커스터마이징 불가)
❌ 스키마 파일 주입이 복잡함

### 실제 사례
- 대부분의 Spring Boot + Testcontainers 튜토리얼
- Baeldung, Spring 공식 문서

---

## 패턴 4: Testcontainers Module 패턴 ⭐⭐⭐

### 개념
Testcontainers 공식 Module처럼 특정 기술 스택에 특화된 컨테이너 제공

### 구조
```java
// PostgresTestContainer.java
public class PostgresTestContainer extends PostgreSQLContainer<PostgresTestContainer> {

    private static final String IMAGE = "postgres:17";
    private static PostgresTestContainer instance;

    private PostgresTestContainer() {
        super(IMAGE);
    }

    public static PostgresTestContainer getInstance() {
        if (instance == null) {
            instance = new PostgresTestContainer()
                .withReuse(true)
                .withCommand("postgres -c max_connections=200");
            instance.start();
        }
        return instance;
    }

    public PostgresTestContainer withSchemaScript(String scriptPath) {
        withInitScript(scriptPath);
        return this;
    }

    public PostgresTestContainer withReplication() {
        withCommand("postgres -c wal_level=replica -c max_wal_senders=10");
        return this;
    }
}
```

### 사용법
```java
@SpringBootTest
@Testcontainers
class OrderServiceTest {

    @Container
    static PostgresTestContainer postgres = PostgresTestContainer.getInstance()
        .withSchemaScript("db/schema.sql")
        .withReplication();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
    }

    @Test
    void test() {
        // ...
    }
}
```

### 장점
✅ **Testcontainers 네이티브 방식**
✅ **유연한 커스터마이징**
✅ **Fluent API**

### 단점
❌ 각 테스트에 설정 코드 필요
❌ Spring과의 통합이 약함

---

## 패턴 5: Test Fixture Factory 패턴 ⭐⭐⭐⭐

### 개념
테스트 픽스처를 제공하는 Factory 클래스

### 구조
```java
public class TestContainerFactory {

    private static final Map<String, Object> containers = new ConcurrentHashMap<>();

    public static PostgreSQLContainer<?> createPostgresContainer(PostgresConfig config) {
        String key = "postgres-" + config.hashCode();

        return (PostgreSQLContainer<?>) containers.computeIfAbsent(key, k -> {
            PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:17");

            if (config.getSchemaPath() != null) {
                container.withInitScript(config.getSchemaPath());
            }

            if (config.isReplicationEnabled()) {
                container.withCommand("postgres -c wal_level=replica");
            }

            container.start();
            return container;
        });
    }

    public static ComposeContainer createFullStack(StackConfig config) {
        // Docker Compose 기반 전체 스택
        return new ComposeContainer(new File("docker-compose-test.yml"))
            .withEnv(config.toEnvMap());
    }
}

@Data
@Builder
public class PostgresConfig {
    private String schemaPath;
    private boolean replicationEnabled;
    private String image = "postgres:17";
}
```

### 사용법
```java
@SpringBootTest
class OrderServiceTest {

    static PostgreSQLContainer<?> postgres = TestContainerFactory.createPostgresContainer(
        PostgresConfig.builder()
            .schemaPath("db/schema.sql")
            .replicationEnabled(true)
            .build()
    );

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
    }
}
```

### 장점
✅ **타입 안전성**
✅ **Builder 패턴으로 명확한 설정**
✅ **컨테이너 재사용 (캐싱)**

### 단점
❌ 각 테스트에 코드 필요
❌ yml 프로퍼티 사용 불가

---

## 패턴 6: Spring Boot 3.1+ @ServiceConnection ⭐⭐⭐⭐⭐ (최신)

### 개념
Spring Boot 3.1부터 도입된 @ServiceConnection으로 자동 연결

### 구조 (중앙 레포)
```java
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>("postgres:17")
            .withInitScript("db/schema.sql");  // 고정된 경로
    }

    @Bean
    @ServiceConnection
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);
    }

    @Bean
    @ServiceConnection
    KafkaContainer kafkaContainer() {
        return new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.1"));
    }
}
```

### 사용법 (서비스 측)
```java
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OrderServiceTest {
    // 끝! DataSource, Redis, Kafka 자동 연결!

    @Autowired
    private DataSource dataSource;  // 자동 주입

    @Test
    void test() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            // PostgreSQL 자동 연결됨
        }
    }
}
```

### 조건부 활성화 (개선)
```java
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    @ConditionalOnProperty(name = "testcontainers.postgres.enabled", havingValue = "true", matchIfMissing = true)
    PostgreSQLContainer<?> postgresContainer(
        @Value("${testcontainers.postgres.schema:db/schema.sql}") String schemaPath
    ) {
        return new PostgreSQLContainer<>("postgres:17")
            .withInitScript(schemaPath);
    }
}
```

### application-test.yml
```yaml
testcontainers:
  postgres:
    enabled: true
    schema: db/order-schema.sql  # 서비스별로 다른 스키마!
  redis:
    enabled: true
  kafka:
    enabled: false  # Kafka 비활성화
```

### 장점
✅ **Spring Boot 3.1+ 최신 기능**
✅ **@ServiceConnection으로 완전 자동화**
✅ **yml 프로퍼티 지원**
✅ **@ConditionalOnProperty로 조건부 활성화**

### 단점
❌ Spring Boot 3.1+ 필수
❌ 스키마 경로를 동적으로 주입하려면 추가 작업 필요

---

## 패턴 7: Contract Testing with Pact/Spring Cloud Contract

### 개념
중앙 레포에서 Contract 정의 + Testcontainers 제공

### 구조
```
platform-contracts/
├── contracts/
│   ├── order-service/
│   ├── payment-service/
│   └── ...
├── testcontainers/
│   └── ContractTestBase.java
└── build.gradle.kts
```

```java
@SpringBootTest
@AutoConfigureStubRunner(
    ids = "com.groom:platform-contracts:+:stubs:8080",
    stubsMode = StubRunnerProperties.StubsMode.LOCAL
)
public abstract class ContractTestBase {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
    }
}
```

---

## 비교표

| 패턴 | yml 지원 | 코드량 | 유연성 | Spring 버전 | 추천도 |
|------|---------|--------|--------|------------|--------|
| Spring Boot Starter | ✅✅✅ | ⭐ 최소 | ⭐⭐⭐ 높음 | 3.1+ | ⭐⭐⭐⭐⭐ |
| @RegisterExtension | ❌ | ⭐⭐ 중간 | ⭐⭐⭐⭐ 매우높음 | 무관 | ⭐⭐⭐⭐ |
| Singleton + Dynamic | ⚠️ 부분 | ⭐ 최소 | ⭐⭐ 낮음 | 2.2+ | ⭐⭐⭐⭐⭐ |
| Module 패턴 | ❌ | ⭐⭐⭐ 많음 | ⭐⭐⭐⭐ 매우높음 | 무관 | ⭐⭐⭐ |
| Factory 패턴 | ❌ | ⭐⭐ 중간 | ⭐⭐⭐⭐ 매우높음 | 무관 | ⭐⭐⭐⭐ |
| @ServiceConnection | ✅✅ | ⭐ 최소 | ⭐⭐⭐ 높음 | 3.1+ | ⭐⭐⭐⭐⭐ |

---

## 권장 방안 (프로젝트 상황별)

### 상황 1: Spring Boot 3.1+ 사용 중
→ **패턴 1 (Spring Boot Starter) + 패턴 6 (@ServiceConnection)**

**이유:**
- yml 프로퍼티 완벽 지원
- 코드 작성 거의 불필요
- Spring 생태계와 완벽 통합

### 상황 2: Spring Boot 2.x 또는 3.0
→ **패턴 3 (Singleton + DynamicPropertySource)**

**이유:**
- 가장 간단한 구현
- Spring 2.2+ 지원
- 상속만 하면 끝

### 상황 3: 각 테스트마다 다른 설정 필요
→ **패턴 2 (@RegisterExtension) + 패턴 5 (Factory)**

**이유:**
- 테스트별 커스터마이징 가능
- 타입 안전성
- Fluent API

### 상황 4: 멀티 모듈 프로젝트 + 각 모듈마다 다른 DB 스키마
→ **패턴 1 (Spring Boot Starter) + Convention**

**구현:**
```yaml
# order-service/src/test/resources/application-test.yml
testcontainers:
  postgres:
    schema-location: classpath:db/order-schema.sql

# payment-service/src/test/resources/application-test.yml
testcontainers:
  postgres:
    schema-location: classpath:db/payment-schema.sql
```

---

## 현재 프로젝트에 가장 적합한 패턴

### 추천: **패턴 3 (Singleton + DynamicPropertySource) + Convention 개선**

#### 이유
1. ✅ **Spring Boot 3.3.4 호환** (2.2+만 필요)
2. ✅ **현재 구조와 유사** (BaseContainerExtension과 비슷)
3. ✅ **코드 작성 최소화** (상속만)
4. ✅ **System Properties로 제어 가능**
5. ✅ **Git Submodule 방식 유지**

#### 구현 예시
```java
// platform-core
public abstract class IntegrationTestBase {

    protected static final Network NETWORK = Network.newNetwork();

    // 설정 로딩
    private static final ContainerConfig CONFIG = ContainerConfig.fromSystemProperties();

    static class PostgresContainer {
        static final PostgreSQLContainer<?> INSTANCE;

        static {
            if (CONFIG.isPostgresEnabled()) {
                INSTANCE = new PostgreSQLContainer<>("postgres:17")
                    .withNetwork(NETWORK)
                    .withReuse(true);

                // Convention: 스키마 자동 탐지
                String schemaPath = detectSchemaPath();
                if (schemaPath != null) {
                    INSTANCE.withInitScript(schemaPath);
                }

                INSTANCE.start();
            } else {
                INSTANCE = null;
            }
        }

        private static String detectSchemaPath() {
            // 1. System Property 우선
            String explicit = System.getProperty("testcontainers.postgres.schema");
            if (explicit != null) return explicit;

            // 2. Convention: src/test/resources/db/schema.sql
            String[] candidates = {
                "db/schema.sql",
                "sql/schema.sql",
                "schema.sql"
            };

            for (String candidate : candidates) {
                if (IntegrationTestBase.class.getClassLoader().getResource(candidate) != null) {
                    return candidate;
                }
            }

            return null;
        }
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        if (CONFIG.isPostgresEnabled() && PostgresContainer.INSTANCE != null) {
            registry.add("spring.datasource.url", PostgresContainer.INSTANCE::getJdbcUrl);
            registry.add("spring.datasource.username", PostgresContainer.INSTANCE::getUsername);
            registry.add("spring.datasource.password", PostgresContainer.INSTANCE::getPassword);
        }

        if (CONFIG.isRedisEnabled() && RedisContainer.INSTANCE != null) {
            registry.add("spring.data.redis.host", RedisContainer.INSTANCE::getHost);
            registry.add("spring.data.redis.port", () -> RedisContainer.INSTANCE.getMappedPort(6379));
        }

        if (CONFIG.isKafkaEnabled() && KafkaContainer.INSTANCE != null) {
            registry.add("spring.kafka.bootstrap-servers", KafkaContainer.INSTANCE::getBootstrapServers);
        }
    }
}

class ContainerConfig {
    private final boolean postgresEnabled;
    private final boolean redisEnabled;
    private final boolean kafkaEnabled;

    static ContainerConfig fromSystemProperties() {
        return new ContainerConfig(
            getBoolean("testcontainers.postgres.enabled", true),
            getBoolean("testcontainers.redis.enabled", true),
            getBoolean("testcontainers.kafka.enabled", true)
        );
    }

    private static boolean getBoolean(String key, boolean defaultValue) {
        String value = System.getProperty(key, System.getenv(key.replace('.', '_').toUpperCase()));
        return value != null ? Boolean.parseBoolean(value) : defaultValue;
    }
}
```

#### 사용법 (서비스)
```java
// store-service
@SpringBootTest
class StoreServiceTest extends IntegrationTestBase {
    // 끝! 상속만 하면 모든 컨테이너 자동 시작

    @Test
    void test() {
        // PostgreSQL, Redis, Kafka 자동 연결
    }
}
```

#### 디렉토리 구조 (Convention)
```
store-service/
└── src/test/resources/
    └── db/
        └── schema.sql  ← 자동 탐지!
```

#### 조건부 비활성화
```bash
# Kafka 없이 테스트
./gradlew test -Dtestcontainers.kafka.enabled=false
```

---

## 최종 추천

**현재 프로젝트:**
→ **패턴 3 (Singleton + DynamicPropertySource) + Convention 개선**

**향후 Spring Boot 3.1+ 마이그레이션 시:**
→ **패턴 1 (Spring Boot Starter) + @ServiceConnection**

**각 서비스마다 크게 다른 요구사항:**
→ **패턴 2 (@RegisterExtension) + Builder 패턴**

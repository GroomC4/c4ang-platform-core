# Schema Registry Testcontainer 통합 요청

## 📋 배경 (Context)

현재 `c4ang-platform-core/testcontainers-starter`에는 PostgreSQL, Redis, Kafka testcontainer가 구현되어 있습니다. 하지만 Kafka Avro 직렬화를 사용하는 서비스 통합 테스트에서 Schema Registry가 없어 `Connection refused (http://localhost:8081)` 오류가 발생하고 있습니다.

## 🎯 목표 (Goal)

Schema Registry testcontainer를 추가하여 Kafka Avro 메시지 직렬화/역직렬화가 필요한 통합 테스트가 정상 작동하도록 합니다.

## 📝 상세 수정사항

### 1️⃣ SharedContainers.kt 수정

**파일:** `testcontainers-starter/src/main/kotlin/com/groom/platform/testcontainers/container/SharedContainers.kt`

**추가할 위치:** `kafkaContainer` 정의 다음

**추가할 코드:**
```kotlin
/**
 * Schema Registry 컨테이너 (싱글톤)
 *
 * Kafka Avro 직렬화를 위한 스키마 레지스트리입니다.
 * Kafka 컨테이너에 의존하며, Kafka가 먼저 시작된 후 실행됩니다.
 */
val schemaRegistryContainer: GenericContainer<*> by lazy {
    println("🚀 Initializing shared Schema Registry container...")

    // Kafka 컨테이너가 먼저 시작되도록 보장
    val kafka = kafkaContainer

    GenericContainer(DockerImageName.parse("confluentinc/cp-schema-registry:7.5.1"))
        .withExposedPorts(8081)
        .withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
        .withEnv("SCHEMA_REGISTRY_LISTENERS", "http://0.0.0.0:8081")
        .withEnv(
            "SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS",
            "PLAINTEXT://${kafka.host}:${kafka.firstMappedPort}"
        )
        .withReuse(true)
        // Note: Do NOT call start() here!
        // The container will be started after configuration in TestcontainersAutoConfiguration
}
```

**중요 포인트:**
- `kafkaContainer`를 먼저 참조하여 Kafka가 초기화되도록 보장
- `withEnv`로 Kafka bootstrap server를 동적으로 설정
- 다른 컨테이너와 동일하게 `.start()` 호출하지 않음

---

### 2️⃣ TestcontainersAutoConfiguration.kt 수정

**파일:** `testcontainers-starter/src/main/kotlin/com/groom/platform/testcontainers/autoconfigure/TestcontainersAutoConfiguration.kt`

**추가할 위치:** `kafkaContainer()` 메서드 다음

**추가할 코드:**
```kotlin
/**
 * Schema Registry 컨테이너 (JVM 전체 공유 싱글톤)
 *
 * Kafka Avro 직렬화/역직렬화를 위한 스키마 레지스트리입니다.
 */
@Bean
@ConditionalOnProperty(prefix = "testcontainers.schema-registry", name = ["enabled"], matchIfMissing = true)
fun schemaRegistryContainer(
    kafkaContainer: KafkaContainer,
): GenericContainer<*> {
    val container = SharedContainers.schemaRegistryContainer

    // 컨테이너 시작 (아직 시작되지 않은 경우에만)
    if (!container.isRunning) {
        container.start()
        val schemaRegistryUrl = "http://${container.host}:${container.getMappedPort(8081)}"
        println("✅ Schema Registry container started and ready ($schemaRegistryUrl)")
    } else {
        println("✅ Schema Registry container already running")
    }

    return container
}
```

**중요 포인트:**
- `kafkaContainer`를 파라미터로 받아 의존성 보장
- `@ConditionalOnProperty`로 활성화 제어 가능

---

### 3️⃣ TestcontainersProperties.kt 수정

**파일:** `testcontainers-starter/src/main/kotlin/com/groom/platform/testcontainers/autoconfigure/TestcontainersProperties.kt`

**수정 내용:** Schema Registry 설정 프로퍼티 추가

**기존 클래스에 추가:**
```kotlin
@ConfigurationProperties(prefix = "testcontainers")
data class TestcontainersProperties(
    val postgres: PostgresProperties = PostgresProperties(),
    val redis: RedisProperties = RedisProperties(),
    val kafka: KafkaProperties = KafkaProperties(),
    val schemaRegistry: SchemaRegistryProperties = SchemaRegistryProperties(), // ← 추가
) {
    // ... 기존 코드 ...

    // 새로 추가할 data class
    data class SchemaRegistryProperties(
        val enabled: Boolean = true,
    )
}
```

---

### 4️⃣ TestContainerContextInitializer.kt 수정

**파일:** `testcontainers-starter/src/main/kotlin/com/groom/platform/testcontainers/initializer/TestContainerContextInitializer.kt`

**수정 내용:** Schema Registry URL을 Spring 프로퍼티로 등록

**`initialize()` 메서드 수정:**
```kotlin
override fun initialize(applicationContext: ConfigurableApplicationContext) {
    try {
        // SharedContainers에서 컨테이너 정보를 가져와서 Spring 프로퍼티에 등록
        val kafkaBootstrapServers = SharedContainers.kafkaContainer.bootstrapServers
        val schemaRegistryUrl =
            "http://${SharedContainers.schemaRegistryContainer.host}:" +
            "${SharedContainers.schemaRegistryContainer.getMappedPort(8081)}"

        val properties =
            arrayOf(
                "KAFKA_BOOTSTRAP_SERVERS=$kafkaBootstrapServers",
                "kafka.bootstrap-servers=$kafkaBootstrapServers",
                "spring.kafka.bootstrap-servers=$kafkaBootstrapServers",
                "SCHEMA_REGISTRY_URL=$schemaRegistryUrl",  // ← 추가
                "kafka.schema-registry.url=$schemaRegistryUrl",  // ← 추가
            )

        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(applicationContext, *properties)

        println("✅ Test container properties configured:")
        println("   - Kafka: $kafkaBootstrapServers")
        println("   - Schema Registry: $schemaRegistryUrl")  // ← 추가
    } catch (e: Exception) {
        // 컨테이너가 아직 초기화되지 않은 경우, 로그만 출력하고 넘어감
        println("⚠️ Containers not yet initialized, properties will be set when containers start")
    }
}
```

---

### 5️⃣ README.md 업데이트

**파일:** `testcontainers-starter/README.md`

**수정 내용:** Schema Registry 관련 문서 추가

**자동 구성되는 컨테이너 섹션에 추가:**
```markdown
### 자동 구성되는 컨테이너

1. **PostgreSQL (Primary & Replica)**
   - 이미지: `postgres:17-alpine`
   - Primary-Replica 패턴 지원
   - 스키마 자동 로딩

2. **Redis**
   - 이미지: `redis:7-alpine`
   - 캐싱 및 세션 저장소

3. **Kafka**
   - 이미지: `confluentinc/cp-kafka:7.5.1`
   - KRaft 모드 (Zookeeper 불필요)

4. **Schema Registry** ← 새로 추가
   - 이미지: `confluentinc/cp-schema-registry:7.5.1`
   - Kafka Avro 직렬화/역직렬화 지원
   - Kafka 컨테이너와 자동 연동
```

**application-test.yml 예시에 추가:**
```yaml
testcontainers:
  postgres:
    enabled: true
    replica-enabled: true
    schema-location: classpath:db/schema.sql
  redis:
    enabled: true
  kafka:
    enabled: true
  schema-registry:  # ← 추가
    enabled: true
```

---

## ✅ 검증 방법

수정 완료 후 다음 명령으로 테스트:

```bash
# 1. testcontainers-starter 빌드
cd c4ang-platform-core
./gradlew :testcontainers-starter:clean :testcontainers-starter:build -x test

# 2. 서비스 통합 테스트 실행
cd ../c4ang-store-service
./gradlew :store-api:test --tests "com.groom.store.application.service.UpdateStoreServiceIntegrationTest"
```

**기대 결과:**
- Schema Registry 컨테이너가 자동으로 시작됨
- Kafka Avro 직렬화가 정상 작동
- UpdateStoreServiceIntegrationTest의 모든 테스트 통과

---

## 🔍 주의사항

1. **컨테이너 시작 순서:** Kafka → Schema Registry 순서가 중요합니다
2. **포트 매핑:** Schema Registry는 8081 포트를 노출하며, 동적으로 매핑됩니다
3. **재사용 (Reuse):** `.withReuse(true)` 설정으로 JVM 재시작 시에도 컨테이너 재사용
4. **의존성:** Schema Registry는 Kafka에 의존하므로 Kafka가 먼저 초기화되어야 합니다

---

## 📌 참고: 현재 발생 중인 오류

```
UpdateStoreServiceIntegrationTest > 스토어 소유자가 자신의 스토어를 성공적으로 수정한다() FAILED

Caused by:
java.net.ConnectException: Connection refused
    at io.confluent.kafka.schemaregistry.client.rest.RestService.sendHttpRequest(RestService.java:303)
    at io.confluent.kafka.serializers.AbstractKafkaAvroSerializer.serializeImpl(AbstractKafkaAvroSerializer.java:118)
```

이 오류는 Schema Registry가 `http://localhost:8081`에서 실행되지 않아 발생합니다.

---

## 🎯 최종 목표

이 수정을 통해:
1. ✅ 모든 서비스에서 Kafka Avro 직렬화/역직렬화가 정상 작동
2. ✅ Schema Registry가 자동으로 testcontainer로 시작
3. ✅ 통합 테스트에서 실제 프로덕션과 동일한 메시징 환경 제공
4. ✅ 개발자가 별도로 Schema Registry를 로컬에 설치하지 않아도 됨

---

**작성일:** 2025-11-17
**요청자:** c4ang-store-service 팀
**대상 저장소:** c4ang-platform-core
**버전:** testcontainers-starter 1.2.0+

# Schema Registry 저장소 동작 원리

## 개념

Schema Registry는 **별도의 데이터베이스 없이** Kafka 자체를 저장소로 사용합니다.

## 저장 방식

### 1. Kafka 내부 토픽 사용

Schema Registry가 시작되면 자동으로 생성되는 토픽:

```bash
# Schema Registry 내부 토픽 확인
kafka-topics --bootstrap-server localhost:9092 --list

_schemas                # 스키마 저장 토픽 (compacted topic)
```

### 2. 스키마 등록 시 동작

```
1. 애플리케이션이 Avro 메시지 전송 시도
   ↓
2. KafkaAvroSerializer가 스키마를 Schema Registry에 등록
   POST http://localhost:8081/subjects/store-events-value/versions
   Body: {"schema": "{\"type\":\"record\"...}"}
   ↓
3. Schema Registry가 스키마를 검증
   ↓
4. Schema Registry가 Kafka _schemas 토픽에 저장
   Key: schema-id (예: 1)
   Value: {
     "subject": "store-events-value",
     "version": 1,
     "id": 1,
     "schema": "{\"type\":\"record\",\"name\":\"StoreUpdated\"...}"
   }
   ↓
5. Schema ID 반환 (예: 1)
```

### 3. Compacted Topic으로 관리

```bash
# _schemas 토픽은 log compaction 사용
Topic: _schemas
Config: cleanup.policy=compact

# 같은 Key의 최신 값만 유지
Key=1, Value=schema_v1  (삭제됨)
Key=1, Value=schema_v2  (삭제됨)
Key=1, Value=schema_v3  (유지됨) ← 최신 버전만 남음
```

## 현재 프로젝트 설정

### Testcontainer 설정 (테스트 환경)

```kotlin
// SharedContainers.kt:108-110
.withEnv(
    "SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS",
    "PLAINTEXT://${kafka.host}:${kafka.firstMappedPort}"
)
```

**의미**:
- Schema Registry가 사용할 Kafka 주소 지정
- 스키마는 이 Kafka의 `_schemas` 토픽에 저장됨
- 별도 DB 불필요!

### 데이터 흐름

```
┌─────────────────┐
│  Application    │
│  (Producer)     │
└────────┬────────┘
         │ 1. Avro 메시지 전송
         ↓
┌─────────────────┐
│ Schema Registry │ 2. 스키마 등록/조회
│  (Container)    │
└────────┬────────┘
         │ 3. 스키마 저장
         ↓
┌─────────────────┐
│  Kafka          │
│  - user-topic   │ ← 실제 메시지
│  - _schemas     │ ← 스키마 저장!
└─────────────────┘
```

## 테스트 환경에서 확인하는 방법

### 1. Schema Registry 컨테이너 로그 확인

```bash
# 테스트 실행 시 로그
🚀 Starting shared Kafka container...
✅ Kafka container started and ready (PLAINTEXT://localhost:xxxxx)
🚀 Starting shared Schema Registry container...
✅ Schema Registry container started and ready (http://localhost:xxxxx)
```

### 2. 스키마 등록 확인 (테스트 중)

```kotlin
@SpringBootTest
class SchemaRegistryTest {

    @Test
    fun `스키마가 Kafka에 저장되는지 확인`() {
        // 1. Avro 메시지 전송
        val record = GenericData.Record(schema)
        producer.send(ProducerRecord("test-topic", record))

        // 2. Schema Registry API로 확인
        val response = restTemplate.getForObject(
            "http://localhost:8081/subjects/test-topic-value/versions/latest",
            String::class.java
        )
        // 스키마 정보 반환됨!
    }
}
```

## 프로덕션 환경에서는?

### 옵션 1: Kafka를 저장소로 사용 (현재와 동일)

```yaml
# docker-compose.yml 또는 K8s 설정
schema-registry:
  image: confluentinc/cp-schema-registry:7.5.1
  environment:
    SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS: kafka:9092
    # 별도 DB 불필요!
```

**장점**:
- 추가 DB 관리 불필요
- Kafka와 동일한 내구성/가용성
- 간단한 설정

**단점**:
- Kafka에 의존적
- 스키마 조회도 Kafka를 거침 (약간의 레이턴시)

### 옵션 2: 외부 DB 사용 (선택적)

```yaml
schema-registry:
  environment:
    # PostgreSQL을 메타데이터 저장소로 사용 (고급 기능)
    SCHEMA_REGISTRY_KAFKASTORE_CONNECTION_URL: jdbc:postgresql://db:5432/schema_registry
```

**사용 케이스**:
- 매우 큰 규모 (수만 개 스키마)
- SQL 기반 스키마 검색 필요
- Kafka와 독립적으로 운영

## 요약

현재 프로젝트:
- ✅ Schema Registry 컨테이너 자동 시작 (테스트 환경)
- ✅ Kafka를 저장소로 사용 (`_schemas` 토픽)
- ✅ 별도 DB 설정 불필요
- ✅ 메모리에 스키마 캐싱 (성능 최적화)

프로덕션 배포 시에도 동일한 방식 사용 가능!

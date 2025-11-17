# 현재 프로젝트의 Schema Registry 토픽 설정

## 현재 설정 상태

### 명시적 설정

```kotlin
// testcontainers-starter/src/main/kotlin/com/groom/platform/testcontainers/container/SharedContainers.kt
GenericContainer(DockerImageName.parse("confluentinc/cp-schema-registry:7.5.1"))
    .withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
    .withEnv("SCHEMA_REGISTRY_LISTENERS", "http://0.0.0.0:8081")
    .withEnv(
        "SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS",
        "PLAINTEXT://${kafka.host}:${kafka.firstMappedPort}"
    )
```

### 기본값으로 동작하는 설정

Schema Registry 7.5.1의 기본 설정:

```properties
# ===== 토픽 기본 설정 =====
kafkastore.topic=_schemas                        # 스키마 저장 토픽 이름
kafkastore.topic.replication.factor=1           # ⚠️ 복제본 1개 (테스트 환경이므로)
kafkastore.timeout.ms=500                        # Kafka 타임아웃
kafkastore.init.timeout.ms=60000                 # 초기화 타임아웃 (60초)

# ===== 토픽 정리 정책 =====
cleanup.policy=compact                           # ✅ Log Compaction (중요!)
min.compaction.lag.ms=0                          # 즉시 압축 가능
delete.retention.ms=86400000                     # 삭제 마커 보관 (24시간)

# ===== 보관 정책 =====
retention.ms=-1                                  # ✅ 무한 보관 (영구)
retention.bytes=-1                               # 용량 제한 없음

# ===== 세그먼트 설정 =====
segment.ms=3600000                               # 1시간마다 새 세그먼트
segment.bytes=1073741824                         # 세그먼트당 1GB

# ===== 성능 설정 =====
compression.type=producer                        # Producer 압축 설정 따름
min.insync.replicas=1                            # ⚠️ 최소 1개 (테스트 환경)

# ===== 스키마 호환성 =====
schema.compatibility.level=BACKWARD              # 하위 호환성 (기본)
```

---

## 상세 설명

### 1. 토픽 이름: `_schemas`

```bash
# Schema Registry가 자동으로 생성하는 내부 토픽
_schemas

# 첫 스키마 등록 시 자동 생성됨
# 수동으로 생성할 필요 없음
```

### 2. ⚠️ Replication Factor: 1 (테스트 환경)

```
현재 상태:
┌────────────────┐
│  _schemas      │
│  Broker 1 Only │  ← 단일 복제본
└────────────────┘

장점:
✅ 테스트 빠름 (복제 불필요)
✅ 리소스 절약

단점:
⚠️  브로커 다운 시 데이터 손실
⚠️  프로덕션에 부적합
```

**프로덕션 권장 설정**:
```properties
kafkastore.topic.replication.factor=3  # 3개 복제
min.insync.replicas=2                   # 최소 2개 동기화
```

### 3. ✅ Cleanup Policy: compact (Log Compaction)

```
동작 방식:

Before Compaction:
Key=1, Value=Schema v1
Key=2, Value=Schema v1
Key=1, Value=Schema v2  ← Key 1이 또 나옴
Key=3, Value=Schema v1
Key=1, Value=Schema v3  ← Key 1이 또 나옴

After Compaction:
Key=2, Value=Schema v1
Key=3, Value=Schema v1
Key=1, Value=Schema v3  ← 최신 값만 유지!
```

**장점**:
- 오래된 스키마 버전은 자동 삭제
- 스토리지 공간 절약
- 최신 스키마만 빠르게 조회

### 4. ✅ Retention: 무한 보관

```properties
retention.ms=-1        # 영구 보관
retention.bytes=-1     # 용량 제한 없음
```

**의미**:
- 스키마는 절대 삭제되지 않음 (compaction만 수행)
- 모든 스키마 히스토리 유지
- 과거 메시지도 항상 역직렬화 가능

### 5. Segment 설정

```properties
segment.ms=3600000       # 1시간마다 새 세그먼트
segment.bytes=1GB        # 또는 1GB 도달 시
```

**동작**:
```
_schemas 토픽 디스크 구조:

00000000000000000000.log  (Segment 1, 완료)
00000000000001000000.log  (Segment 2, 완료)
00000000000002000000.log  (Segment 3, 활성)
                           ↑ 현재 쓰기 중
```

### 6. Min In-Sync Replicas: 1

```properties
min.insync.replicas=1  # ⚠️ 테스트 환경
```

**현재 동작**:
```
Producer → Broker 1 (Leader)
            ↓
Producer ← "저장 완료!" (1개만 확인)
```

**프로덕션 권장**:
```properties
min.insync.replicas=2  # 최소 2개 브로커 확인
```

---

## 테스트 환경 vs 프로덕션 비교

| 설정 | 현재 (테스트) | 프로덕션 권장 |
|------|-------------|-------------|
| **replication.factor** | 1 (복제 없음) | 3 (3개 복제) |
| **min.insync.replicas** | 1 | 2 |
| **cleanup.policy** | compact ✅ | compact ✅ |
| **retention.ms** | -1 (무한) ✅ | -1 (무한) ✅ |
| **segment.ms** | 1시간 ✅ | 1시간 ✅ |

### 현재 설정의 특징

```
✅ 장점:
- cleanup.policy=compact: 최신 스키마만 유지 (공간 효율)
- retention.ms=-1: 영구 보관 (데이터 손실 없음)
- 테스트 환경으로 완벽: 빠르고 간단

⚠️  제한사항:
- replication.factor=1: 단일 장애점
- 브로커 재시작 시 일시 중단
- 프로덕션 배포 시 설정 변경 필요
```

---

## 프로덕션 배포 시 권장 설정

### 방법 1: 환경 변수로 설정 (권장)

```kotlin
// SharedContainers.kt 수정 (프로덕션용)
GenericContainer(DockerImageName.parse("confluentinc/cp-schema-registry:7.5.1"))
    .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", kafkaServers)
    .withEnv("SCHEMA_REGISTRY_KAFKASTORE_TOPIC_REPLICATION_FACTOR", "3")  // ⭐ 추가
    .withEnv("SCHEMA_REGISTRY_KAFKASTORE_MIN_INSYNC_REPLICAS", "2")       // ⭐ 추가
```

### 방법 2: Kubernetes ConfigMap

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: schema-registry-config
data:
  SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS: "kafka-0:9092,kafka-1:9092,kafka-2:9092"
  SCHEMA_REGISTRY_KAFKASTORE_TOPIC_REPLICATION_FACTOR: "3"
  SCHEMA_REGISTRY_KAFKASTORE_MIN_INSYNC_REPLICAS: "2"
  SCHEMA_REGISTRY_SCHEMA_COMPATIBILITY_LEVEL: "BACKWARD"
```

### 방법 3: Docker Compose

```yaml
services:
  schema-registry:
    image: confluentinc/cp-schema-registry:7.5.1
    environment:
      SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS: "kafka-1:9092,kafka-2:9092,kafka-3:9092"
      SCHEMA_REGISTRY_KAFKASTORE_TOPIC_REPLICATION_FACTOR: 3
      SCHEMA_REGISTRY_KAFKASTORE_MIN_INSYNC_REPLICAS: 2
      SCHEMA_REGISTRY_HOST_NAME: schema-registry
    ports:
      - "8081:8081"
    depends_on:
      - kafka-1
      - kafka-2
      - kafka-3
```

---

## 설정 확인 방법

### 1. 런타임 시 Schema Registry API로 확인

```bash
# Schema Registry 설정 확인
curl http://schema-registry:8081/config

Response:
{
  "compatibilityLevel": "BACKWARD"
}
```

### 2. Kafka Admin API로 _schemas 토픽 확인

```bash
# 토픽 상세 정보
kafka-topics --describe --topic _schemas --bootstrap-server localhost:9092

Topic: _schemas
PartitionCount: 1
ReplicationFactor: 1  ← 현재 설정
Configs: cleanup.policy=compact,
         segment.ms=3600000
```

### 3. 로그 확인

```bash
# Schema Registry 컨테이너 로그
docker logs schema-registry-container 2>&1 | grep kafkastore

[2025-01-17 ...] INFO  Initializing KafkaStore for topic _schemas
[2025-01-17 ...] INFO  KafkaStore topic _schemas created successfully
```

---

## 마이그레이션 가이드 (테스트 → 프로덕션)

### 1단계: 설정 추가

```kotlin
// 프로덕션 환경에서만 적용
val isProduction = System.getenv("ENVIRONMENT") == "production"

GenericContainer(DockerImageName.parse("confluentinc/cp-schema-registry:7.5.1"))
    .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", kafkaServers)
    .apply {
        if (isProduction) {
            withEnv("SCHEMA_REGISTRY_KAFKASTORE_TOPIC_REPLICATION_FACTOR", "3")
            withEnv("SCHEMA_REGISTRY_KAFKASTORE_MIN_INSYNC_REPLICAS", "2")
        }
    }
```

### 2단계: Kafka 클러스터 구성

```
최소 3-broker 클러스터:

┌─────────┐  ┌─────────┐  ┌─────────┐
│ Broker 1│  │ Broker 2│  │ Broker 3│
└────┬────┘  └────┬────┘  └────┬────┘
     │            │            │
     └────────────┴────────────┘
              Kafka Cluster
```

### 3단계: Schema Registry HA

```
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│ SR Instance 1│    │ SR Instance 2│    │ SR Instance 3│
└──────┬───────┘    └──────┬───────┘    └──────┬───────┘
       │                   │                   │
       └───────────────────┴───────────────────┘
                     Load Balancer
                          ↓
                    Application
```

### 4단계: 모니터링 설정

```yaml
# Prometheus 메트릭
kafka_topic_partition_replicas{topic="_schemas"}              # 복제본 수
kafka_topic_partition_in_sync_replicas{topic="_schemas"}      # ISR 수

# 알람
ALERT SchemasTopicUnderReplicated
  IF kafka_topic_partition_in_sync_replicas{topic="_schemas"} < 2
  FOR 5m
```

---

## 결론

### 현재 설정 요약

```
✅ 좋은 점:
- cleanup.policy=compact: 공간 효율적
- retention.ms=-1: 영구 보관
- 테스트 환경에 최적화

⚠️  개선 필요 (프로덕션):
- replication.factor: 1 → 3
- min.insync.replicas: 1 → 2
```

### 액션 아이템

```
1. 현재 (테스트): 변경 불필요 ✅
   → 단일 브로커로 충분

2. 프로덕션 배포 시:
   → replication.factor=3 설정
   → min.insync.replicas=2 설정
   → 3-broker Kafka 클러스터 구성
   → Schema Registry 3-instance HA
```

**현재 테스트 환경은 완벽합니다! 프로덕션 배포 시에만 복제 설정을 추가하면 됩니다.**

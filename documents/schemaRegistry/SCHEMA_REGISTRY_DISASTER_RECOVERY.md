# Schema Registry 재해 복구 및 고가용성

## 문제 인식

> "Kafka에 장애가 생겼을 때 스키마 데이터가 날아가는 경우가 있을 것 같은데?"

**결론부터**: 올바르게 설정하면 거의 불가능합니다. 하지만 설정이 중요합니다!

---

## 1. Kafka의 데이터 내구성 메커니즘

### 1-1. 복제 (Replication)

```bash
# _schemas 토픽 설정 확인
kafka-topics --describe --topic _schemas --bootstrap-server localhost:9092

Topic: _schemas
PartitionCount: 1
ReplicationFactor: 3  ⭐ 중요!
Configs: cleanup.policy=compact, min.insync.replicas=2

# 3개 브로커에 복제되어 저장됨
Broker 1 (Leader):  [Schema 1][Schema 2][Schema 3]
Broker 2 (Replica): [Schema 1][Schema 2][Schema 3]
Broker 3 (Replica): [Schema 1][Schema 2][Schema 3]
```

**의미**:
- 3개 브로커 중 2개가 죽어도 데이터 유지
- 1개 브로커만 살아있으면 스키마 복구 가능

### 1-2. 영구 저장 (Persistence)

```
Kafka는 메시지를 디스크에 저장:

메모리 (RAM):     [임시 캐시]
       ↓ 즉시 flush
디스크 (SSD):     [영구 저장]  ⭐
       ↓
백업 (선택):      [스냅샷]
```

**특징**:
- 메시지 수신 즉시 디스크에 기록
- 서버 재시작 시에도 데이터 유지
- Log Compaction으로 최신 스키마만 유지

### 1-3. min.insync.replicas (최소 동기화 복제본)

```yaml
# _schemas 토픽 기본 설정
min.insync.replicas: 2

# 동작 방식:
Producer → Broker 1 (Leader)
            ↓ 복제 대기
            Broker 2 (Replica) ✅ ACK
            Broker 3 (Replica) ✅ ACK
            ↓
Producer ← "저장 완료!" (최소 2개 브로커에 저장 확인)
```

**보장**:
- 최소 2개 브로커에 저장된 후에만 성공 응답
- 1개 브로커만 죽으면 나머지에서 즉시 복구

---

## 2. 장애 시나리오별 대응

### 시나리오 1: Kafka 브로커 1대 장애

```
Before:
Broker 1 (Leader):  ✅ [Schema Data]
Broker 2 (Replica): ✅ [Schema Data]
Broker 3 (Replica): ✅ [Schema Data]

Broker 1 장애 발생! 💥

After (자동 복구):
Broker 1:           ❌ Down
Broker 2 (Leader):  ✅ [Schema Data] ← 자동 승격
Broker 3 (Replica): ✅ [Schema Data]

결과: 데이터 손실 없음! Schema Registry 정상 작동
```

**복구 시간**: 수 초 (자동 리더 선출)

### 시나리오 2: Kafka 브로커 2대 동시 장애

```
Before:
Broker 1 (Leader):  ✅ [Schema Data]
Broker 2 (Replica): ✅ [Schema Data]
Broker 3 (Replica): ✅ [Schema Data]

Broker 1, 2 장애 발생! 💥💥

After:
Broker 1:           ❌ Down
Broker 2:           ❌ Down
Broker 3 (Leader):  ✅ [Schema Data] ← 유일한 생존자

결과: 데이터 손실 없음! 1개만 살아도 OK
```

**복구 시간**: 수 초 ~ 수십 초

### 시나리오 3: Kafka 전체 클러스터 장애 (재시작)

```
Before:
Broker 1, 2, 3: ❌ All Down (정전, 네트워크 단절 등)

디스크는 살아있음:
Disk 1: [Schema Data] 💾
Disk 2: [Schema Data] 💾
Disk 3: [Schema Data] 💾

After (재시작):
Broker 1, 2, 3: ✅ 디스크에서 데이터 로드

결과: 데이터 손실 없음! 디스크에서 복구
```

**복구 시간**: 수 분 (클러스터 재시작 시간)

### 시나리오 4: 디스크까지 손상 (최악의 경우)

```
Before:
Broker 1 (Leader):  ✅ [Schema Data]
Broker 2 (Replica): ✅ [Schema Data]
Broker 3 (Replica): ✅ [Schema Data]

Broker 1, 2, 3의 디스크 모두 손상! 💥💥💥
(화재, 데이터센터 전체 장애 등)

Result: 데이터 손실 발생! ⚠️
```

**대응**: 백업에서 복구 (다음 섹션 참조)

---

## 3. Schema Registry 자체 보호 메커니즘

### 3-1. 메모리 캐싱

```kotlin
Schema Registry 내부:

┌──────────────────────┐
│  In-Memory Cache     │
│  ├─ Schema 1         │ ⚡ 빠른 조회
│  ├─ Schema 2         │
│  └─ Schema 3         │
└──────────┬───────────┘
           │ 캐시 미스 시
           ↓
┌──────────────────────┐
│  Kafka (_schemas)    │
│  ├─ Schema 1         │ 💾 영구 저장
│  ├─ Schema 2         │
│  └─ Schema 3         │
└──────────────────────┘
```

**장점**:
- Kafka 일시적 장애 시에도 캐시된 스키마로 동작
- 성능 향상 (디스크 I/O 최소화)

### 3-2. Schema Registry HA (High Availability)

```
프로덕션 권장 구성:

┌──────────────────┐    ┌──────────────────┐    ┌──────────────────┐
│ Schema Registry 1│    │ Schema Registry 2│    │ Schema Registry 3│
│   (Active)       │    │   (Active)       │    │   (Active)       │
└────────┬─────────┘    └────────┬─────────┘    └────────┬─────────┘
         │                       │                       │
         └───────────────────────┼───────────────────────┘
                                 │
                        ┌────────┴─────────┐
                        │  Kafka Cluster   │
                        │   (_schemas)     │
                        └──────────────────┘
```

**동작**:
- 3개 Schema Registry 모두 동일한 Kafka 읽음
- 1개가 죽어도 나머지 2개로 서비스 계속
- 로드밸런서로 트래픽 분산

---

## 4. 프로덕션 권장 설정

### 4-1. Kafka 클러스터 설정

```yaml
# _schemas 토픽 생성 시 (Schema Registry가 자동 생성)
kafka-configs --alter --topic _schemas \
  --add-config \
    replication.factor=3 \                # ⭐ 3개 복제
    min.insync.replicas=2 \               # ⭐ 최소 2개 동기화
    cleanup.policy=compact \              # 최신 스키마만 유지
    segment.ms=3600000 \                  # 1시간마다 세그먼트 생성
    retention.ms=-1                       # 무한 보관

# Kafka 브로커 설정 (server.properties)
num.replica.fetchers=4                    # 복제 속도 향상
log.flush.interval.messages=10000         # 디스크 flush 주기
log.flush.interval.ms=1000
```

### 4-2. Schema Registry 설정

```properties
# schema-registry.properties

# HA 구성
kafkastore.bootstrap.servers=kafka1:9092,kafka2:9092,kafka3:9092

# 토픽 설정
kafkastore.topic=_schemas
kafkastore.topic.replication.factor=3     # ⭐ 3개 복제

# 캐싱
schema.cache.size=10000                   # 캐시 크기
schema.cache.expiry.secs=300              # 캐시 만료 시간

# 타임아웃
kafkastore.timeout.ms=10000
```

### 4-3. Kubernetes 배포 예시

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: schema-registry
spec:
  replicas: 3  # ⭐ 3개 인스턴스
  template:
    spec:
      containers:
      - name: schema-registry
        image: confluentinc/cp-schema-registry:7.5.1
        env:
        - name: SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS
          value: "kafka-0:9092,kafka-1:9092,kafka-2:9092"
        - name: SCHEMA_REGISTRY_KAFKASTORE_TOPIC_REPLICATION_FACTOR
          value: "3"  # ⭐ 중요!
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
        livenessProbe:
          httpGet:
            path: /
            port: 8081
          initialDelaySeconds: 30
          periodSeconds: 10
```

---

## 5. 백업 전략

### 5-1. 스키마 Export (정기 백업)

```bash
#!/bin/bash
# schema-backup.sh

# 모든 스키마 추출
SUBJECTS=$(curl -s http://schema-registry:8081/subjects | jq -r '.[]')

mkdir -p schema-backup/$(date +%Y%m%d)

for subject in $SUBJECTS; do
  # 각 subject의 모든 버전 백업
  curl -s "http://schema-registry:8081/subjects/$subject/versions" | \
    jq -r '.[]' | \
    while read version; do
      curl -s "http://schema-registry:8081/subjects/$subject/versions/$version" \
        > "schema-backup/$(date +%Y%m%d)/${subject}-v${version}.json"
    done
done

# S3에 업로드
aws s3 sync schema-backup/ s3://my-bucket/schema-backup/
```

### 5-2. Kafka 토픽 백업

```bash
# _schemas 토픽 전체 백업
kafka-mirror-maker \
  --consumer.config consumer.properties \
  --producer.config producer-backup.properties \
  --whitelist _schemas

# 또는 Kafka Connect S3 Sink
{
  "name": "schemas-backup-sink",
  "config": {
    "connector.class": "io.confluent.connect.s3.S3SinkConnector",
    "topics": "_schemas",
    "s3.bucket.name": "kafka-backup",
    "flush.size": "100"
  }
}
```

### 5-3. 복구 절차

```bash
# 1. Kafka 클러스터가 살아있는 경우
# → Schema Registry 재시작만으로 복구 (Kafka에서 자동 로드)

# 2. Kafka 클러스터 전체 손실 시
# → 백업에서 복구

# (a) JSON 파일에서 복구
for file in schema-backup/20250117/*.json; do
  subject=$(basename $file | cut -d'-' -f1)
  curl -X POST \
    -H "Content-Type: application/vnd.schemaregistry.v1+json" \
    --data @$file \
    http://schema-registry:8081/subjects/$subject/versions
done

# (b) Kafka 토픽에서 복구
kafka-console-producer \
  --topic _schemas \
  --bootstrap-server kafka:9092 \
  < schemas-backup.log
```

---

## 6. 모니터링 및 알림

### 6-1. 핵심 메트릭

```yaml
# Prometheus 메트릭

# Schema Registry 상태
kafka_schema_registry_up{instance="sr1"}                     # 0 or 1
kafka_schema_registry_master_slave_role{instance="sr1"}     # master or slave

# Kafka 토픽 상태
kafka_topic_partition_replicas{topic="_schemas"}            # 복제본 수
kafka_topic_partition_in_sync_replicas{topic="_schemas"}    # 동기화된 복제본 수

# 알람 조건
ALERT SchemaRegistryDown
  IF kafka_schema_registry_up == 0
  FOR 1m

ALERT SchemasTopicUnderReplicated
  IF kafka_topic_partition_in_sync_replicas{topic="_schemas"} < 2
  FOR 5m
```

### 6-2. 헬스체크

```bash
# Schema Registry 헬스체크
curl -s http://schema-registry:8081/ | jq .

# Kafka 연결 확인
curl -s http://schema-registry:8081/subjects

# 특정 스키마 조회 테스트
curl -s http://schema-registry:8081/subjects/store-events-value/versions/latest
```

---

## 7. 현재 프로젝트 (테스트 환경)

### 현재 설정

```kotlin
// SharedContainers.kt
val schemaRegistryContainer: GenericContainer<*> by lazy {
    GenericContainer(DockerImageName.parse("confluentinc/cp-schema-registry:7.5.1"))
        .withReuse(true)  // ⭐ 컨테이너 재사용
        // ...
}

val kafkaContainer: KafkaContainer by lazy {
    KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.1"))
        .withReuse(true)  // ⭐ 컨테이너 재사용
}
```

**테스트 환경 특징**:
- Kafka 단일 브로커 (복제 없음)
- 디스크 영속성 있음 (`.withReuse(true)`)
- JVM 재시작 시에도 데이터 유지

**제한사항**:
- 컨테이너 삭제 시 데이터 손실
- 복제 없음 (단일 장애점)

### 프로덕션 마이그레이션 시

```yaml
# docker-compose.yml (로컬 개발)
services:
  kafka-1:
    image: confluentinc/cp-kafka:7.5.1
  kafka-2:
    image: confluentinc/cp-kafka:7.5.1
  kafka-3:
    image: confluentinc/cp-kafka:7.5.1

  schema-registry-1:
    environment:
      SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS: kafka-1:9092,kafka-2:9092,kafka-3:9092
      SCHEMA_REGISTRY_KAFKASTORE_TOPIC_REPLICATION_FACTOR: 3
  schema-registry-2:
    # 동일 설정
  schema-registry-3:
    # 동일 설정
```

---

## 8. 데이터 손실 위험도 평가

### 위험도 매트릭스

| 시나리오 | 발생 확률 | 데이터 손실 | 복구 시간 | 대응 |
|---------|----------|------------|----------|------|
| 브로커 1대 장애 | 높음 | 없음 | 수 초 | 자동 |
| 브로커 2대 장애 | 중간 | 없음 | 수십 초 | 자동 |
| 클러스터 재시작 | 중간 | 없음 | 수 분 | 수동 |
| 디스크 1대 손상 | 낮음 | 없음 | 수 분 | 자동 |
| 디스크 전체 손상 | 매우 낮음 | 있음 | 수 시간 | 백업 복구 |
| 데이터센터 장애 | 매우 낮음 | 있음 | 수 시간 | DR 사이트 |

### 권장 사항

```
단계별 보호 수준:

Level 1 (필수): Kafka 3-broker 클러스터 + replication.factor=3
→ 단일/이중 장애 대응

Level 2 (권장): Schema Registry 3-instance HA
→ Schema Registry 장애 대응

Level 3 (권장): 정기 백업 (일 1회 S3 업로드)
→ 재해 복구

Level 4 (선택): Multi-Region DR
→ 데이터센터 전체 장애 대응
```

---

## 결론

### Q: Kafka 장애 시 스키마 데이터가 날아가는가?

**A: 올바른 설정 시 거의 불가능합니다.**

1. ✅ **복제 (Replication)**: 3개 브로커에 복사 → 2개 죽어도 OK
2. ✅ **영구 저장 (Persistence)**: 디스크에 저장 → 재시작해도 OK
3. ✅ **캐싱 (Caching)**: 메모리에 캐시 → 일시 장애 시에도 OK
4. ✅ **백업 (Backup)**: 정기 백업 → 최악의 경우에도 복구 가능

### 유일한 데이터 손실 시나리오

```
모든 Kafka 브로커의 디스크가 동시에 손상
AND 백업이 없는 경우

→ 발생 확률: 거의 0에 수렴
→ 대응: 정기 백업 (하루 1회면 충분)
```

**현재 프로젝트는 테스트 환경이므로 단일 브로커지만, 프로덕션 배포 시 위 설정을 적용하면 안전합니다!**

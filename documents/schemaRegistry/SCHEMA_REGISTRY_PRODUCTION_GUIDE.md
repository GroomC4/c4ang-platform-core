# Schema Registry 프로덕션 배포 가이드

## 개요

테스트 환경에서는 단일 브로커와 복제본 1개로 동작하지만, 프로덕션 환경에서는 고가용성과 데이터 안정성을 위해 설정을 변경해야 합니다.

---

## 현재 설정 (테스트 환경)

```kotlin
// SharedContainers.kt
.withEnv("SCHEMA_REGISTRY_KAFKASTORE_TOPIC_REPLICATION_FACTOR", "1")
.withEnv("SCHEMA_REGISTRY_KAFKASTORE_MIN_INSYNC_REPLICAS", "1")
.withEnv("SCHEMA_REGISTRY_KAFKASTORE_TIMEOUT_MS", "10000")
.withEnv("SCHEMA_REGISTRY_KAFKASTORE_INIT_TIMEOUT_MS", "60000")
.withEnv("SCHEMA_REGISTRY_SCHEMA_COMPATIBILITY_LEVEL", "BACKWARD")
```

**특징**:
- ✅ 테스트에 최적화 (빠르고 간단)
- ⚠️ 단일 장애점 (브로커 1대만)
- ⚠️ 프로덕션 부적합

---

## 프로덕션 권장 설정

### 필수 변경사항

```properties
# 최소 3-broker Kafka 클러스터 필요

SCHEMA_REGISTRY_KAFKASTORE_TOPIC_REPLICATION_FACTOR=3       # ⭐ 3개 복제
SCHEMA_REGISTRY_KAFKASTORE_MIN_INSYNC_REPLICAS=2            # ⭐ 최소 2개 동기화
SCHEMA_REGISTRY_KAFKASTORE_TIMEOUT_MS=10000
SCHEMA_REGISTRY_KAFKASTORE_INIT_TIMEOUT_MS=60000
SCHEMA_REGISTRY_SCHEMA_COMPATIBILITY_LEVEL=BACKWARD
```

**효과**:
```
_schemas 토픽:
├─ Broker 1 (Leader):  [Schema Data]
├─ Broker 2 (Replica): [Schema Data]  ← 복제본
└─ Broker 3 (Replica): [Schema Data]  ← 복제본

- 최대 1개 브로커 장애 허용
- 데이터 손실 없음
- 자동 장애 복구
```

---

## 배포 방법

### 옵션 1: Docker Compose (로컬 개발/스테이징)

```yaml
# docker-compose.yml
version: '3.8'

services:
  kafka-1:
    image: confluentinc/cp-kafka:7.5.1
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: 'CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT'
      KAFKA_ADVERTISED_LISTENERS: 'PLAINTEXT://kafka-1:9092'
      KAFKA_PROCESS_ROLES: 'broker,controller'
      KAFKA_CONTROLLER_QUORUM_VOTERS: '1@kafka-1:9093,2@kafka-2:9093,3@kafka-3:9093'
    ports:
      - "9092:9092"

  kafka-2:
    image: confluentinc/cp-kafka:7.5.1
    environment:
      KAFKA_NODE_ID: 2
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: 'CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT'
      KAFKA_ADVERTISED_LISTENERS: 'PLAINTEXT://kafka-2:9092'
      KAFKA_PROCESS_ROLES: 'broker,controller'
      KAFKA_CONTROLLER_QUORUM_VOTERS: '1@kafka-1:9093,2@kafka-2:9093,3@kafka-3:9093'
    ports:
      - "9093:9092"

  kafka-3:
    image: confluentinc/cp-kafka:7.5.1
    environment:
      KAFKA_NODE_ID: 3
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: 'CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT'
      KAFKA_ADVERTISED_LISTENERS: 'PLAINTEXT://kafka-3:9092'
      KAFKA_PROCESS_ROLES: 'broker,controller'
      KAFKA_CONTROLLER_QUORUM_VOTERS: '1@kafka-1:9093,2@kafka-2:9093,3@kafka-3:9093'
    ports:
      - "9094:9092"

  schema-registry-1:
    image: confluentinc/cp-schema-registry:7.5.1
    environment:
      SCHEMA_REGISTRY_HOST_NAME: schema-registry-1
      SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS: 'kafka-1:9092,kafka-2:9092,kafka-3:9092'
      SCHEMA_REGISTRY_KAFKASTORE_TOPIC_REPLICATION_FACTOR: 3        # ⭐
      SCHEMA_REGISTRY_KAFKASTORE_MIN_INSYNC_REPLICAS: 2             # ⭐
      SCHEMA_REGISTRY_SCHEMA_COMPATIBILITY_LEVEL: BACKWARD
      SCHEMA_REGISTRY_LISTENERS: 'http://0.0.0.0:8081'
    ports:
      - "8081:8081"
    depends_on:
      - kafka-1
      - kafka-2
      - kafka-3

  schema-registry-2:
    image: confluentinc/cp-schema-registry:7.5.1
    environment:
      SCHEMA_REGISTRY_HOST_NAME: schema-registry-2
      SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS: 'kafka-1:9092,kafka-2:9092,kafka-3:9092'
      SCHEMA_REGISTRY_KAFKASTORE_TOPIC_REPLICATION_FACTOR: 3
      SCHEMA_REGISTRY_KAFKASTORE_MIN_INSYNC_REPLICAS: 2
      SCHEMA_REGISTRY_SCHEMA_COMPATIBILITY_LEVEL: BACKWARD
      SCHEMA_REGISTRY_LISTENERS: 'http://0.0.0.0:8081'
    ports:
      - "8082:8081"

  schema-registry-3:
    image: confluentinc/cp-schema-registry:7.5.1
    environment:
      SCHEMA_REGISTRY_HOST_NAME: schema-registry-3
      SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS: 'kafka-1:9092,kafka-2:9092,kafka-3:9092'
      SCHEMA_REGISTRY_KAFKASTORE_TOPIC_REPLICATION_FACTOR: 3
      SCHEMA_REGISTRY_KAFKASTORE_MIN_INSYNC_REPLICAS: 2
      SCHEMA_REGISTRY_SCHEMA_COMPATIBILITY_LEVEL: BACKWARD
      SCHEMA_REGISTRY_LISTENERS: 'http://0.0.0.0:8081'
    ports:
      - "8083:8081"
```

**실행**:
```bash
docker-compose up -d
```

### 옵션 2: Kubernetes (프로덕션)

#### Kafka StatefulSet

```yaml
# kafka-statefulset.yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: kafka
spec:
  serviceName: kafka
  replicas: 3
  selector:
    matchLabels:
      app: kafka
  template:
    metadata:
      labels:
        app: kafka
    spec:
      containers:
      - name: kafka
        image: confluentinc/cp-kafka:7.5.1
        ports:
        - containerPort: 9092
          name: plaintext
        - containerPort: 9093
          name: controller
        env:
        - name: KAFKA_NODE_ID
          valueFrom:
            fieldRef:
              fieldPath: metadata.name
        - name: KAFKA_PROCESS_ROLES
          value: "broker,controller"
        - name: KAFKA_LISTENER_SECURITY_PROTOCOL_MAP
          value: "CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT"
        volumeMounts:
        - name: data
          mountPath: /var/lib/kafka/data
  volumeClaimTemplates:
  - metadata:
      name: data
    spec:
      accessModes: ["ReadWriteOnce"]
      resources:
        requests:
          storage: 100Gi
```

#### Schema Registry Deployment

```yaml
# schema-registry-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: schema-registry
spec:
  replicas: 3  # ⭐ HA 구성
  selector:
    matchLabels:
      app: schema-registry
  template:
    metadata:
      labels:
        app: schema-registry
    spec:
      containers:
      - name: schema-registry
        image: confluentinc/cp-schema-registry:7.5.1
        ports:
        - containerPort: 8081
        env:
        - name: SCHEMA_REGISTRY_HOST_NAME
          valueFrom:
            fieldRef:
              fieldPath: metadata.name
        - name: SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS
          value: "kafka-0.kafka:9092,kafka-1.kafka:9092,kafka-2.kafka:9092"
        - name: SCHEMA_REGISTRY_KAFKASTORE_TOPIC_REPLICATION_FACTOR
          value: "3"  # ⭐
        - name: SCHEMA_REGISTRY_KAFKASTORE_MIN_INSYNC_REPLICAS
          value: "2"  # ⭐
        - name: SCHEMA_REGISTRY_SCHEMA_COMPATIBILITY_LEVEL
          value: "BACKWARD"
        - name: SCHEMA_REGISTRY_LISTENERS
          value: "http://0.0.0.0:8081"
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
        livenessProbe:
          httpGet:
            path: /
            port: 8081
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /subjects
            port: 8081
          initialDelaySeconds: 20
          periodSeconds: 5
---
apiVersion: v1
kind: Service
metadata:
  name: schema-registry
spec:
  type: ClusterIP
  ports:
  - port: 8081
    targetPort: 8081
  selector:
    app: schema-registry
```

**배포**:
```bash
kubectl apply -f kafka-statefulset.yaml
kubectl apply -f schema-registry-deployment.yaml
```

### 옵션 3: Helm Chart (권장)

```yaml
# values.yaml
kafka:
  replicaCount: 3
  persistence:
    enabled: true
    size: 100Gi
  resources:
    requests:
      memory: 2Gi
      cpu: 1000m

schemaRegistry:
  replicaCount: 3
  kafka:
    bootstrapServers: "kafka-0.kafka:9092,kafka-1.kafka:9092,kafka-2.kafka:9092"

  config:
    kafkastoreTopicReplicationFactor: 3        # ⭐
    kafkastoreMinInsyncReplicas: 2             # ⭐
    schemaCompatibilityLevel: BACKWARD

  resources:
    requests:
      memory: 512Mi
      cpu: 250m
    limits:
      memory: 1Gi
      cpu: 500m

  livenessProbe:
    enabled: true
    path: /
    initialDelaySeconds: 30
    periodSeconds: 10

  readinessProbe:
    enabled: true
    path: /subjects
    initialDelaySeconds: 20
    periodSeconds: 5
```

**설치**:
```bash
helm repo add confluentinc https://confluentinc.github.io/cp-helm-charts/
helm install my-kafka confluentinc/cp-kafka --values values.yaml
```

---

## 설정 검증

### 1. _schemas 토픽 설정 확인

```bash
# Kubernetes 환경
kubectl exec -it kafka-0 -- kafka-topics \
  --describe \
  --topic _schemas \
  --bootstrap-server localhost:9092

# 예상 출력:
Topic: _schemas
PartitionCount: 1
ReplicationFactor: 3  ✅
Configs: cleanup.policy=compact,
         min.insync.replicas=2  ✅
```

### 2. Schema Registry 클러스터 상태 확인

```bash
# 각 인스턴스 헬스체크
curl http://schema-registry-1:8081/
curl http://schema-registry-2:8081/
curl http://schema-registry-3:8081/

# 모두 OK 응답 확인
```

### 3. 복제 동작 테스트

```bash
# 스키마 등록
curl -X POST http://schema-registry:8081/subjects/test-value/versions \
  -H "Content-Type: application/vnd.schemaregistry.v1+json" \
  -d '{
    "schema": "{\"type\":\"record\",\"name\":\"Test\",\"fields\":[{\"name\":\"id\",\"type\":\"long\"}]}"
  }'

# 다른 인스턴스에서 조회 (복제 확인)
curl http://schema-registry-2:8081/subjects/test-value/versions/latest

# 동일한 스키마 반환되면 복제 성공! ✅
```

---

## 모니터링

### Prometheus 메트릭

```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'schema-registry'
    static_configs:
      - targets:
        - 'schema-registry-1:8081'
        - 'schema-registry-2:8081'
        - 'schema-registry-3:8081'
    metrics_path: '/metrics'
```

### 주요 메트릭

```promql
# Schema Registry 상태
up{job="schema-registry"}

# Kafka 토픽 복제 상태
kafka_server_replicamanager_partitioncount{topic="_schemas"}
kafka_server_replicamanager_underreplicatedpartitions

# 알람 설정
ALERT SchemaRegistryDown
  IF up{job="schema-registry"} == 0
  FOR 1m

ALERT SchemasTopicUnderReplicated
  IF kafka_server_replicamanager_underreplicatedpartitions > 0
  FOR 5m
```

---

## 장애 시나리오 및 복구

### 시나리오 1: Schema Registry 인스턴스 1개 다운

```
Before:
SR-1 ✅  SR-2 ✅  SR-3 ✅

SR-1 다운:
SR-1 ❌  SR-2 ✅  SR-3 ✅

영향: 없음 (로드밸런서가 자동으로 SR-2, SR-3로 라우팅)
복구: 자동 (Kubernetes가 Pod 재시작)
```

### 시나리오 2: Kafka 브로커 1개 다운

```
Before:
Kafka-1 (Leader) ✅  Kafka-2 ✅  Kafka-3 ✅
_schemas 복제: 3개 모두 보유

Kafka-1 다운:
Kafka-1 ❌  Kafka-2 (Leader) ✅  Kafka-3 ✅
_schemas: Kafka-2가 Leader로 승격 (자동)

영향: 없음 (Schema Registry가 자동으로 새 Leader 사용)
복구: 자동 (수 초 내)
```

### 시나리오 3: 전체 재시작

```bash
# 순차적 재시작 (무중단 배포)

# 1. Kafka 재시작 (하나씩)
kubectl rollout restart statefulset/kafka --wait=true

# 2. Schema Registry 재시작
kubectl rollout restart deployment/schema-registry

# 영향: 없음 (롤링 업데이트)
```

---

## 백업 전략

### 자동 백업 스크립트

```bash
#!/bin/bash
# schema-registry-backup.sh

BACKUP_DIR="/backup/schema-registry"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
SR_URL="http://schema-registry:8081"

# 모든 스키마 백업
mkdir -p "$BACKUP_DIR/$TIMESTAMP"

curl -s "$SR_URL/subjects" | jq -r '.[]' | while read subject; do
  versions=$(curl -s "$SR_URL/subjects/$subject/versions" | jq -r '.[]')

  for version in $versions; do
    curl -s "$SR_URL/subjects/$subject/versions/$version" \
      > "$BACKUP_DIR/$TIMESTAMP/${subject}-v${version}.json"
  done
done

# S3 업로드
aws s3 sync "$BACKUP_DIR/$TIMESTAMP" "s3://my-bucket/schema-backup/$TIMESTAMP/"

echo "Backup completed: $TIMESTAMP"
```

**Cron 설정**:
```cron
# 매일 새벽 2시 백업
0 2 * * * /opt/scripts/schema-registry-backup.sh
```

---

## 체크리스트

### 배포 전 확인사항

- [ ] Kafka 클러스터: 최소 3 브로커
- [ ] `replication.factor`: 3
- [ ] `min.insync.replicas`: 2
- [ ] Schema Registry: 최소 3 인스턴스
- [ ] 로드밸런서 설정
- [ ] 모니터링 구성
- [ ] 백업 스크립트 설정
- [ ] 알람 설정

### 배포 후 확인사항

- [ ] `_schemas` 토픽 생성 확인
- [ ] 복제본 3개 확인
- [ ] 모든 Schema Registry 인스턴스 정상
- [ ] 스키마 등록/조회 테스트
- [ ] 복제 동작 확인
- [ ] 모니터링 대시보드 확인

---

## 비용 최적화

### 리소스 권장사항

| 컴포넌트 | 최소 사양 | 권장 사양 | 비고 |
|---------|---------|----------|------|
| Kafka (각) | 2 CPU, 4GB | 4 CPU, 8GB | 디스크 100GB+ |
| Schema Registry (각) | 0.5 CPU, 512MB | 1 CPU, 1GB | 경량 서비스 |

### 비용 절감 팁

1. **Dev/Staging 환경**: replication.factor=2 (3 대신)
2. **Spot 인스턴스**: Kafka에는 비권장, Schema Registry는 가능
3. **Auto Scaling**: Schema Registry만 적용 (Kafka는 StatefulSet)

---

## 결론

### 프로덕션 최소 요구사항

```
✅ Kafka 클러스터: 3 브로커
✅ Schema Registry: 3 인스턴스
✅ replication.factor=3
✅ min.insync.replicas=2
✅ 모니터링 + 알람
✅ 백업 (일 1회)
```

### 예상 효과

```
- 99.9% 가용성 보장
- 단일 장애점 제거
- 데이터 손실 위험 거의 0%
- 자동 장애 복구
```

**이 설정으로 엔터프라이즈급 안정성을 확보할 수 있습니다!**

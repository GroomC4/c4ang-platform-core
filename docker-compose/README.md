# Docker Compose 환경 설정 가이드

이 디렉토리는 로컬 개발 환경과 통합 테스트를 위한 Docker Compose 설정을 관리합니다.

## 📁 디렉토리 구조

```
docker-compose/
├── docker-compose.local.yml         # 로컬 개발 환경 (통합 설정)
├── base/
│   └── docker-compose.base.yml      # Redis 기본 설정
├── postgres/
│   └── docker-compose.postgres.yml  # PostgreSQL Primary/Replica
├── kafka/
│   └── docker-compose.kafka.yml     # Kafka, Zookeeper, Schema Registry
└── test/
    └── docker-compose-integration-test.yml  # 통합 테스트용
```

## 🚀 사용 방법

### 1. 로컬 개발 환경 (권장)

**전체 인프라를 한번에 실행:**

```bash
# c4ang-platform-core/docker-compose 디렉토리에서
docker compose -f docker-compose.local.yml up -d

# 또는 프로젝트 루트에서
docker compose -f c4ang-platform-core/docker-compose/docker-compose.local.yml up -d
```

**포함된 서비스:**
- PostgreSQL Primary (포트: 15432)
- PostgreSQL Replica (포트: 15433)
- Redis (포트: 6379)
- Kafka (포트: 9092)
- Zookeeper (포트: 2181)
- Schema Registry (포트: 8081)

**중지:**
```bash
docker compose -f docker-compose.local.yml down

# 볼륨까지 삭제 (데이터 초기화)
docker compose -f docker-compose.local.yml down -v
```

---

### 2. 개별 서비스 실행 (선택적)

필요한 서비스만 선택적으로 실행할 수 있습니다.

#### PostgreSQL만 실행
```bash
docker compose -f postgres/docker-compose.postgres.yml up -d
```

#### Redis만 실행
```bash
docker compose -f base/docker-compose.base.yml up -d
```

#### Kafka 스택만 실행 (Kafka + Zookeeper + Schema Registry)
```bash
docker compose -f kafka/docker-compose.kafka.yml up -d
```

---

### 3. 통합 테스트 환경

통합 테스트 실행 시 자동으로 사용됩니다.

```bash
# 테스트 실행 (Gradle이 자동으로 docker-compose 실행)
./gradlew integrationTest

# 수동으로 테스트 환경 실행
docker compose -f test/docker-compose-integration-test.yml up -d
```

**특징:**
- 포트가 랜덤으로 할당되어 로컬 환경과 충돌 없음
- 테스트 종료 후 자동으로 정리됨

---

## 📊 서비스 접속 정보

### 로컬 개발 환경

| 서비스 | 호스트 | 포트 | 사용자/비밀번호 |
|--------|--------|------|------------------|
| **PostgreSQL Primary** | localhost | 15432 | application / application |
| **PostgreSQL Replica** | localhost | 15433 | application / application |
| **Redis** | localhost | 6379 | (없음) |
| **Kafka** | localhost | 9092 | (없음) |
| **Zookeeper** | localhost | 2181 | (없음) |
| **Schema Registry** | localhost | 8081 | (없음) |

### 애플리케이션 연결 설정

**application-local.yml 예시:**
```yaml
spring:
  datasource:
    master:
      url: jdbc:postgresql://localhost:15432/groom
      username: application
      password: application
    replica:
      url: jdbc:postgresql://localhost:15433/groom
      username: application
      password: application
  data:
    redis:
      host: localhost
      port: 6379

kafka:
  bootstrap-servers: localhost:9092
  schema-registry:
    url: http://localhost:8081
```

---

## 🔍 상태 확인

### 실행 중인 컨테이너 확인
```bash
docker compose -f docker-compose.local.yml ps
```

### 로그 확인
```bash
# 전체 로그
docker compose -f docker-compose.local.yml logs

# 특정 서비스 로그
docker compose -f docker-compose.local.yml logs kafka

# 실시간 로그 팔로우
docker compose -f docker-compose.local.yml logs -f
```

### Health Check 확인
```bash
# PostgreSQL
docker exec postgres_primary pg_isready -U application -d groom

# Redis
docker exec ecommerce_redis redis-cli ping

# Kafka
docker exec ecommerce_kafka kafka-broker-api-versions --bootstrap-server localhost:9092

# Schema Registry
curl http://localhost:8081/
```

---

## 🛠️ Kafka 관리

### 토픽 생성
```bash
docker exec ecommerce_kafka kafka-topics \
  --create \
  --bootstrap-server localhost:9092 \
  --topic store.info.updated \
  --partitions 3 \
  --replication-factor 1
```

### 토픽 목록 확인
```bash
docker exec ecommerce_kafka kafka-topics \
  --list \
  --bootstrap-server localhost:9092
```

### 메시지 발행 (테스트)
```bash
docker exec -it ecommerce_kafka kafka-console-producer \
  --bootstrap-server localhost:9092 \
  --topic store.info.updated
```

### 메시지 소비 (테스트)
```bash
docker exec -it ecommerce_kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic store.info.updated \
  --from-beginning
```

### Schema Registry에서 스키마 확인
```bash
# 모든 스키마 subject 조회
curl http://localhost:8081/subjects

# 특정 subject의 스키마 조회
curl http://localhost:8081/subjects/store.info.updated-value/versions/latest
```

---

## 🐛 트러블슈팅

### 포트 충돌 오류
```bash
# 포트를 사용 중인 프로세스 확인 (macOS)
lsof -i :9092

# 해당 프로세스 종료
kill -9 <PID>
```

### 볼륨 삭제 (데이터 초기화)
```bash
# 컨테이너와 볼륨 모두 삭제
docker compose -f docker-compose.local.yml down -v

# 다시 시작
docker compose -f docker-compose.local.yml up -d
```

### Schema Registry 연결 실패
```bash
# Kafka가 완전히 시작될 때까지 대기
docker compose -f docker-compose.local.yml up -d kafka
sleep 30
docker compose -f docker-compose.local.yml up -d schema-registry
```

### 네트워크 문제
```bash
# 네트워크 삭제 및 재생성
docker network prune
docker compose -f docker-compose.local.yml up -d
```

---

## 🔄 환경 변수 커스터마이징

`.env` 파일을 생성하여 기본 설정을 변경할 수 있습니다:

```bash
# docker-compose/.env
PRIMARY_POSTGRES_PORT=15432
REPLICA_POSTGRES_PORT=15433
REDIS_PORT=6379
KAFKA_PORT=9092
SCHEMA_REGISTRY_PORT=8081
```

---

## 📚 관련 문서

- [PostgreSQL Replication 설정](../docker/postgres/README.md)
- [Kafka 이벤트 명세](../../c4ang-contract-hub/docs/interface/kafka-event-specifications.md)
- [Avro 스키마 가이드](../../c4ang-contract-hub/README.md)

---

## ⚠️ 주의사항

1. **로컬 개발 환경에서는 `docker-compose.local.yml` 사용 권장**
   - 모든 인프라가 한번에 실행되어 편리
   - 네트워크 설정이 자동으로 연결됨

2. **통합 테스트는 자동으로 관리됨**
   - 직접 실행할 필요 없음
   - Gradle이 자동으로 docker-compose 실행 및 정리

3. **Schema Registry 포트 충돌 주의**
   - Schema Registry: 8081
   - Store Service: 8082 (이전 8081에서 변경)

4. **운영 환경에서는 사용하지 않음**
   - 이 설정은 로컬 개발/테스트 전용입니다
   - 운영 환경은 별도의 Kubernetes/AWS 설정 사용

# 로컬 개발 환경 가이드

이 디렉토리는 개발자가 로컬에서 인프라를 수동으로 실행할 때 사용하는 Docker Compose 설정입니다.

> **참고**: 자동화된 통합 테스트는 `testcontainers-starter`를 사용합니다.
> 이 로컬 환경은 개발자가 서비스를 직접 실행하며 개발할 때 사용합니다.

---

## 📁 디렉토리 구조

```
local-dev/
├── README.md (이 파일)
├── docker-compose.local.yml         # 전체 인프라 통합 실행
├── docker/
│   └── postgres/
│       ├── primary-init/            # Primary DB 초기화 스크립트
│       └── replica-init/            # Replica DB 초기화 스크립트
├── postgres/
│   └── docker-compose.postgres.yml  # PostgreSQL만 실행
├── kafka/
│   └── docker-compose.kafka.yml     # Kafka만 실행 (KRaft 모드)
└── base/
    └── docker-compose.base.yml      # Redis만 실행
```

---

## 🚀 사용 방법

### 1. 전체 인프라 실행 (권장)

**모든 서비스를 한번에 시작:**

```bash
# 프로젝트 루트에서
cd local-dev
docker compose -f docker-compose.local.yml up -d

# 또는 절대 경로로
docker compose -f /path/to/c4ang-platform-core/local-dev/docker-compose.local.yml up -d
```

**포함된 서비스:**
- ✅ PostgreSQL Primary (포트: 15432)
- ✅ PostgreSQL Replica (포트: 15433)
- ✅ Redis (포트: 6379)
- ✅ Kafka (KRaft 모드, 포트: 9092)
- ✅ Schema Registry (포트: 8081)

**중지:**
```bash
docker compose -f docker-compose.local.yml down

# 볼륨까지 삭제 (데이터 초기화)
docker compose -f docker-compose.local.yml down -v
```

---

### 2. 개별 서비스 실행 (선택적)

필요한 서비스만 선택적으로 실행할 수 있습니다.

#### PostgreSQL Primary + Replica
```bash
cd local-dev
docker compose -f postgres/docker-compose.postgres.yml up -d
```

#### Redis만 실행
```bash
cd local-dev
docker compose -f base/docker-compose.base.yml up -d
```

#### Kafka + Schema Registry (KRaft 모드)
```bash
cd local-dev
docker compose -f kafka/docker-compose.kafka.yml up -d
```

---

## 📊 서비스 접속 정보

### 로컬 개발 환경

| 서비스 | 호스트 | 포트 | 사용자/비밀번호 |
|--------|--------|------|------------------|
| **PostgreSQL Primary** | localhost | 15432 | application / application |
| **PostgreSQL Replica** | localhost | 15433 | application / application |
| **Redis** | localhost | 6379 | (없음) |
| **Kafka** | localhost | 9092 | (없음) |
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
cd local-dev
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

### Health Check
```bash
# PostgreSQL
docker exec postgres_primary pg_isready -U application -d groom

# Redis
docker exec local_redis redis-cli ping

# Kafka
docker exec local_kafka kafka-broker-api-versions --bootstrap-server localhost:9092

# Schema Registry
curl http://localhost:8081/
```

---

## 🛠️ Kafka 관리

### 토픽 생성
```bash
docker exec local_kafka kafka-topics \
  --create \
  --bootstrap-server localhost:9092 \
  --topic my.topic \
  --partitions 3 \
  --replication-factor 1
```

### 토픽 목록 확인
```bash
docker exec local_kafka kafka-topics \
  --list \
  --bootstrap-server localhost:9092
```

### 메시지 발행 (테스트)
```bash
docker exec -it local_kafka kafka-console-producer \
  --bootstrap-server localhost:9092 \
  --topic my.topic
```

### 메시지 소비 (테스트)
```bash
docker exec -it local_kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic my.topic \
  --from-beginning
```

### Schema Registry에서 스키마 확인
```bash
# 모든 스키마 subject 조회
curl http://localhost:8081/subjects

# 특정 subject의 스키마 조회
curl http://localhost:8081/subjects/my.topic-value/versions/latest
```

---

## 🐛 트러블슈팅

### 포트 충돌 오류
```bash
# 포트를 사용 중인 프로세스 확인 (macOS)
lsof -i :15432
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
# local-dev/.env
PRIMARY_POSTGRES_PORT=15432
REPLICA_POSTGRES_PORT=15433
REDIS_PORT=6379
KAFKA_PORT=9092
SCHEMA_REGISTRY_PORT=8081
```

---

## ⚠️ 주의사항

1. **로컬 개발 전용**
   - 이 설정은 로컬 개발 환경 전용입니다
   - 운영 환경에서는 사용하지 마세요

2. **통합 테스트와 별도**
   - 통합 테스트는 `testcontainers-starter`가 자동으로 관리합니다
   - 로컬 개발 환경은 개발자가 수동으로 관리합니다

3. **데이터 영구성**
   - Docker 볼륨에 데이터가 저장됩니다
   - `down -v` 명령으로 완전히 초기화할 수 있습니다

4. **포트 충돌 주의**
   - 로컬에서 이미 PostgreSQL, Redis 등이 실행 중이면 충돌합니다
   - 포트를 변경하거나 기존 서비스를 중지하세요

---

## 📚 관련 문서

- **[통합 테스트 가이드](../documents/guides/SERVICE_INTEGRATION_GUIDE.md)** - testcontainers-starter 사용법
- **[유지보수 가이드](../documents/guides/MAINTAINER_GUIDE.md)** - platform-core 관리
- **[프로젝트 README](../README.md)** - 프로젝트 개요

---

**Happy Local Development! 🚀**

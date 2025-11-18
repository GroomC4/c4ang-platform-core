# RC8 도메인 서버 적용 가이드

## 📌 개요

`testcontainers-starter:1.2.2-RC8`에서 **Kafka 토픽 TimeoutException 문제가 해결**되었습니다.

### 변경 사항

1. ✅ **Kafka 토픽 자동 생성 활성화** (기본값)
2. ✅ **사전 정의 토픽 지원** (파티션, 복제 계수, 설정 커스터마이징 가능)
3. ✅ **TimeoutException 완전 해결**

---

## 🚀 Step 1: 의존성 업데이트

### build.gradle.kts

```kotlin
dependencies {
    // 기존
    // testImplementation("com.groom.platform:testcontainers-starter:1.2.2-RC7")

    // 변경 ⬇️
    testImplementation("com.groom.platform:testcontainers-starter:1.2.2-RC8")
}
```

### 의존성 새로고침

```bash
./gradlew build --refresh-dependencies
```

---

## 🔧 Step 2: application-test.yml 설정 업데이트

### 옵션 1: 최소 설정 (가장 간단)

아무 설정도 추가하지 않아도 됩니다. 모든 토픽이 자동으로 생성됩니다.

```yaml
# src/test/resources/application-test.yml
testcontainers:
  postgres:
    enabled: true
    replica-enabled: true
    schema-location: project:store-api/sql/schema.sql
    # ⚠️ project: 스킴은 프로젝트 루트 기준입니다 (모듈 루트 아님)
    #    프로젝트 루트 = settings.gradle.kts가 있는 위치
    #    예: project:{모듈명}/sql/schema.sql
    # 📖 자세한 설명: documents/guides/SERVICE_INTEGRATION_GUIDE.md 참고

  redis:
    enabled: true

  kafka:
    enabled: true
    # 끝! 모든 토픽 자동 생성 ✅
```

**이 옵션이 적합한 경우:**
- 빠르게 테스트 환경을 구축하고 싶을 때
- 토픽 설정에 신경 쓰고 싶지 않을 때

---

### 옵션 2: 사전 정의 토픽 추가 (권장)

중요한 토픽은 운영 환경과 동일하게 설정하되, 예상 못한 토픽은 자동 생성되도록 합니다.

```yaml
# src/test/resources/application-test.yml
testcontainers:
  postgres:
    enabled: true
    replica-enabled: true
    schema-location: project:store-api/sql/schema.sql

  redis:
    enabled: true

  kafka:
    enabled: true
    auto-create-topics: true  # 기본값, 생략 가능
    topics:
      # 스토어 정보 업데이트 이벤트
      - name: store.info.updated
        partitions: 3           # 높은 처리량
        replication-factor: 1
        config:
          retention.ms: 604800000  # 7일 보관

      # 스토어 삭제 이벤트
      - name: store.deleted
        partitions: 1           # 순서 보장
        replication-factor: 1
```

**이 옵션이 적합한 경우:**
- 운영 환경과 동일한 토픽 설정으로 테스트하고 싶을 때
- 중요 토픽은 명시적으로 관리하되, 유연성도 유지하고 싶을 때

---

### 옵션 3: 엄격한 제어 (운영 환경 시뮬레이션)

자동 생성을 비활성화하고, 정의된 토픽만 사용합니다.

```yaml
testcontainers:
  kafka:
    enabled: true
    auto-create-topics: false  # 자동 생성 비활성화
    topics:
      - name: store.info.updated
        partitions: 3
        replication-factor: 1
      - name: store.deleted
        partitions: 1
        replication-factor: 1
    # 목록에 없는 토픽 사용 시 TimeoutException 발생 (의도적)
```

**이 옵션이 적합한 경우:**
- 운영 환경과 완전히 동일한 제약 조건으로 테스트하고 싶을 때
- 잘못된 토픽 사용을 강제로 방지하고 싶을 때

---

## 📝 Step 3: 토픽 설정 가이드

### 토픽 이름 규칙

```yaml
# 권장 네이밍 패턴
<domain>.<entity>.<action>

# 예시
store.info.updated
order.payment.completed
user.profile.deleted
```

### 파티션 수 결정

| 파티션 수 | 사용 사례 |
|---------|---------|
| 1 | 메시지 순서 보장 필수, 낮은 처리량 |
| 2-3 | 중간 처리량, 순서 보장 불필요 |
| 6+ | 높은 처리량, 병렬 처리 필요 |

### 보관 기간 설정

| 설정값 | 기간 | 사용 사례 |
|-------|-----|---------|
| 86400000 | 1일 | 일시적 알림, 로그 |
| 604800000 | 7일 | 일반 이벤트 |
| 2592000000 | 30일 | 중요 비즈니스 이벤트 |

### 압축 방식

| 압축 | 특징 | 추천 |
|-----|-----|-----|
| none | 압축 없음, 빠름 | 작은 메시지 |
| gzip | 높은 압축률, 느림 | 큰 메시지, 네트워크 대역폭 제한 |
| snappy | 중간 압축률, 빠름 | 일반적인 경우 (권장) |
| lz4 | 낮은 압축률, 매우 빠름 | 실시간 처리 |

---

## ✅ Step 4: 테스트 실행

### 1. 단일 테스트 실행

```bash
./gradlew test --tests UpdateStoreServiceIntegrationTest
```

### 2. 전체 통합 테스트 실행

```bash
./gradlew test
```

### 3. 예상 로그

성공 시 다음과 같은 로그가 출력됩니다:

```
🚀 Starting shared Kafka container...
✅ Kafka container started and ready (PLAINTEXT://localhost:xxxxx)
   - Auto Create Topics: Enabled
   - Default Partitions: 1
   - Replication Factor: 1

📄 Kafka: Using shared singleton container
✅ Kafka predefined topics created:
   - store.info.updated (partitions=3, replication-factor=1)
   - store.deleted (partitions=1, replication-factor=1)
```

---

## 🐛 트러블슈팅

### 문제 1: 여전히 TimeoutException 발생

**증상:**
```
org.apache.kafka.common.errors.TimeoutException:
Topic xxx not present in metadata after 60000 ms.
```

**해결:**
1. RC8 의존성이 제대로 다운로드되었는지 확인:
   ```bash
   ./gradlew dependencies --refresh-dependencies | grep testcontainers-starter
   ```

2. 캐시 삭제 후 재빌드:
   ```bash
   ./gradlew clean build --refresh-dependencies
   ```

3. Docker 컨테이너 재시작:
   ```bash
   docker ps  # 실행 중인 컨테이너 확인
   docker stop <kafka-container-id>
   ```

---

### 문제 2: 토픽 설정이 적용되지 않음

**증상:**
토픽이 생성되지만 파티션 수가 1로 고정됨

**원인:**
- YAML 들여쓰기 오류
- 설정 파일이 로드되지 않음

**해결:**
1. YAML 문법 검증:
   ```yaml
   # ❌ 잘못된 들여쓰기
   testcontainers:
   kafka:
     topics:

   # ✅ 올바른 들여쓰기
   testcontainers:
     kafka:
       topics:
   ```

2. application-test.yml 위치 확인:
   ```
   src/test/resources/application-test.yml  ✅
   ```

---

### 문제 3: "Multiple topics with same name" 에러

**증상:**
```
Topic 'order.created' is defined multiple times
```

**해결:**
중복 토픽 정의 제거:

```yaml
# ❌ 잘못됨
testcontainers:
  kafka:
    topics:
      - name: order.created
        partitions: 1
      - name: order.created  # 중복!
        partitions: 3

# ✅ 올바름
testcontainers:
  kafka:
    topics:
      - name: order.created
        partitions: 3
```

---

## 📚 추가 참고 자료

- **[서비스 통합 가이드](documents/guides/SERVICE_INTEGRATION_GUIDE.md)** - Kafka 토픽 설정 상세 가이드
- **[CHANGELOG](CHANGELOG.md)** - RC8 변경 사항 전체 목록

---

## ❓ 질문이나 문제가 있나요?

- **GitHub Issues:** https://github.com/GroomC4/c4ang-platform-core/issues
- **팀 채널:** Slack #platform-support
- **담당자:** @hayden-han

---

## 🎯 체크리스트

마이그레이션 완료 전에 다음 항목들을 확인하세요:

- [ ] `testcontainers-starter:1.2.2-RC8` 의존성 업데이트
- [ ] `./gradlew build --refresh-dependencies` 실행
- [ ] `application-test.yml`에 Kafka 설정 추가 (선택사항)
- [ ] 테스트 실행 및 성공 확인
- [ ] Kafka 토픽이 정상적으로 생성되는지 로그 확인
- [ ] 기존 실패 테스트 (4개) 통과 확인

---

**Happy Testing! 🚀**

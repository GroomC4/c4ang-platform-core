# 🤖 도메인 서버 Platform Core 통합 요청 프롬프트

도메인 서버 개발자가 AI 어시스턴트에게 요청할 때 사용할 수 있는 프롬프트 템플릿입니다.

---

## 📝 기본 통합 요청

```
Platform Core의 datasource-starter와 testcontainers-starter를 우리 서비스에 통합해줘.

저장소 정보:
- Platform Core: https://github.com/GroomC4/c4ang-platform-core (branch: fix/datasource-circular-reference)
- 가이드 문서: DOMAIN_SERVER_INTEGRATION_GUIDE.md
- 빠른 시작 템플릿: QUICK_START_TEMPLATE.md

현재 서비스 정보:
- 서비스명: [your-service-name]
- 데이터베이스: PostgreSQL
- 현재 DataSource 설정 위치: [파일 경로]

요청사항:
1. build.gradle.kts에 의존성 추가
2. application.yml 설정 (master/replica)
3. 테스트 환경 설정 (TestContainers)
4. 기존 DataSource 설정 제거
5. 서비스 클래스에 @Transactional 적용
```

---

## 🔧 커스텀 설정 요청

```
Platform Core를 통합하되, 우리 서비스의 특별한 요구사항을 반영해줘.

Platform Core 참조:
- 저장소: https://github.com/GroomC4/c4ang-platform-core
- DataSourceDefaultConfiguration.kt 참고

특별 요구사항:
- Master DB 커넥션 풀: 50개
- Replica DB 커넥션 풀: 100개
- Connection Timeout: 60초
- 커스텀 Hikari 설정 필요
- SSL 연결 필요

추가로:
- 개발/운영 환경별 설정 분리
- Health Check 엔드포인트 추가
- DataSource 메트릭 노출 설정
```

---

## 🧪 테스트 환경 구성 요청

```
Platform Core의 testcontainers-starter를 사용해서 테스트 환경을 구성해줘.

참고 문서:
- https://github.com/GroomC4/c4ang-platform-core
- TestDataSourceAutoConfiguration.kt
- IntegrationTest 어노테이션 사용법

테스트 요구사항:
- PostgreSQL 15 사용
- Redis 7 사용
- 통합 테스트용 베이스 클래스 생성
- Repository 테스트 설정
- Service 테스트 설정
- 테스트 데이터 초기화 스크립트 적용

테스트 시나리오:
- Master/Replica 라우팅 검증
- Transaction rollback 테스트
- 동시성 테스트
```

---

## 🚨 문제 해결 요청

```
Platform Core 통합 후 문제가 발생했어. 해결 방법을 알려줘.

Platform Core 버전: 1.0.0
참고: https://github.com/GroomC4/c4ang-platform-core/blob/fix/datasource-circular-reference/DATASOURCE_CIRCULAR_REFERENCE_ISSUE.md

발생한 문제:
[에러 메시지 또는 증상 설명]

시도한 해결 방법:
1. [시도 1]
2. [시도 2]

현재 설정:
- application.yml: [관련 설정]
- build.gradle.kts: [의존성 버전]
```

---

## 🎯 마이그레이션 요청

```
기존 DataSource 설정을 Platform Core로 마이그레이션해줘.

Platform Core 정보:
- 저장소: https://github.com/GroomC4/c4ang-platform-core
- 마이그레이션 가이드: DOMAIN_SERVER_INTEGRATION_GUIDE.md#마이그레이션-체크리스트

현재 구조:
- DataSource 설정: [현재 설정 클래스 경로]
- application.yml: [현재 설정]
- 테스트 설정: [현재 TestContainers 설정]

마이그레이션 목표:
- 기존 기능 100% 유지
- Platform Core 자동 구성 활용
- 테스트 코드 간소화
- Master-Replica 패턴 적용

주의사항:
- 운영 중인 서비스라 downtime 최소화 필요
- 기존 테스트 모두 통과해야 함
```

---

## 💡 최적화 요청

```
Platform Core를 사용 중인데, 성능 최적화가 필요해.

현재 설정 참조:
- https://github.com/GroomC4/c4ang-platform-core
- 우리 서비스 설정: [application.yml]

성능 이슈:
- Connection Pool 부족 warning 발생
- Replica 라우팅이 제대로 안 됨
- 트랜잭션 타임아웃 발생

트래픽 패턴:
- 평균 TPS: 1000
- 피크 TPS: 3000
- Read:Write 비율 = 8:2

최적화 요청:
1. HikariCP 설정 튜닝
2. Master/Replica 풀 사이즈 조정
3. Connection 재사용 최적화
4. 모니터링 메트릭 추가
```

---

## 🔄 업데이트 요청

```
Platform Core가 업데이트되었는데, 우리 서비스도 업데이트해줘.

Platform Core 정보:
- 최신 버전: [버전]
- 변경사항: https://github.com/GroomC4/c4ang-platform-core/releases
- Breaking Changes: [있다면 명시]

현재 버전: [현재 사용 중인 버전]

업데이트 요청:
1. 의존성 버전 업데이트
2. Deprecated API 마이그레이션
3. 새로운 기능 활용
4. 테스트 통과 확인

리스크 관리:
- 단계적 업데이트 계획
- 롤백 계획
- 테스트 시나리오
```

---

## 📊 모니터링 설정 요청

```
Platform Core DataSource에 대한 모니터링을 설정해줘.

참고:
- Platform Core 구조: https://github.com/GroomC4/c4ang-platform-core
- DynamicRoutingDataSource 클래스

모니터링 요구사항:
1. Connection Pool 메트릭
   - Active/Idle connections
   - Wait time
   - Connection 생성/종료 수

2. DataSource 라우팅 메트릭
   - Master/Replica 요청 수
   - 라우팅 실패 수

3. 트랜잭션 메트릭
   - 트랜잭션 수/시간
   - Rollback 비율

4. 알림 설정
   - Connection Pool 임계치
   - 응답 시간 임계치
   - 에러율 임계치

출력 형식:
- Prometheus metrics
- Grafana 대시보드
- Spring Boot Actuator endpoints
```

---

## 사용 방법

1. 위 프롬프트 중 적절한 것을 선택
2. `[대괄호]` 부분을 실제 정보로 교체
3. AI 어시스턴트에게 전달
4. Platform Core 저장소 URL을 반드시 포함 (컨텍스트 제공)

## 팁

- 구체적인 파일 경로와 설정을 제공할수록 정확한 답변 가능
- 에러 메시지는 전체를 복사하여 제공
- 현재 사용 중인 버전 정보 명시
- Platform Core GitHub 저장소 링크 포함 필수
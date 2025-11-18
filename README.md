# C4ang Platform Core

공통 인프라 및 테스트 라이브러리 - Spring Boot Starter 패키지

[![GitHub](https://img.shields.io/badge/GitHub-GroomC4%2Fc4ang--platform--core-blue)](https://github.com/GroomC4/c4ang-platform-core)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen)](https://spring.io/projects/spring-boot)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple)](https://kotlinlang.org/)
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)

---

## 개요

**c4ang-platform-core**는 마이크로서비스 아키텍처에서 공통으로 사용되는 인프라와 테스트 환경을 Spring Boot Starter 패키지로 제공합니다.

### 주요 기능

✅ **Primary-Replica 자동 라우팅**
- `@Transactional(readOnly = true)` → Replica DB
- `@Transactional(readOnly = false)` → Primary DB
- 코드 작성 없이 yml 설정만으로 동작

✅ **통합 테스트 자동화**
- PostgreSQL (Primary/Replica) Testcontainers 자동 시작
- Redis Testcontainers 자동 시작
- Kafka Testcontainers 자동 시작
- Schema Registry Testcontainers 자동 시작 (Kafka Avro 직렬화 지원)
- 스키마 파일 자동 로딩
- **JVM 전역 컨테이너 공유** - 모든 테스트가 동일한 컨테이너 사용 (성능 최적화)

✅ **로컬 개발 환경 제공**
- Docker Compose 기반 로컬 인프라
- PostgreSQL Primary/Replica, Redis, Kafka 포함
- 수동 관리로 개발 중 빠른 재시작

✅ **설정 간소화**
- DataSource 설정 코드 불필요
- Testcontainers 설정 코드 불필요
- yml 파일만으로 완전한 설정

---

## 빠른 시작

### 1. 의존성 추가

```kotlin
// build.gradle.kts
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/GroomC4/c4ang-platform-core")
        credentials {
            username = System.getenv("GITHUB_ACTOR")
            password = System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    // ⭐ 중요: 프로덕션과 테스트 환경에서 각각 하나만 사용하세요!

    // 프로덕션용 (선택) - Primary-Replica 라우팅 필요 시
    implementation("com.groom.platform:datasource-starter:1.2.2-RC2")

    // 테스트용 (필수) - Testcontainers 자동 구성
    // ⚠️ datasource-starter와 함께 사용하지 마세요 (순환 참조 발생)
    testImplementation("com.groom.platform:testcontainers-starter:1.2.2-RC2")
}
```

### 2. 테스트 설정

```yaml
# src/test/resources/application-test.yml
testcontainers:
  postgres:
    enabled: true
    replica-enabled: true
    # 스키마 파일 위치 지정 (3가지 방법 지원)
    # 1. classpath 리소스: classpath:db/schema.sql
    # 2. 프로젝트 루트 기준 파일: file:sql/schema.sql
    # 3. 절대 경로 파일: file:/absolute/path/to/schema.sql
    schema-location: classpath:db/schema.sql
  redis:
    enabled: true
  kafka:
    enabled: true
  schema-registry:
    enabled: true
```

### 3. 테스트 작성

```kotlin
@SpringBootTest
class OrderRepositoryTest {

    @Autowired
    private lateinit var orderRepository: OrderRepository

    @Test
    @Transactional(readOnly = false)  // MASTER DB 사용
    fun `주문 생성 테스트`() {
        val order = orderRepository.save(Order(...))
        assert(order.id != null)
    }

    @Test
    @Transactional(readOnly = true)  // REPLICA DB 사용
    fun `주문 조회 테스트`() {
        val orders = orderRepository.findAll()
        assert(orders is List)
    }
}
```

**끝!** 컨테이너가 자동으로 시작되고 Primary-Replica 라우팅이 작동합니다.

---

## 프로젝트 구조

```
c4ang-platform-core/
├── datasource-core/                 # ⭐ 공통 DataSource 클래스 (순수 라이브러리)
│   └── src/main/kotlin/com/groom/platform/datasource/
│       ├── DynamicRoutingDataSource.kt      # Primary-Replica 라우팅 로직
│       └── DataSourceType.kt                # MASTER/REPLICA enum
│
├── datasource-starter/              # 프로덕션용 DataSource 라우팅
│   └── src/main/kotlin/com/groom/platform/datasource/autoconfigure/
│       ├── DataSourceAutoConfiguration.kt   # Spring Boot Auto-Configuration
│       └── PlatformDataSourceProperties.kt
│
├── testcontainers-starter/          # 테스트용 Testcontainers 자동화
│   └── src/main/kotlin/com/groom/platform/testcontainers/
│       ├── container/
│       │   └── SharedContainers.kt          # ✨ JVM 전역 싱글톤 컨테이너
│       ├── autoconfigure/
│       │   ├── TestcontainersAutoConfiguration.kt
│       │   ├── TestDataSourceAutoConfiguration.kt
│       │   └── TestcontainersProperties.kt
│       ├── annotation/
│       │   └── IntegrationTest.kt
│       └── initializer/
│           └── TestContainerContextInitializer.kt
│
└── local-dev/                       # 로컬 개발 환경 (수동)
    ├── README.md                    # 로컬 환경 가이드
    ├── docker-compose.local.yml     # 전체 인프라 실행
    ├── docker/                      # PostgreSQL 초기화 스크립트
    ├── postgres/                    # PostgreSQL 개별 실행
    ├── kafka/                       # Kafka 개별 실행
    └── base/                        # Redis 개별 실행
```

### datasource-core

**목적:** DataSource 라우팅 핵심 클래스 제공 (AutoConfiguration 없음)

**제공 클래스:**
- `DynamicRoutingDataSource`: @Transactional(readOnly) 기반 자동 라우팅
- `DataSourceType`: MASTER/REPLICA enum

**사용 대상:** datasource-starter와 testcontainers-starter의 공통 의존성
**특징:** Spring Boot AutoConfiguration 없음, 순수 클래스만 포함

### datasource-starter

**목적:** 프로덕션 환경에서 Primary-Replica 패턴 지원

**제공 기능:**
- `DataSourceAutoConfiguration`: Spring Boot Auto-Configuration
- datasource-core를 통한 DynamicRoutingDataSource 제공

**의존성:** datasource-core (api)
**사용 대상:** 모든 마이크로서비스 (프로덕션)

### testcontainers-starter

**목적:** 통합 테스트를 위한 Testcontainers 자동 구성

**제공 기능:**
- PostgreSQL Primary/Replica 자동 시작
- Redis 자동 시작
- Kafka 자동 시작
- Schema Registry 자동 시작 (Kafka Avro 직렬화 지원)
- DataSource 자동 구성 (TestDataSourceAutoConfiguration)
- application-test.yml 기반 설정

**의존성:** datasource-core (api) - ⚠️ datasource-starter 제외 (순환 참조 방지)
**사용 대상:** 모든 마이크로서비스 (테스트)

---

## 문서

### 👥 사용자별 가이드

#### 서비스 개발자
- **[서비스 통합 가이드](documents/guides/SERVICE_INTEGRATION_GUIDE.md)**
  - 프로젝트 설정
  - 테스트 작성 방법
  - 트러블슈팅

- **[로컬 개발 환경 가이드](local-dev/README.md)**
  - Docker Compose로 로컬 인프라 실행
  - PostgreSQL Primary/Replica, Redis, Kafka 설정
  - 서비스 접속 정보
  - Kafka 관리 명령어

#### 유지보수 담당자
- **[유지보수 가이드](documents/guides/MAINTAINER_GUIDE.md)**
  - 프로젝트 구조
  - 빌드 및 테스트
  - GitHub Packages 배포
  - 버전 관리

### 📐 아키텍처 문서

- **[공유 라이브러리 설계](documents/architecture/SHARED_LIBRARY_DESIGN.md)**
  - 초기 설계 기획안
  - 요구사항 분석
  - 설계 방향

- **[완전한 중앙화 제안](documents/architecture/COMPLETE_CENTRALIZATION_PROPOSAL.md)**
  - Primary-Replica 라우팅 포함
  - Spring Boot Starter 패턴
  - 구현 상세

- **[Primary-Replica 비교](documents/architecture/PRIMARY_REPLICA_COMPARISON.md)**
  - 패턴별 비교 분석
  - 중앙화 수준 비교
  - 코드량 비교

### 🔬 조사 및 분석

- **[실현 가능성 분석](documents/research/FEASIBILITY_ANALYSIS.md)**
  - 초기 기획안 검토
  - 기술적 제약사항
  - 대안 설계

- **[Testcontainer 패턴 연구](documents/research/TESTCONTAINER_PATTERNS.md)**
  - 업계 표준 패턴
  - 패턴별 장단점
  - 적용 사례

### 🚀 배포

- **[Starter 빌드 및 배포](documents/deployment/STARTER_BUILD_DEPLOY_GUIDE.md)**
  - Gradle 설정
  - GitHub Packages 배포
  - CI/CD 설정

- **[Kubernetes/Helm 마이그레이션](documents/deployment/K8S_HELM_MIGRATION_GUIDE.md)**
  - K8s 마이그레이션 계획
  - Helm Chart 구성
  - 단계별 가이드

---

## 기술 스택

- **Language:** Kotlin 2.0.21
- **Framework:** Spring Boot 3.3.4
- **Build Tool:** Gradle 8.10.2
- **JDK:** Java 21
- **Test:** JUnit 5, Testcontainers 1.19.3
- **Database:** PostgreSQL 17
- **Cache:** Redis 7
- **Message Queue:** Kafka 7.5.1

---

## 요구사항

### 개발 환경
- JDK 21
- Docker Desktop (Testcontainers용)
- Gradle 8.x (gradlew 사용 권장)

### 프로덕션 환경
- Spring Boot 3.3.4+
- PostgreSQL 17+ (Primary-Replica 구성)
- Redis 7+ (선택)
- Kafka 7.5+ (선택)

---

## 버그 리포트나 기능 제안

**@hayden-han**

---

## 변경 이력

### 1.0.0-SNAPSHOT (현재)
- ✨ Spring Boot Starter 패키지 구현
- ✨ Primary-Replica 자동 라우팅
- ✨ Testcontainers 자동 구성 (JVM 전역 컨테이너 공유)
- ✨ yml 기반 설정
- ✨ 로컬 개발 환경 Docker Compose 제공
- 🔧 SharedContainers 싱글톤 패턴으로 테스트 성능 최적화
- 📦 프로젝트 구조 재구성 (datasource-starter, testcontainers-starter, local-dev)
- 📝 문서 작성

---

**Happy Coding! 🚀**

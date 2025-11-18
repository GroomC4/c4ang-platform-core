# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0-SNAPSHOT] - 2024-11-17

### Added
- 🎉 Initial release of Spring Boot Starter packages
- ✨ `datasource-starter`: Primary-Replica 자동 라우팅
  - `DynamicRoutingDataSource`: @Transactional(readOnly) 기반 자동 라우팅
  - `DataSourceAutoConfiguration`: Spring Boot Auto-Configuration
  - `PlatformDataSourceProperties`: YML 설정 바인딩
- ✨ `testcontainers-starter`: 통합 테스트 자동화
  - `TestcontainersAutoConfiguration`: PostgreSQL, Redis, Kafka 컨테이너 자동 시작
  - `TestDataSourceAutoConfiguration`: 테스트용 DataSource 자동 구성
  - `TestcontainersProperties`: YML 기반 컨테이너 설정
  - 스키마 파일 자동 로딩 지원 (schemaLocation 프로퍼티)
- 📝 포괄적인 문서 작성
  - `documents/guides/MAINTAINER_GUIDE.md`: 유지보수 담당자 가이드
  - `documents/guides/SERVICE_INTEGRATION_GUIDE.md`: 서비스 통합 가이드
  - `documents/architecture/`: 아키텍처 설계 문서
  - `documents/research/`: 기술 조사 문서
  - `documents/deployment/`: 배포 관련 문서
- 🔧 GitHub Actions CI/CD 워크플로우
  - `ci.yml`: PR 및 메인 브랜치 빌드/테스트
  - `publish-starters.yml`: GitHub Packages 자동 배포
- 🛠 Gradle 멀티 모듈 프로젝트 구조
  - Gradle Wrapper 8.10.2
  - Kotlin 2.0.21
  - Spring Boot 3.3.4

### Changed
- 🔄 Kotlin 버전 업그레이드: 1.9.20 → 2.0.21 (Gradle 9.x 호환성)
- 🔄 kotlinOptions → compilerOptions DSL 마이그레이션 (Kotlin 2.x)
- 🔄 루트 프로젝트 빌드 설정 최적화 (apply false 패턴)

### Fixed
- 🐛 Gradle wrapper 생성 오류 해결 (버전 호환성 문제)
- 🐛 멀티 모듈 프로젝트 bootJar 오류 해결 (라이브러리 모드)
- 🐛 kotlin-logging-jvm 의존성 오타 수정

### Technical Details

#### Build Configuration
- Root project: Plugins declared with `apply false`
- Subprojects: Plain JAR generation (bootJar disabled)
- Maven publishing configured for GitHub Packages

#### Dependencies
- Spring Boot 3.3.4
- Kotlin 2.0.21
- Testcontainers 1.19.3
- PostgreSQL Driver (compileOnly)
- HikariCP (connection pooling)
- kotlin-logging-jvm 7.0.0

#### Auto-Configuration
- `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- `@ConditionalOnClass`, `@ConditionalOnProperty` 사용
- `@EnableConfigurationProperties` for YML binding

---

## [1.2.2-RC10] - 2024-11-18

### Added
- 📝 **멀티 모듈 프로젝트 설정 가이드 추가**
  - IntegrationTestBase 패턴 문서화
  - IntelliJ 모듈 루트 설정 불필요한 방법 제공
  - @SpringBootTest properties로 명시적 설정 방법

### Improved
- 📚 SERVICE_INTEGRATION_GUIDE.md 개선
  - 멀티 모듈 Gradle 프로젝트 지원 문서 추가
  - 코드 기반 설정 예시 추가
  - 프로젝트 구조별 가이드 강화

### Why
- 멀티 모듈 프로젝트에서 모듈 루트 설정 없이도 작동하도록
- IntelliJ 설정과 무관하게 일관된 테스트 환경 제공
- 팀 협업 시 설정 불일치 문제 해결

---

## [1.2.2-RC9] - 2024-11-18

### Changed
- 🔄 **PostgreSQL 단일 컨테이너 모드로 변경** (주요 변경사항)
  - Primary-Replica 복제 구조 → 단일 PostgreSQL 컨테이너로 간소화
  - Primary와 Replica DataSource 모두 동일한 컨테이너 참조
  - 라우팅 로직(@Transactional readOnly) 정상 작동 유지

### Removed
- ❌ `postgresReplicaContainer` 제거 (독립 컨테이너)
- ❌ Streaming Replication 설정 제거 (복잡도 감소)

### Improved
- ⚡ 테스트 실행 속도 향상 (컨테이너 1개만 시작)
- 🔧 설정 간소화 및 유지보수성 향상
- 📝 명확한 동작 방식 문서화

### Why
- Testcontainers에서 실제 Streaming Replication 구현은 기술적으로 복잡
- 대부분의 통합 테스트는 라우팅 로직 검증만으로 충분
- 단일 컨테이너로도 @Transactional(readOnly) 동작 완전히 테스트 가능

### Future
- v2.0에서 실제 Streaming Replication 지원 예정 (옵션)

---

## [1.2.2-RC8] - 2024-11-18

### Added
- ✨ Kafka 토픽 자동 생성 활성화 (기본값)
  - `KAFKA_AUTO_CREATE_TOPICS_ENABLE=true`
  - `KAFKA_NUM_PARTITIONS=1` (기본 파티션 수)
  - `KAFKA_DEFAULT_REPLICATION_FACTOR=1` (단일 브로커)

- ✨ Kafka 사전 정의 토픽 지원
  - `testcontainers.kafka.auto-create-topics`: 자동 생성 활성화 여부 (기본값: true)
  - `testcontainers.kafka.topics`: 사전 정의 토픽 목록 (partitions, replication-factor, config 설정 가능)
  - Kafka AdminClient로 컨테이너 시작 시 토픽 자동 생성

### Changed
- 🔧 KafkaProperties 확장: `autoCreateTopics`, `topics` 필드 추가
- 🔧 KafkaTopicConfig 추가: 토픽별 상세 설정 지원

### Fixed
- 🐛 Kafka Producer TimeoutException 해결
  - 토픽 미생성으로 인한 60초 timeout 문제 수정
  - Producer가 존재하지 않는 토픽에 메시지 발행 시 자동 생성
  - 명시적 토픽 정의로 운영 환경과 동일한 설정 테스트 가능

## [1.2.2-RC7] - 2024-11-18

### Added
- 🔍 디버깅 로그 추가 (스키마 로딩 진단용)

### Issues
- ⚠️ 모듈 루트 설정 이슈로 AutoConfiguration 미실행 (사용자 환경 문제)

## [1.2.2-RC6] - 2024-11-18

### Added
- ✨ `project:` 프로토콜 추가로 프로젝트 루트 기준 스키마 파일 로딩 (권장)
  - `project:store-api/sql/schema.sql` - 명확한 의도 표현

### Changed
- 🔧 `file:` 프로토콜은 절대 경로 전용으로 변경
  - `file:/absolute/path/to/schema.sql`

### Summary
- `project:` - 프로젝트 루트 기준 상대 경로 (권장!)
- `file:` - 파일 시스템 절대 경로
- `classpath:` - classpath 리소스

## [1.2.2-RC5] - 2024-11-18

### Added
- ✨ `file:` 프로토콜 지원으로 호스트 파일 시스템의 스키마 파일 로딩 가능

### Changed
- 🔧 스키마 로딩 로직 개선: MountableFile 사용

### Issues
- ⚠️ 상대 경로 혼란 → RC6에서 `project:` 프로토콜로 해결

## [1.2.2-RC4] - 2024-11-18

### Fixed
- 🐛 @Profile("test") 제거로 test profile 미활성화 시에도 DataSource Bean 생성되도록 수정
- ✨ testImplementation 의존성만으로 자동 작동 (profile 설정 불필요)

## [1.2.2-RC3] - 2024-11-18

### Changed
- 🔧 Schema Registry를 Kafka와 동일 네트워크로 이동

## [1.2.2-RC2] - 2024-11-18

### Added
- ✨ `datasource-core`: 공통 DataSource 클래스 모듈 신규 생성
  - DynamicRoutingDataSource, DataSourceType 분리

### Fixed
- 🐛 DataSource Bean 순환 참조 문제 해결
  - testcontainers-starter가 datasource-starter 대신 datasource-core 의존
  - AutoConfiguration 충돌 완전 제거

## [1.2.2-RC1] - 2024-11-18

### Added
- ✨ Schema Registry Testcontainer 지원 추가
  - Kafka Avro 직렬화/역직렬화 자동 구성
  - testcontainers.schema-registry.enabled 설정 지원

### Changed
- 🔧 Git tag 기반 버전 관리로 변경 (v1.2.0 → 1.2.0)

### Fixed
- 🐛 @Profile("!test") 추가로 순환 참조 시도 (미해결)

---

## [Unreleased]

### Planned
- [ ] Kubernetes/Helm Chart 마이그레이션
- [ ] 추가 데이터베이스 지원 (MySQL, MariaDB)
- [ ] 성능 모니터링 메트릭 추가
- [ ] Spring Boot 3.4.x 업그레이드 대응

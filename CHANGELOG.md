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

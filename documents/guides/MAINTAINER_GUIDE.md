# Platform Core 유지보수 가이드

이 문서는 **c4ang-platform-core 프로젝트의 유지보수 담당자**를 위한 가이드입니다.

## 목차

1. [프로젝트 개요](#프로젝트-개요)
2. [로컬 개발 환경 설정](#로컬-개발-환경-설정)
3. [빌드 및 테스트](#빌드-및-테스트)
4. [GitHub Packages 배포](#github-packages-배포)
5. [버전 관리](#버전-관리)
6. [트러블슈팅](#트러블슈팅)

---

## 프로젝트 개요

### 프로젝트 구조

```
c4ang-platform-core/
├── datasource-starter/                            # 프로덕션용 DataSource Starter
│   ├── src/main/kotlin/
│   │   └── com/groom/platform/datasource/
│   │       ├── DynamicRoutingDataSource.kt       # Primary-Replica 라우팅
│   │       └── autoconfigure/
│   │           ├── DataSourceAutoConfiguration.kt
│   │           └── PlatformDataSourceProperties.kt
│   └── build.gradle.kts
│
├── testcontainers-starter/                        # 테스트용 Testcontainers Starter
│   ├── src/main/kotlin/
│   │   └── com/groom/platform/testcontainers/
│   │       ├── container/
│   │       │   └── SharedContainers.kt           # JVM 전역 싱글톤 컨테이너
│   │       ├── autoconfigure/
│   │       │   ├── TestcontainersAutoConfiguration.kt
│   │       │   ├── TestDataSourceAutoConfiguration.kt
│   │       │   └── TestcontainersProperties.kt
│   │       ├── annotation/
│   │       │   └── IntegrationTest.kt
│   │       └── initializer/
│   │           └── TestContainerContextInitializer.kt
│   └── build.gradle.kts
│
├── local-dev/                                     # 로컬 개발 환경 (수동)
│   ├── README.md                                  # 로컬 환경 가이드
│   ├── docker-compose.local.yml                   # 전체 인프라 실행
│   ├── docker/                                    # PostgreSQL 초기화 스크립트
│   ├── postgres/                                  # PostgreSQL 개별 실행
│   ├── kafka/                                     # Kafka 개별 실행
│   └── base/                                      # Redis 개별 실행
│
├── documents/                                     # 프로젝트 문서
│   ├── guides/                                    # 사용자 가이드
│   ├── architecture/                              # 아키텍처 문서
│   ├── research/                                  # 조사 및 분석
│   └── deployment/                                # 배포 가이드
│
├── settings.gradle.kts                            # 멀티 모듈 설정
├── build.gradle.kts                               # 루트 빌드 설정
└── README.md                                      # 프로젝트 개요
```

### 패키지 설명

#### 1. datasource-starter

**목적:** 프로덕션 환경에서 Primary-Replica 패턴을 위한 DataSource 자동 설정

**제공 기능:**
- `DynamicRoutingDataSource`: @Transactional(readOnly) 기반 자동 라우팅
- `DataSourceType`: MASTER/REPLICA enum
- `DataSourceAutoConfiguration`: Spring Boot Auto-Configuration

**사용 대상:** 모든 마이크로서비스 (프로덕션 환경)

#### 2. testcontainers-starter

**목적:** 통합 테스트를 위한 Testcontainers 자동 설정

**제공 기능:**
- PostgreSQL Primary/Replica 컨테이너 자동 시작
- Redis 컨테이너 자동 시작
- Kafka 컨테이너 자동 시작
- Primary-Replica DataSource 자동 구성
- **JVM 전역 컨테이너 공유** (SharedContainers 싱글톤 패턴)
- application-test.yml 기반 설정

**사용 대상:** 모든 마이크로서비스 (테스트 환경)

#### 3. local-dev

**목적:** 로컬 개발 환경을 위한 Docker Compose 설정

**제공 기능:**
- PostgreSQL Primary/Replica 수동 실행
- Redis 수동 실행
- Kafka (KRaft 모드) 수동 실행
- 개별 서비스 선택적 실행 가능

**사용 대상:** 서비스 개발자 (로컬 개발)

---

## 로컬 개발 환경 설정

### 1. 필수 요구사항

- **Java:** JDK 21
- **Kotlin:** 2.0.21 (Gradle이 자동 설치)
- **Gradle:** 8.10.2 (프로젝트에 포함된 gradlew 사용)
- **Docker:** Docker Desktop 또는 Docker Engine (Testcontainers용)
- **Git:** 버전 관리

### 2. 프로젝트 클론

```bash
cd /Users/groom/IdeaProjects
git clone https://github.com/GroomC4/c4ang-platform-core.git
cd c4ang-platform-core
```

### 3. GitHub Token 설정 (필수)

패키지 배포를 위해 GitHub Personal Access Token이 필요합니다.

#### 3.1. Token 생성

1. GitHub 접속: https://github.com/settings/tokens
2. **Generate new token (classic)** 클릭
3. 설정:
   - **Note:** `Platform Core Package Token`
   - **Expiration:** `90 days` (또는 Custom)
   - **Select scopes:**
     - ✅ `write:packages` (패키지 업로드)
     - ✅ `read:packages` (패키지 다운로드)
     - ✅ `repo` (Private repository인 경우 필수)
4. **Generate token** 클릭
5. 생성된 토큰 복사: `ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx`

⚠️ **중요:** 토큰은 한 번만 표시되므로 안전한 곳에 보관하세요!

#### 3.2. 환경 변수 설정

**macOS/Linux:**

```bash
# ~/.zshrc 또는 ~/.bashrc에 추가
export GITHUB_ACTOR="your-github-username"
export GITHUB_TOKEN="ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"

# 적용
source ~/.zshrc
```

**Windows (PowerShell):**

```powershell
# 시스템 환경 변수 설정
setx GITHUB_ACTOR "your-github-username"
setx GITHUB_TOKEN "ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"

# 재시작 후 확인
echo $env:GITHUB_ACTOR
echo $env:GITHUB_TOKEN
```

#### 3.3. 환경 변수 확인

```bash
echo $GITHUB_ACTOR
echo $GITHUB_TOKEN
```

출력이 없으면 설정이 안 된 것입니다.

---

## 빌드 및 테스트

### 1. 의존성 다운로드

```bash
./gradlew dependencies
```

### 2. 전체 빌드

```bash
# Clean + Build
./gradlew clean build

# 성공 메시지:
# BUILD SUCCESSFUL in 30s
```

### 3. 개별 모듈 빌드

```bash
# DataSource Starter만
./gradlew :datasource-starter:build

# Testcontainers Starter만
./gradlew :testcontainers-starter:build
```

### 4. JAR 파일 확인

```bash
ls -la datasource-starter/build/libs/
ls -la testcontainers-starter/build/libs/

# 출력 예시:
# datasource-starter-1.0.0-SNAPSHOT.jar
# testcontainers-starter-1.0.0-SNAPSHOT.jar
```

### 5. 로컬 테스트 (Maven Local)

다른 서비스에서 사용하기 전에 로컬 테스트:

```bash
# Maven Local (~/.m2/repository)에 설치
./gradlew publishToMavenLocal

# 확인
ls -la ~/.m2/repository/com/groom/platform/datasource-starter/1.0.0-SNAPSHOT/
ls -la ~/.m2/repository/com/groom/platform/testcontainers-starter/1.0.0-SNAPSHOT/
```

---

## GitHub Packages 배포

### 1. 배포 전 체크리스트

- [ ] 코드 변경사항 커밋 완료
- [ ] 로컬 빌드 성공 (`./gradlew clean build`)
- [ ] GITHUB_ACTOR 환경 변수 설정 확인
- [ ] GITHUB_TOKEN 환경 변수 설정 확인
- [ ] 버전 확인 (build.gradle.kts)

### 2. 배포 명령어

```bash
# GitHub Packages에 배포
./gradlew publish

# 성공 메시지:
# > Task :datasource-starter:publishMavenPublicationToGitHubPackagesRepository
# > Task :testcontainers-starter:publishMavenPublicationToGitHubPackagesRepository
# BUILD SUCCESSFUL in 15s
```

### 3. 배포 확인

#### 3.1. GitHub UI에서 확인

브라우저에서 접속:
```
https://github.com/GroomC4/c4ang-platform-core/packages
```

확인 사항:
- ✅ `com.groom.platform:datasource-starter` 패키지 존재
- ✅ `com.groom.platform:testcontainers-starter` 패키지 존재
- ✅ 버전 확인 (예: 1.0.0-SNAPSHOT)
- ✅ 최근 배포 시간 확인

#### 3.2. 다른 프로젝트에서 다운로드 테스트

```bash
# 테스트 프로젝트에서
./gradlew build --refresh-dependencies

# 성공하면 정상 배포된 것
```

### 4. CI/CD 자동 배포 (GitHub Actions)

#### 4.1. 워크플로우 파일 생성

`.github/workflows/publish.yml` 파일을 생성합니다:

```yaml
name: Publish Packages

on:
  push:
    branches:
      - main
    tags:
      - 'v*'  # v1.0.0, v1.0.1 등
  workflow_dispatch:  # 수동 실행

jobs:
  publish:
    runs-on: ubuntu-latest

    permissions:
      contents: read
      packages: write

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Grant execute permission
        run: chmod +x gradlew

      - name: Build with Gradle
        run: ./gradlew clean build

      - name: Publish to GitHub Packages
        run: ./gradlew publish
        env:
          GITHUB_ACTOR: ${{ github.actor }}
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}

      - name: Upload artifacts
        uses: actions/upload-artifact@v4
        with:
          name: packages
          path: |
            datasource-starter/build/libs/*.jar
            testcontainers-starter/build/libs/*.jar
```

#### 4.2. 자동 배포 트리거

**옵션 1: main 브랜치에 Push**
```bash
git add .
git commit -m "feat: Add new feature"
git push origin main

# GitHub Actions가 자동으로 빌드 및 배포
```

**옵션 2: 버전 태그 생성**
```bash
git tag -a v1.0.0 -m "Release version 1.0.0"
git push origin v1.0.0

# GitHub Actions가 자동으로 빌드 및 배포
```

**옵션 3: 수동 실행**
1. GitHub 레포지토리 접속
2. **Actions** 탭 클릭
3. **Publish Packages** 워크플로우 선택
4. **Run workflow** 버튼 클릭

#### 4.3. 배포 상태 확인

```
https://github.com/GroomC4/c4ang-platform-core/actions
```

- ✅ 녹색 체크: 성공
- ❌ 빨간 X: 실패 (로그 확인)

---

## 버전 관리

### 1. Semantic Versioning 사용

```
MAJOR.MINOR.PATCH-SNAPSHOT

예시:
1.0.0-SNAPSHOT   (개발 중)
1.0.0            (릴리즈)
1.0.1            (버그 수정)
1.1.0            (새 기능)
2.0.0            (Breaking Change)
```

### 2. 버전 변경 방법

#### 2.1. build.gradle.kts 수정

`build.gradle.kts` 파일의 `version` 속성 변경:

```kotlin
// build.gradle.kts (루트)
allprojects {
    group = "com.groom.platform"
    version = "1.1.0-SNAPSHOT"  // 여기만 변경!
}
```

#### 2.2. 변경사항 커밋

```bash
git add build.gradle.kts
git commit -m "chore: Bump version to 1.1.0-SNAPSHOT"
git push origin main
```

### 3. 릴리즈 프로세스

#### 3.1. SNAPSHOT 제거

```kotlin
// SNAPSHOT 제거
version = "1.0.0"  // -SNAPSHOT 제거
```

#### 3.2. 빌드 및 배포

```bash
./gradlew clean build
./gradlew publish
```

#### 3.3. Git 태그 생성

```bash
git tag -a v1.0.0 -m "Release version 1.0.0"
git push origin v1.0.0
```

#### 3.4. 다음 개발 버전으로 변경

```kotlin
// 다음 버전으로 변경
version = "1.1.0-SNAPSHOT"
```

```bash
git add build.gradle.kts
git commit -m "chore: Prepare for next development iteration"
git push origin main
```

### 4. 버전별 사용법

**서비스 프로젝트에서:**

```kotlin
dependencies {
    // SNAPSHOT 버전 (개발 중)
    testImplementation("com.groom.platform:testcontainers-starter:1.0.0-SNAPSHOT")

    // 릴리즈 버전 (안정)
    testImplementation("com.groom.platform:testcontainers-starter:1.0.0")
}
```

---

## 트러블슈팅

### 문제 1: "Could not publish to repository" 에러

**증상:**
```
FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':datasource-starter:publishMavenPublicationToGitHubPackagesRepository'.
> Failed to publish publication 'maven' to repository 'GitHubPackages'
  > Could not PUT 'https://maven.pkg.github.com/...'. Received status code 401 from server: Unauthorized
```

**원인:**
- GitHub Token이 설정되지 않았거나 만료됨
- Token 권한이 부족함

**해결:**

1. 환경 변수 확인:
   ```bash
   echo $GITHUB_ACTOR
   echo $GITHUB_TOKEN
   ```

2. Token 재생성:
   - https://github.com/settings/tokens
   - `write:packages`, `read:packages`, `repo` 권한 확인

3. 환경 변수 재설정:
   ```bash
   export GITHUB_TOKEN="새로운_토큰"
   source ~/.zshrc
   ```

### 문제 2: "Artifact already exists" 에러

**증상:**
```
Failed to publish: Artifact already exists
```

**원인:**
- 동일한 버전을 재배포하려고 시도
- 릴리즈 버전(SNAPSHOT 아님)은 덮어쓰기 불가

**해결:**

**옵션 1: SNAPSHOT 버전 사용 (권장)**
```kotlin
// SNAPSHOT은 덮어쓰기 가능
version = "1.0.0-SNAPSHOT"
```

**옵션 2: 새 버전 사용**
```kotlin
// 새 버전으로 변경
version = "1.0.1"
```

### 문제 3: "Could not resolve dependency" 에러 (서비스 프로젝트)

**증상:**
```
Could not resolve com.groom.platform:testcontainers-starter:1.0.0-SNAPSHOT.
```

**원인:**
- 서비스 프로젝트에서 패키지를 찾을 수 없음
- GitHub Token 미설정
- Gradle 캐시 문제

**해결:**

1. 서비스 프로젝트의 환경 변수 확인:
   ```bash
   echo $GITHUB_ACTOR
   echo $GITHUB_TOKEN
   ```

2. Gradle 캐시 삭제:
   ```bash
   ./gradlew build --refresh-dependencies
   ```

3. 완전히 새로 받기:
   ```bash
   rm -rf ~/.gradle/caches
   ./gradlew build
   ```

### 문제 4: 빌드 실패 (컴파일 에러)

**증상:**
```
Compilation error: ...
```

**해결:**

1. Gradle 캐시 삭제:
   ```bash
   ./gradlew clean
   ```

2. 전체 빌드:
   ```bash
   ./gradlew clean build
   ```

3. IDE 재시작 (IntelliJ IDEA):
   - File → Invalidate Caches / Restart

### 문제 5: Docker 관련 에러 (Testcontainers)

**증상:**
```
Could not start container
```

**원인:**
- Docker가 실행되지 않음

**해결:**

1. Docker 실행 확인:
   ```bash
   docker ps
   ```

2. Docker Desktop 시작:
   - macOS: Docker Desktop 앱 실행
   - Linux: `sudo systemctl start docker`

---

## 연락처

문제가 해결되지 않으면:
- **GitHub Issues:** https://github.com/GroomC4/c4ang-platform-core/issues
- **팀 채널:** Slack #platform-core

---

## 참고 자료

- [Spring Boot Auto-Configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.developing-auto-configuration)
- [GitHub Packages Maven](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry)
- [Testcontainers](https://www.testcontainers.org/)
- [Gradle Multi-Module Projects](https://docs.gradle.org/current/userguide/multi_project_builds.html)

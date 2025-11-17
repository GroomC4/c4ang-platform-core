# Spring Boot Starter 빌드 및 배포 가이드

## 목표

platform-core를 Spring Boot Starter로 패키징하여 GitHub Packages에 배포하고, 다른 서비스에서 의존성으로 사용

---

## 배포 옵션 비교

| 방법 | 장점 | 단점 | 추천도 |
|------|------|------|--------|
| **GitHub Packages** | ✅ GitHub 통합<br>✅ Private 가능<br>✅ 무료 | ⚠️ GitHub Token 필요 | ⭐⭐⭐⭐⭐ |
| **Maven Local** | ✅ 설정 간단<br>✅ 로컬 테스트 | ❌ 팀 공유 불가<br>❌ CI/CD 불가 | ⭐⭐ (개발용) |
| **Maven Central** | ✅ 공개 배포<br>✅ 전 세계 공유 | ❌ 복잡한 승인 절차<br>❌ 도메인 필요 | ⭐ (오픈소스) |
| **Private Nexus** | ✅ 회사 내부 관리<br>✅ 세밀한 권한 | ❌ 서버 운영 필요<br>❌ 비용 | ⭐⭐⭐⭐ (엔터프라이즈) |

**추천: GitHub Packages** (현재 프로젝트에 최적)

---

## Step 1: Starter 패키지 구조 설계

### 2개의 Starter 패키지 생성

```
c4ang-platform-core/
├── datasource-starter/     # 프로덕션용
│   ├── build.gradle.kts
│   └── src/main/
│       ├── kotlin/
│       │   └── com/groom/platform/datasource/
│       │       ├── DynamicRoutingDataSource.kt
│       │       └── DataSourceType.kt
│       └── resources/
│           └── META-INF/
│               └── spring.factories  (Spring Boot 2.x)
│               └── spring/
│                   └── org.springframework.boot.autoconfigure.AutoConfiguration.imports  (Spring Boot 3.x)
│
├── testcontainers-starter/  # 테스트용
│   ├── build.gradle.kts
│   └── src/main/
│       ├── kotlin/
│       │   └── com/groom/platform/testcontainers/
│       │       ├── autoconfigure/
│       │       │   ├── TestcontainersAutoConfiguration.kt
│       │       │   ├── TestcontainersProperties.kt
│       │       │   └── TestDataSourceAutoConfiguration.kt
│       │       └── containers/
│       │           ├── PostgresTestContainer.kt
│       │           ├── RedisTestContainer.kt
│       │           └── KafkaTestContainer.kt
│       └── resources/
│           └── META-INF/spring/
│               └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
│
├── settings.gradle.kts                           # 멀티 모듈 설정
└── build.gradle.kts                              # 루트 빌드 설정
```

---

## Step 2: Gradle 빌드 설정

### settings.gradle.kts (루트)

```kotlin
rootProject.name = "c4ang-platform-core"

include("datasource-starter")
include("testcontainers-starter")
```

### build.gradle.kts (루트)

```kotlin
plugins {
    kotlin("jvm") version "2.0.21" apply false
    kotlin("plugin.spring") version "2.0.21" apply false
    id("org.springframework.boot") version "3.3.4" apply false
    id("io.spring.dependency-management") version "1.1.6" apply false
    `maven-publish` apply false
}

allprojects {
    group = "com.groom.platform"
    version = "1.0.0-SNAPSHOT"  // 버전 관리

    repositories {
        mavenCentral()
        maven {
            url = uri("https://packages.confluent.io/maven/")
        }
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.spring")
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "maven-publish")

    dependencies {
        // 공통 의존성
        val implementation by configurations
        implementation("org.jetbrains.kotlin:kotlin-reflect")
        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    }

    // Java 21 타겟
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs = listOf("-Xjsr305=strict")
            jvmTarget = "21"
        }
    }
}
```

### datasource-starter/build.gradle.kts

```kotlin
plugins {
    `maven-publish`
}

dependencies {
    // Spring Boot
    api("org.springframework.boot:spring-boot-starter-data-jpa:3.3.4")
    api("org.springframework.boot:spring-boot-starter-jdbc:3.3.4")

    // HikariCP (이미 spring-boot-starter-jdbc에 포함되지만 명시)
    api("com.zaxxer:HikariCP")

    // Optional dependencies (사용자가 선택)
    compileOnly("org.postgresql:postgresql")
}

// Maven 배포 설정
publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.groom.platform"
            artifactId = "datasource-starter"
            version = project.version.toString()

            from(components["java"])

            pom {
                name.set("Platform DataSource Spring Boot Starter")
                description.set("Dynamic routing datasource for primary-replica pattern")
                url.set("https://github.com/GroomC4/c4ang-platform-core")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }

                developers {
                    developer {
                        id.set("groom")
                        name.set("Groom Team")
                        email.set("dev@groom.com")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/GroomC4/c4ang-platform-core.git")
                    developerConnection.set("scm:git:ssh://github.com/GroomC4/c4ang-platform-core.git")
                    url.set("https://github.com/GroomC4/c4ang-platform-core")
                }
            }
        }
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/GroomC4/c4ang-platform-core")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: project.findProperty("gpr.user") as String?
                password = System.getenv("GITHUB_TOKEN") ?: project.findProperty("gpr.token") as String?
            }
        }
    }
}
```

### testcontainers-starter/build.gradle.kts

```kotlin
plugins {
    `maven-publish`
}

dependencies {
    // Platform DataSource Starter 의존
    api(project(":datasource-starter"))

    // Spring Boot
    api("org.springframework.boot:spring-boot-starter-data-jpa:3.3.4")
    api("org.springframework.boot:spring-boot-starter-data-redis:3.3.4")

    // Testcontainers
    api("org.testcontainers:testcontainers:1.19.3")
    api("org.testcontainers:postgresql:1.19.3")
    api("org.testcontainers:kafka:1.19.3")
    api("org.testcontainers:junit-jupiter:1.19.3")

    // Configuration Processor (yml 자동완성)
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
}

// Maven 배포 설정 (동일)
publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.groom.platform"
            artifactId = "testcontainers-starter"
            version = project.version.toString()

            from(components["java"])

            pom {
                name.set("Platform Testcontainers Spring Boot Starter")
                description.set("Testcontainers auto-configuration for integration tests")
                url.set("https://github.com/GroomC4/c4ang-platform-core")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }

                developers {
                    developer {
                        id.set("groom")
                        name.set("Groom Team")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/GroomC4/c4ang-platform-core.git")
                    developerConnection.set("scm:git:ssh://github.com/GroomC4/c4ang-platform-core.git")
                    url.set("https://github.com/GroomC4/c4ang-platform-core")
                }
            }
        }
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/GroomC4/c4ang-platform-core")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: project.findProperty("gpr.user") as String?
                password = System.getenv("GITHUB_TOKEN") ?: project.findProperty("gpr.token") as String?
            }
        }
    }
}
```

---

## Step 3: GitHub Token 생성

### 1. GitHub Settings 접속
```
https://github.com/settings/tokens
또는
GitHub 프로필 → Settings → Developer settings → Personal access tokens → Tokens (classic)
```

### 2. Generate new token (classic) 클릭

### 3. 권한 설정
```
Note: Platform Core Package Token

Expiration: 90 days (또는 Custom으로 1년)

Select scopes:
  ✅ write:packages  (패키지 업로드)
  ✅ read:packages   (패키지 다운로드)
  ✅ delete:packages (패키지 삭제 - 선택)
  ✅ repo            (Private repo인 경우 필수)
```

### 4. Generate token 클릭 후 토큰 복사
```
ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

⚠️ **주의: 토큰을 안전하게 보관하세요! 다시 볼 수 없습니다.**

---

## Step 4: 로컬 환경 설정

### Option 1: 환경 변수 설정 (추천)

**macOS/Linux:**
```bash
# ~/.zshrc 또는 ~/.bashrc에 추가
export GITHUB_ACTOR="your-github-username"
export GITHUB_TOKEN="ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"

# 적용
source ~/.zshrc
```

**Windows:**
```powershell
# 시스템 환경 변수 설정
setx GITHUB_ACTOR "your-github-username"
setx GITHUB_TOKEN "ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
```

### Option 2: gradle.properties 파일 (개발용)

```bash
# ~/.gradle/gradle.properties (전역 설정)
gpr.user=your-github-username
gpr.token=ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

⚠️ **주의: gradle.properties를 Git에 커밋하지 마세요!**

---

## Step 5: 빌드 및 배포

### 로컬에서 빌드 테스트

```bash
cd /Users/groom/IdeaProjects/c4ang-platform-core

# 1. 의존성 다운로드
./gradlew dependencies

# 2. 컴파일
./gradlew clean build

# 3. JAR 확인
ls -la datasource-starter/build/libs/
ls -la testcontainers-starter/build/libs/

# 출력 예시:
# datasource-starter-1.0.0-SNAPSHOT.jar
# testcontainers-starter-1.0.0-SNAPSHOT.jar
```

### Maven Local에 배포 (로컬 테스트용)

```bash
# Maven Local (~/.m2/repository)에 설치
./gradlew publishToMavenLocal

# 확인
ls -la ~/.m2/repository/com/groom/platform/datasource-starter/1.0.0-SNAPSHOT/
```

### GitHub Packages에 배포

```bash
# GitHub Packages에 업로드
./gradlew publish

# 성공 메시지:
# > Task :datasource-starter:publishMavenPublicationToGitHubPackagesRepository
# > Task :testcontainers-starter:publishMavenPublicationToGitHubPackagesRepository
# BUILD SUCCESSFUL
```

### GitHub에서 확인

```
https://github.com/GroomC4/c4ang-platform-core/packages
```

패키지 목록:
- `com.groom.platform:datasource-starter`
- `com.groom.platform:testcontainers-starter`

---

## Step 6: 다른 서비스에서 사용

### build.gradle.kts (서비스 프로젝트)

```kotlin
repositories {
    mavenCentral()

    // GitHub Packages 추가
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/GroomC4/c4ang-platform-core")
        credentials {
            username = System.getenv("GITHUB_ACTOR") ?: project.findProperty("gpr.user") as String?
            password = System.getenv("GITHUB_TOKEN") ?: project.findProperty("gpr.token") as String?
        }
    }
}

dependencies {
    // 프로덕션용 (src/main)
    implementation("com.groom.platform:datasource-starter:1.0.0-SNAPSHOT")

    // 테스트용 (src/test)
    testImplementation("com.groom.platform:testcontainers-starter:1.0.0-SNAPSHOT")
}
```

### 환경 변수 설정 (각 팀원)

각 개발자가 자신의 환경에 GitHub Token 설정:

```bash
# ~/.zshrc
export GITHUB_ACTOR="your-github-username"
export GITHUB_TOKEN="ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
```

---

## Step 7: CI/CD 설정 (GitHub Actions)

### .github/workflows/publish.yml (platform-core)

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
      packages: write  # GitHub Packages 쓰기 권한

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Grant execute permission for gradlew
        run: chmod +x gradlew

      - name: Build with Gradle
        run: ./gradlew build

      - name: Publish to GitHub Packages
        run: ./gradlew publish
        env:
          GITHUB_ACTOR: ${{ github.actor }}
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}  # 자동 제공되는 토큰

      - name: Upload build artifacts
        uses: actions/upload-artifact@v4
        with:
          name: packages
          path: |
            datasource-starter/build/libs/*.jar
            testcontainers-starter/build/libs/*.jar
```

### .github/workflows/test.yml (서비스 프로젝트)

```yaml
name: Run Tests

on:
  pull_request:
  push:
    branches: [main]

jobs:
  test:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Run tests
        run: ./gradlew test
        env:
          GITHUB_ACTOR: ${{ github.actor }}
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}  # platform-core 패키지 다운로드용
```

---

## Step 8: 버전 관리

### Semantic Versioning 사용

```
1.0.0-SNAPSHOT   (개발 중)
1.0.0            (릴리즈)
1.0.1            (버그 수정)
1.1.0            (새 기능)
2.0.0            (Breaking Change)
```

### 버전 업데이트 방법

**1. build.gradle.kts (루트) 수정**
```kotlin
allprojects {
    version = "1.1.0-SNAPSHOT"  // 버전 변경
}
```

**2. Git Tag 생성 (릴리즈 시)**
```bash
git tag -a v1.0.0 -m "Release version 1.0.0"
git push origin v1.0.0
```

**3. GitHub Actions 자동 배포**
- Tag push 시 자동으로 빌드 및 배포

### 서비스에서 버전 사용

```kotlin
dependencies {
    // SNAPSHOT 버전 (개발 중)
    testImplementation("com.groom.platform:testcontainers-starter:1.0.0-SNAPSHOT")

    // 릴리즈 버전 (안정)
    testImplementation("com.groom.platform:testcontainers-starter:1.0.0")
}
```

---

## Step 9: 패키지 삭제 (필요 시)

### GitHub UI에서 삭제

```
1. https://github.com/GroomC4/c4ang-platform-core/packages 접속
2. 패키지 선택
3. Package settings
4. Delete this package
```

### Gradle로 삭제 (권한 필요)

GitHub Packages는 Gradle로 직접 삭제 불가. UI 사용 필요.

---

## 트러블슈팅

### 1. "Could not find artifact" 에러

**원인:** GitHub Token 미설정 또는 만료

**해결:**
```bash
# 환경 변수 확인
echo $GITHUB_ACTOR
echo $GITHUB_TOKEN

# 토큰 재생성
https://github.com/settings/tokens
```

### 2. "401 Unauthorized" 에러

**원인:** Token 권한 부족

**해결:**
- Token에 `write:packages`, `read:packages` 권한 추가
- Private repo인 경우 `repo` 권한도 필요

### 3. "Could not resolve dependency" (SNAPSHOT 버전)

**원인:** Gradle이 SNAPSHOT을 캐싱

**해결:**
```bash
# 캐시 삭제
./gradlew build --refresh-dependencies

# 또는 Gradle 캐시 완전 삭제
rm -rf ~/.gradle/caches
```

### 4. "Artifact already exists" 에러

**원인:** 동일 버전 재배포 불가 (릴리즈 버전)

**해결:**
```kotlin
// SNAPSHOT 버전은 덮어쓰기 가능
version = "1.0.0-SNAPSHOT"

// 릴리즈 버전은 새 버전 사용
version = "1.0.1"
```

### 5. 로컬 테스트 시 변경사항 반영 안됨

**원인:** Maven Local 캐시

**해결:**
```bash
# Maven Local 재설치
./gradlew clean publishToMavenLocal

# 서비스 프로젝트에서 캐시 삭제
./gradlew build --refresh-dependencies
```

---

## 대안: Private Repository 사용 시

### Nexus Repository Manager (추천)

**장점:**
- 세밀한 권한 관리
- SNAPSHOT/Release 분리
- Docker Registry도 지원

**단점:**
- 서버 운영 필요 (AWS EC2, Docker)
- 비용 발생

**설정 예시:**
```kotlin
repositories {
    maven {
        url = uri("https://nexus.yourcompany.com/repository/maven-releases/")
        credentials {
            username = System.getenv("NEXUS_USERNAME")
            password = System.getenv("NEXUS_PASSWORD")
        }
    }
}
```

---

## 요약

### 빌드 및 배포 프로세스

```bash
# 1. 로컬 개발
./gradlew build
./gradlew publishToMavenLocal

# 2. GitHub Packages 배포
./gradlew publish

# 3. CI/CD (자동)
git push origin main  # 또는 git push origin v1.0.0
```

### 서비스에서 사용

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
    testImplementation("com.groom.platform:testcontainers-starter:1.0.0-SNAPSHOT")
}
```

### 필수 작업 체크리스트

- [ ] GitHub Token 생성 (write:packages 권한)
- [ ] 환경 변수 설정 (GITHUB_ACTOR, GITHUB_TOKEN)
- [ ] build.gradle.kts 작성 (루트 + 서브모듈)
- [ ] 로컬 빌드 테스트 (./gradlew build)
- [ ] Maven Local 배포 테스트 (./gradlew publishToMavenLocal)
- [ ] GitHub Packages 배포 (./gradlew publish)
- [ ] 서비스 프로젝트에서 의존성 테스트
- [ ] GitHub Actions 워크플로우 설정
- [ ] 팀원들에게 Token 설정 가이드 공유

---

## 다음 단계

1. **로컬 테스트부터 시작**
   ```bash
   ./gradlew publishToMavenLocal
   ```

2. **서비스에서 사용 테스트**
   ```kotlin
   repositories { mavenLocal() }
   dependencies {
       testImplementation("com.groom.platform:testcontainers-starter:1.0.0-SNAPSHOT")
   }
   ```

3. **GitHub Packages 배포**
   ```bash
   ./gradlew publish
   ```

4. **CI/CD 자동화**
   - GitHub Actions 워크플로우 추가

더 궁금한 부분이 있으면 알려주세요!

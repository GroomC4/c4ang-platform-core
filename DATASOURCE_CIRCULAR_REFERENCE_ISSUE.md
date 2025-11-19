# DataSource 라우팅 구조 순환 참조 문제 보고서

## 1. 현재 구조의 문제점

### 구조도
```
Platform Core (datasource-starter)
├── DataSourceAutoConfiguration
│   ├── routingDataSource(@Bean)
│   │   └── 파라미터: masterDataSource, replicaDataSource 필요
│   └── dataSource(@Bean, @Primary)
│       └── 파라미터: routingDataSource 필요

Customer Service
├── DataSourceConfig
│   ├── masterDataSource(@Bean)
│   └── replicaDataSource(@Bean)
```

### 순환 참조 발생 경로
```
1. Spring이 dataSource 빈 생성 시도
2. → routingDataSource 빈 필요
3. → routingDataSource 생성 시 masterDataSource, replicaDataSource 파라미터 주입
4. → Spring이 DataSource 타입 빈 검색
5. → dataSource 빈 발견 (DataSource 타입)
6. → 다시 1번으로 (순환 참조!)
```

### 실제 에러 메시지
```
***************************
APPLICATION FAILED TO START
***************************

Description:

The dependencies of some of the beans in the application context form a cycle:

   entityManagerFactory defined in class path resource [com/groom/customer/configuration/jpa/JpaConfig.class]
      ↓
   dataSourceScriptDatabaseInitializer defined in class path resource [org/springframework/boot/autoconfigure/sql/init/DataSourceInitializationConfiguration.class]
┌─────┐
|  dataSource defined in class path resource [com/groom/platform/datasource/autoconfigure/DataSourceAutoConfiguration.class]
↑     ↓
|  routingDataSource defined in class path resource [com/groom/platform/datasource/autoconfigure/DataSourceAutoConfiguration.class]
└─────┘
```

## 2. 시도한 해결 방법들과 실패 이유

| 시도 | 방법 | 코드 | 결과 | 실패 이유 |
|------|------|------|------|-----------|
| 1 | `@Lazy` 어노테이션 추가 | `@Lazy fun dataSource()` | ❌ 실패 | 빈 생성 시점만 지연, 순환 구조는 그대로 |
| 2 | `@DependsOn` 으로 순서 제어 | `@DependsOn("masterDataSource", "replicaDataSource")` | ❌ 실패 | 의존성 순서와 순환 참조는 별개 문제 |
| 3 | ApplicationContext.getBean() 사용 | `applicationContext.getBean("routingDataSource")` | ❌ 실패 | 같은 컨텍스트 내에서 순환 참조 감지 |
| 4 | LazyConnectionDataSourceProxy 래핑 | `LazyConnectionDataSourceProxy(routingDataSource)` | ❌ 실패 | 프록시도 결국 실제 빈 필요 |
| 5 | spring.main.allow-circular-references=true | YAML 설정 추가 | ❌ 실패 | 구조적 순환 참조는 허용 불가 |
| 6 | 빈 이름 별칭 사용 | `@Bean(name = ["dataSource", "routingDataSource"])` | ❌ 실패 | Spring이 별도 빈으로 인식 |
| 7 | 메서드 이름 변경 | `fun dataSource()` vs `fun routingDataSource()` | ❌ 실패 | 메서드명과 무관하게 순환 발생 |

## 3. 근본 원인

### Spring Boot의 빈 주입 메커니즘
- Spring은 **타입 기반**으로 빈을 찾음
- 모든 DataSource 타입 빈이 후보가 됨
- `@Qualifier`를 사용해도 **순환 참조 체크는 타입 레벨**에서 발생

### 현재 구조의 코드 (Platform Core)
```kotlin
// 문제가 되는 현재 구조
@AutoConfiguration(before = [SpringDataSourceAutoConfiguration::class])
@ConditionalOnClass(DataSource::class)
@EnableConfigurationProperties(PlatformDataSourceProperties::class)
@Profile("!test")
class DataSourceAutoConfiguration {

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Bean
    @DependsOn("masterDataSource", "replicaDataSource")
    fun routingDataSource(
        @Qualifier("masterDataSource") masterDataSource: DataSource,  // 파라미터 주입
        @Qualifier("replicaDataSource") replicaDataSource: DataSource
    ): DataSource {
        val router = DynamicRoutingDataSource()
        router.setTargetDataSources(
            mapOf(
                DataSourceType.MASTER to masterDataSource,
                DataSourceType.REPLICA to replicaDataSource,
            ),
        )
        router.setDefaultTargetDataSource(masterDataSource)
        router.afterPropertiesSet()
        return router
    }

    @Primary
    @Bean
    @DependsOn("routingDataSource")
    fun dataSource(): DataSource {
        val routingDataSource = applicationContext.getBean("routingDataSource", DataSource::class.java)
        return LazyConnectionDataSourceProxy(routingDataSource)  // 여기서 순환!
    }
}
```

### 구조적 한계
1. **타입 충돌**: 모든 빈이 DataSource 타입
2. **의존성 혼재**: 파라미터 주입과 ApplicationContext 조회 혼용
3. **Spring 컨테이너 제약**: 같은 컨테이너 내에서 순환 참조 불가피

## 4. 제안하는 해결 방안

### 방안 1: 서비스 레벨에서 완전 제어 (권장) ⭐

Platform Core는 유틸리티 클래스만 제공하고, 빈 구성은 서비스가 담당

#### Platform Core (수정안)
```kotlin
// datasource-core 모듈 - 유틸리티 클래스만 제공
class DynamicRoutingDataSource : AbstractRoutingDataSource() {
    override fun determineCurrentLookupKey(): DataSourceType {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            val isReadOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly()
            return DataSourceType.isReadOnlyTransaction(isReadOnly)
        }
        return DataSourceType.MASTER
    }
}

// AutoConfiguration 제거 또는 최소화
```

#### Customer Service (사용 예시)
```kotlin
@Configuration
class DataSourceConfig {

    @Bean
    @ConfigurationProperties("spring.datasource.master")
    fun masterDataSourceProperties(): DataSourceProperties = DataSourceProperties()

    @Bean
    fun masterDataSource(): DataSource =
        masterDataSourceProperties()
            .initializeDataSourceBuilder()
            .type(HikariDataSource::class.java)
            .build()

    @Bean
    @ConfigurationProperties("spring.datasource.replica")
    fun replicaDataSourceProperties(): DataSourceProperties = DataSourceProperties()

    @Bean
    fun replicaDataSource(): DataSource =
        replicaDataSourceProperties()
            .initializeDataSourceBuilder()
            .type(HikariDataSource::class.java)
            .build()

    @Bean
    fun routingDataSource(): DataSource {
        val router = DynamicRoutingDataSource()
        router.setTargetDataSources(
            mapOf(
                DataSourceType.MASTER to masterDataSource(),
                DataSourceType.REPLICA to replicaDataSource()
            )
        )
        router.setDefaultTargetDataSource(masterDataSource())
        router.afterPropertiesSet()
        return router
    }

    @Primary
    @Bean
    fun dataSource(): DataSource =
        LazyConnectionDataSourceProxy(routingDataSource())
}
```

### 방안 2: BeanPostProcessor 사용

빈이 완전히 생성된 후 후처리로 라우팅 설정

```kotlin
// Platform Core
@Component
class RoutingDataSourceConfigurer : BeanPostProcessor, ApplicationContextAware {

    private lateinit var applicationContext: ApplicationContext

    override fun setApplicationContext(applicationContext: ApplicationContext) {
        this.applicationContext = applicationContext
    }

    override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
        if (bean is DynamicRoutingDataSource && !bean.isConfigured()) {
            configureRoutingDataSource(bean)
        }
        return bean
    }

    private fun configureRoutingDataSource(routingDataSource: DynamicRoutingDataSource) {
        val master = applicationContext.getBean("masterDataSource", DataSource::class.java)
        val replica = try {
            applicationContext.getBean("replicaDataSource", DataSource::class.java)
        } catch (e: NoSuchBeanDefinitionException) {
            master
        }

        routingDataSource.setTargetDataSources(
            mapOf(
                DataSourceType.MASTER to master,
                DataSourceType.REPLICA to replica
            )
        )
        routingDataSource.setDefaultTargetDataSource(master)
        routingDataSource.afterPropertiesSet()
    }
}
```

### 방안 3: Configuration Properties 방식

YAML 설정으로 DataSource 생성을 완전 분리

```kotlin
// Platform Core
@ConfigurationProperties(prefix = "platform.datasource.routing")
data class RoutingDataSourceProperties(
    var masterBeanName: String = "masterDataSource",
    var replicaBeanName: String = "replicaDataSource"
)

@AutoConfiguration
@EnableConfigurationProperties(RoutingDataSourceProperties::class)
class DataSourceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    fun routingDataSourceFactory(
        properties: RoutingDataSourceProperties,
        applicationContext: ApplicationContext
    ): RoutingDataSourceFactory {
        return RoutingDataSourceFactory(properties, applicationContext)
    }
}

// Factory pattern으로 늦은 초기화
class RoutingDataSourceFactory(
    private val properties: RoutingDataSourceProperties,
    private val applicationContext: ApplicationContext
) {
    fun createRoutingDataSource(): DataSource {
        // 실제 사용 시점에 빈 조회
        val master = applicationContext.getBean(properties.masterBeanName, DataSource::class.java)
        val replica = applicationContext.getBean(properties.replicaBeanName, DataSource::class.java)

        return DynamicRoutingDataSource().apply {
            setTargetDataSources(
                mapOf(
                    DataSourceType.MASTER to master,
                    DataSourceType.REPLICA to replica
                )
            )
            setDefaultTargetDataSource(master)
            afterPropertiesSet()
        }
    }
}
```

## 5. 다른 프로젝트 사례

### Spring Cloud Netflix
- 서비스 디스커버리를 위한 추상화 제공
- 실제 구현은 서비스에서 수행

### Spring Boot DataSource AutoConfiguration
- 단일 DataSource만 자동 구성
- 멀티 DataSource는 수동 구성 권장

### Baeldung의 Spring Data Source Routing
- 서비스 레벨에서 전체 구성
- Starter는 가이드만 제공

## 6. 결론 및 권장사항

### 문제의 핵심
- ❌ Starter에서 라우팅 DataSource와 Primary DataSource를 모두 제공하려는 시도
- ❌ 파라미터 주입과 ApplicationContext 조회를 혼용
- ❌ 같은 타입(DataSource)의 여러 빈 간 상호 의존

### 권장사항
1. **즉시 조치**:
   - 서비스에서 직접 DataSource 구성하도록 가이드 변경
   - 현재 datasource-starter의 AutoConfiguration 비활성화

2. **장기 개선**:
   - Platform Core는 DynamicRoutingDataSource 클래스와 설정 가이드만 제공
   - AutoConfiguration은 최소화하거나 제거

3. **대안 검토**:
   - Spring Cloud의 `@RefreshScope` 같은 동적 구성 방식
   - AOP 기반 DataSource 전환

## 7. 임시 해결책 (즉시 적용 가능)

Customer Service에서 Platform Core의 AutoConfiguration을 제외하고 직접 구성:

```kotlin
// CustomerApiApplication.kt
@SpringBootApplication(
    exclude = [DataSourceAutoConfiguration::class] // Platform Core의 자동 구성 제외
)
class CustomerApiApplication

// DataSourceConfig.kt
@Configuration
class DataSourceConfig {

    @Bean
    @ConfigurationProperties("spring.datasource.master")
    fun masterDataSourceProperties(): DataSourceProperties = DataSourceProperties()

    @Bean
    fun masterDataSource(): DataSource =
        masterDataSourceProperties()
            .initializeDataSourceBuilder()
            .type(HikariDataSource::class.java)
            .build()

    @Bean
    @ConfigurationProperties("spring.datasource.replica")
    fun replicaDataSourceProperties(): DataSourceProperties = DataSourceProperties()

    @Bean
    fun replicaDataSource(): DataSource =
        replicaDataSourceProperties()
            .initializeDataSourceBuilder()
            .type(HikariDataSource::class.java)
            .build()

    @Primary
    @Bean
    fun dataSource(): DataSource {
        val router = DynamicRoutingDataSource() // Platform Core의 클래스 사용
        router.setTargetDataSources(
            mapOf(
                DataSourceType.MASTER to masterDataSource(),
                DataSourceType.REPLICA to replicaDataSource()
            )
        )
        router.setDefaultTargetDataSource(masterDataSource())
        router.afterPropertiesSet()
        return LazyConnectionDataSourceProxy(router)
    }
}
```

## 8. 참고 자료

- [Spring Boot DataSource Configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/howto.html#howto.data-access.configure-custom-datasource)
- [Baeldung - Spring JPA Multiple Databases](https://www.baeldung.com/spring-data-jpa-multiple-databases)
- [Spring Framework - AbstractRoutingDataSource](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/jdbc/datasource/lookup/AbstractRoutingDataSource.html)

---

작성일: 2025-11-19
작성자: Claude (Anthropic)
검토 필요: Platform Core 팀
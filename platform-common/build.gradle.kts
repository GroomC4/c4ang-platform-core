dependencies {
    // Spring JDBC (AbstractRoutingDataSource)
    api("org.springframework:spring-jdbc:6.1.13")

    // Spring TX (TransactionSynchronizationManager)
    api("org.springframework:spring-tx:6.1.13")
}

// platform-common은 내부 전용 모듈이므로 publishing 설정 없음

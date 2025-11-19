# Local Dev Starter

Spring Boot Starter for automatic local development environment setup with Docker Compose.

## Features

- 🚀 **Zero-Configuration**: Automatically starts Docker Compose when running with `local` profile
- 🔄 **Lifecycle Management**: Automatically stops containers on application shutdown
- 🏥 **Health Checks**: Waits for all services to be healthy before starting the application
- 🔌 **DataSource Integration**: Seamlessly integrates with `datasource-starter`
- 📦 **Service Support**: PostgreSQL (Primary/Replica), Redis, Kafka (KRaft mode)

## Quick Start

### 1. Add Dependency

Add to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.groom.platform:local-dev-starter:1.0.0")
    implementation("com.groom.platform:datasource-starter:1.2.2-RC3")
}
```

### 2. Run with Local Profile

```bash
# Using Gradle
./gradlew bootRun -Dspring.profiles.active=local

# Or create a Gradle task
./gradlew localRun
```

### 3. That's It!

The following services will automatically start:
- PostgreSQL Primary (port 15432)
- PostgreSQL Replica (port 15433)
- Redis (port 6379)
- Kafka with KRaft (port 9092)

## Configuration

### Default Configuration

The starter works out-of-the-box with sensible defaults:

```yaml
platform:
  local-dev:
    enabled: true              # Enable local dev environment
    auto-start: true          # Auto-start Docker Compose
    auto-stop: true           # Auto-stop on shutdown
    wait-for-healthy: true    # Wait for health checks
    health-check-timeout: 60s # Timeout for health checks
    project-name: c4ang-local-dev

    services:
      postgres:
        enabled: true
        primary-port: 15432
        replica-port: 15433
        database: groom
        username: application
        password: application

      redis:
        enabled: true
        port: 6379

      kafka:
        enabled: true
        port: 9092
        use-kraft: true
```

### Custom Configuration

Create `application-local.yml`:

```yaml
platform:
  local-dev:
    services:
      postgres:
        primary-port: 25432  # Custom port
        database: mydb       # Custom database name
      redis:
        port: 26379         # Custom Redis port
```

## Integration with DataSource Starter

This starter automatically configures DataSource beans that are compatible with `datasource-starter`:

```kotlin
// No configuration needed! These are automatically provided:
@Bean fun masterDataSource(): DataSource
@Bean fun replicaDataSource(): DataSource
```

The DataSource URLs are automatically configured based on local-dev settings:
- Master: `jdbc:postgresql://localhost:15432/groom`
- Replica: `jdbc:postgresql://localhost:15433/groom`

## Requirements

- Docker Desktop or Docker Engine
- Docker Compose v2.x
- Java 21+
- Spring Boot 3.3+

## Troubleshooting

### Docker Not Found

If you see "Docker is not installed or not running":

1. Install Docker Desktop from https://www.docker.com/get-started
2. Ensure Docker is running
3. Or disable auto-start: `platform.local-dev.auto-start=false`

### Port Conflicts

If ports are already in use, customize them:

```yaml
platform:
  local-dev:
    services:
      postgres:
        primary-port: 35432  # Alternative port
```

### Health Check Timeout

If services take longer to start:

```yaml
platform:
  local-dev:
    health-check-timeout: 120s  # Increase timeout
```

### Disable for Tests

For test environments, disable auto-start:

```yaml
# application-test.yml
platform:
  local-dev:
    enabled: false
```

## Manual Control

You can also control the services manually:

```kotlin
@Component
class MyComponent(
    private val dockerComposeManager: DockerComposeManager
) {

    fun startServices() {
        dockerComposeManager.start()
        dockerComposeManager.waitForHealthy(Duration.ofSeconds(60))
    }

    fun stopServices() {
        dockerComposeManager.stop()
    }
}
```

## Service Details

### PostgreSQL
- **Primary**: Write operations, port 15432
- **Replica**: Read operations, port 15433
- **Version**: PostgreSQL 15 Alpine
- **Health Check**: `pg_isready`

### Redis
- **Port**: 6379
- **Version**: Redis 7 Alpine
- **Persistence**: AOF enabled
- **Health Check**: `redis-cli ping`

### Kafka
- **Port**: 9092
- **Mode**: KRaft (no Zookeeper)
- **Version**: Confluent Kafka 7.5.0
- **Health Check**: `kafka-broker-api-versions`

## Development Tips

### View Logs

```bash
# View all logs
docker compose -p c4ang-local-dev logs

# Follow logs
docker compose -p c4ang-local-dev logs -f

# Specific service
docker compose -p c4ang-local-dev logs postgres-primary
```

### Manual Management

```bash
# Stop services
docker compose -p c4ang-local-dev down

# Stop and remove volumes
docker compose -p c4ang-local-dev down -v

# View status
docker compose -p c4ang-local-dev ps
```

### Custom Docker Compose

To use a custom Docker Compose file:

```yaml
platform:
  local-dev:
    docker-compose-file: file:/path/to/custom-compose.yml
```

## Contributing

Please follow the project's contribution guidelines when submitting pull requests.

## License

Apache License 2.0
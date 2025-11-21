# Wikimedia Kafka Streaming Application

A real-time event streaming application that consumes Wikimedia recent changes via Server-Sent Events (SSE), publishes to Kafka, and persists to MongoDB with comprehensive observability.

## Architecture

```
┌─────────────────┐     ┌─────────────┐     ┌─────────────────┐     ┌─────────────┐
│ Wikimedia SSE   │ --> │ Producer    │ --> │ Kafka Topic     │ --> │ Consumer    │
│ Stream          │     │ (9090)      │     │ wikimedia_      │     │ (9091)      │
└─────────────────┘     └─────────────┘     │ recent_change   │     └──────┬──────┘
                                            └─────────────────┘            │
                                                                           v
                                            ┌─────────────────┐     ┌─────────────┐
                                            │ MongoDB         │ <-- │ Batch       │
                                            │ - events        │     │ Writer      │
                                            │ - failed_events │     └──────┬──────┘
                                            └─────────────────┘            │
                                                  ^                        │ (on failure)
                                                  │                        v
                                            ┌─────────────┐     ┌─────────────────┐
                                            │ DLQ         │ <-- │ Kafka DLQ       │
                                            │ Consumer    │     │ Topic           │
                                            └─────────────┘     └─────────────────┘
```

## Features

- **Real-time streaming** from Wikimedia recent changes
- **Kafka message processing** with consumer groups
- **MongoDB persistence** with async batch processing
- **Dead Letter Queue** (dual: Kafka + MongoDB)
- **SSE & WebSocket** endpoints for live dashboards
- **Complete observability stack** (Prometheus, Grafana, Loki, Tempo, Zipkin)
- **Alerting** with Alertmanager
- **Distributed tracing** with OpenTelemetry

## Tech Stack

- Java 25
- Spring Boot 4.0.0
- Apache Kafka
- MongoDB
- WebFlux (reactive SSE client)
- Micrometer + Prometheus
- OpenTelemetry
- Grafana + Loki + Tempo + Zipkin

## Project Structure

```
springboot-kafka-app/
├── kafka-producer-wikimedia/     # SSE consumer & Kafka producer
├── kafka-consumer-database/      # Kafka consumer & MongoDB persistence
├── infra/docker/                 # Docker Compose & configs
│   ├── prometheus/
│   ├── grafana/
│   ├── loki/
│   ├── tempo/
│   ├── alertmanager/
│   └── otel/
└── docs/
    ├── architecture.md
    ├── observability-guide.md
    └── tasks.md
```

## Quick Start

### Prerequisites

- Java 25
- Docker & Docker Compose
- Maven
- [Task](https://taskfile.dev/) (optional, for convenience commands)

### Using Taskfile (Recommended)

```bash
# Install Task: https://taskfile.dev/installation/

# Start everything
task start

# Or step by step:
task infra:up      # Start infrastructure
task build         # Build project
task run:producer  # Run producer (Terminal 1)
task run:consumer  # Run consumer (Terminal 2)

# View all available commands
task --list
```

### Manual Setup

### 1. Start Infrastructure

```bash
# Start all services (Kafka, MongoDB, Observability stack)
docker-compose -f infra/docker/docker-compose.yml up -d

# Verify services are running
docker-compose -f infra/docker/docker-compose.yml ps
```

### 2. Build the Application

```bash
./mvnw clean install
```

### 3. Run the Applications

**Terminal 1 - Producer:**
```bash
./mvnw spring-boot:run -pl kafka-producer-wikimedia
```

**Terminal 2 - Consumer:**
```bash
./mvnw spring-boot:run -pl kafka-consumer-database
```

### 4. Access the Dashboard

- **Live Dashboard**: http://localhost:9091
- **SSE Stream**: http://localhost:9091/wikimedia/stream
- **WebSocket**: ws://localhost:9091/ws/wikimedia

## Service Ports

| Service | Port | URL |
|---------|------|-----|
| Producer | 9090 | http://localhost:9090 |
| Consumer | 9091 | http://localhost:9091 |
| Kafka | 9092 | localhost:9092 |
| Kafka UI | 8080 | http://localhost:8080 |
| MongoDB | 27017 | localhost:27017 |
| Mongo Express | 8082 | http://localhost:8082 |
| Prometheus | 9093 | http://localhost:9093 |
| Grafana | 3000 | http://localhost:3000 |
| Alertmanager | 9094 | http://localhost:9094 |
| Loki | 3100 | http://localhost:3100 |
| Tempo | 3200 | http://localhost:3200 |
| Zipkin | 9411 | http://localhost:9411 |

## API Endpoints

### Consumer (9091)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/` | GET | Live dashboard |
| `/wikimedia/stream` | GET | SSE event stream |
| `/ws/wikimedia` | WS | WebSocket stream |
| `/api/events/recent` | GET | Last 50 events from MongoDB |
| `/api/events/stats` | GET | Processing statistics |
| `/actuator/health` | GET | Health check |
| `/actuator/prometheus` | GET | Prometheus metrics |

### Producer (9090)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/actuator/health` | GET | Health check |
| `/actuator/prometheus` | GET | Prometheus metrics |

## Monitoring & Observability

### Grafana Dashboards

Access Grafana at http://localhost:3000 (admin/admin)

Pre-configured dashboard includes:
- Event production/consumption rates
- Queue size monitoring
- DLQ event tracking
- JVM heap usage
- HTTP latency (p99)

### Prometheus Metrics

Custom metrics exposed:
- `wikimedia_events_produced_total` - Events sent to Kafka
- `wikimedia_events_persisted_total` - Events saved to MongoDB
- `wikimedia_events_dlq_total` - Events sent to DLQ
- `wikimedia_events_queue_size` - Current queue size
- `wikimedia_stream_reconnects_total` - SSE reconnection attempts

### Example PromQL Queries

```promql
# Events per second
rate(wikimedia_events_produced_total[5m])

# Queue size
wikimedia_events_queue_size

# DLQ events in last 5 minutes
increase(wikimedia_events_dlq_total[5m])

# JVM heap usage
jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"} * 100
```

### Distributed Tracing

View traces in:
- **Zipkin**: http://localhost:9411
- **Tempo** via Grafana Explore

Logs include trace context:
```
2025-11-20 10:30:45.123 [main] INFO app.js.kafka - traceId=abc123 spanId=xyz789 - Event message received
```

### Pre-configured Alerts

| Alert | Severity | Condition |
|-------|----------|-----------|
| HighEventQueueSize | warning | queue > 5000 |
| CriticalEventQueueSize | critical | queue > 8000 |
| DLQEventsIncreasing | warning | DLQ +10 in 5m |
| ApplicationDown | critical | up == 0 |
| HighJVMMemoryUsage | warning | heap > 80% |

## Configuration

### Producer (`kafka-producer-wikimedia/src/main/resources/application.yml`)

```yaml
app:
  kafka:
    topic: wikimedia_recent_change
  wikimedia:
    stream-url: https://stream.wikimedia.org/v2/stream/recentchange
    retry:
      max-attempts: 50
      initial-backoff-seconds: 3
      max-backoff-minutes: 2
```

### Consumer (`kafka-consumer-database/src/main/resources/application.yml`)

```yaml
spring:
  kafka:
    consumer:
      group-id: sbGroup
      auto-offset-reset: earliest

app:
  kafka:
    topic: wikimedia_recent_change
    dlq-topic: wikimedia_recent_change_dlq
  persistence:
    queue-capacity: 10000
    batch-size: 100
    flush-interval-ms: 1000
```

## Development

### Build Commands

```bash
# Build entire project
./mvnw clean install

# Build specific module
./mvnw clean install -pl kafka-producer-wikimedia

# Run tests
./mvnw test

# Run single test
./mvnw test -Dtest=EventPersistenceServiceTest -pl kafka-consumer-database
```

### Testing

Uses Testcontainers for integration tests:

```bash
# Run all tests
./mvnw test

# Run with specific profile
./mvnw test -Dspring.profiles.active=test
```

## Kafka Operations

### View Consumer Lag

```bash
docker exec sb-kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --group sbGroup \
  --describe
```

### Reset Consumer Offset

```bash
docker exec sb-kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --group sbGroup \
  --topic wikimedia_recent_change \
  --reset-offsets --to-earliest --execute
```

### View Topic Messages

```bash
docker exec sb-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic wikimedia_recent_change \
  --from-beginning --max-messages 10
```

## Troubleshooting

### Common Issues

**Consumer not receiving messages:**
1. Check Kafka UI for topic/consumer group
2. Verify consumer group offset
3. Check application logs for errors

**High queue size:**
1. Check MongoDB connectivity
2. Review batch processing metrics
3. Increase batch size or reduce flush interval

**Prometheus not scraping:**
1. Verify actuator endpoints are accessible
2. Check Prometheus targets page
3. Ensure `host.docker.internal` is resolving

### Logs

```bash
# View producer logs
./mvnw spring-boot:run -pl kafka-producer-wikimedia | grep -E "INFO|ERROR"

# View container logs
docker logs -f sb-kafka
docker logs -f sb-mongodb
```

## Taskfile Commands

### Quick Reference

```bash
# Build & Run
task build              # Build entire project
task run:producer       # Run producer
task run:consumer       # Run consumer
task test               # Run all tests

# Infrastructure
task infra:up           # Start all services
task infra:down         # Stop all services
task infra:status       # Show service status

# Kafka Operations
task kafka:topics       # List topics
task kafka:lag          # Show consumer lag
task kafka:reset        # Reset offsets to earliest
task kafka:consume -- wikimedia_recent_change  # Consume messages

# MongoDB
task mongo:shell        # Open MongoDB shell
task mongo:count        # Count events
task mongo:failed       # Count failed events
task mongo:clear        # Clear all events

# Logs
task logs:kafka         # Kafka logs
task logs:mongo         # MongoDB logs
task logs:prometheus    # Prometheus logs
task logs:grafana       # Grafana logs

# SSH into Containers
task ssh:kafka          # SSH into Kafka
task ssh:mongo          # SSH into MongoDB
task ssh:prometheus     # SSH into Prometheus
task ssh:grafana        # SSH into Grafana

# Health & Metrics
task health             # Check all services health
task metrics:producer   # Get producer metrics
task metrics:consumer   # Get consumer metrics
task metrics:queue      # Get queue size

# Troubleshooting
task debug:network      # Show Docker network
task debug:ports        # Show exposed ports
task debug:consumer-lag # Debug consumer lag
task debug:connectivity # Test Prometheus connectivity

# Observability
task obs:up             # Start observability stack
task prometheus:reload  # Reload Prometheus config
task prometheus:targets # Check scrape targets
task grafana:reset      # Reset Grafana

# Cleanup
task clean              # Clean build artifacts
task clean:docker       # Remove containers and volumes
task clean:all          # Clean everything

# Utilities
task urls               # Show all service URLs
task start              # Start everything
task stop               # Stop everything
```

## CI/CD

### GitHub Actions

The project includes GitHub Actions workflows:

- **CI** (`.github/workflows/ci.yml`): Build, test, and code quality checks on every push/PR
- **Release** (`.github/workflows/release.yml`): Create releases and Docker images on tags

### Required Secrets

Configure these in your GitHub repository settings:

- `DOCKERHUB_USERNAME`: Docker Hub username
- `DOCKERHUB_TOKEN`: Docker Hub access token

### Creating a Release

```bash
git tag v1.0.0
git push origin v1.0.0
```

## Documentation

- [Architecture & Kafka Mechanics](docs/architecture.md)
- [Observability Guide](docs/observability-guide.md)

## License

MIT

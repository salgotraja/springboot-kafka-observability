# Architecture & Kafka Mechanics

## System Overview

```
┌─────────────────┐     ┌─────────┐     ┌──────────────────┐     ┌─────────┐
│ Wikimedia SSE   │ --> │ Producer│ --> │ Kafka Topic      │ --> │Consumer │
│ Stream          │     │ (9090)  │     │ wikimedia_recent │     │ (9091)  │
└─────────────────┘     └─────────┘     │ _change          │     └────┬────┘
                                        └──────────────────┘          │
                                                                      v
                                        ┌──────────────────┐     ┌─────────┐
                                        │ MongoDB          │ <-- │ Batch   │
                                        │ - wikimedia_events    │ Writer  │
                                        │ - failed_events  │     └────┬────┘
                                        └──────────────────┘          │
                                              ^                       │ (on failure)
                                              │                       v
                                        ┌─────────┐     ┌──────────────────┐
                                        │ DLQ     │ <-- │ Kafka DLQ Topic  │
                                        │Consumer │     │ wikimedia_recent │
                                        └─────────┘     │ _change_dlq      │
                                                        └──────────────────┘
```

## Kafka Consumer Group & Offset Management

### Why Old Messages Aren't Reprocessed

Kafka uses **Consumer Group Offsets** to track progress:

1. **Consumer Group ID**: Our consumer uses `sbGroup` (configured in `application.yml`)
2. **Offset**: A number indicating the position in the partition
3. **Committed Offset**: After processing, consumer tells Kafka "I've processed up to offset X"

```
Partition 0:  [0] [1] [2] [3] [4] [5] [6] [7] [8] [9]
                              ^
                              |
                    Committed Offset = 4
                    (Consumer resumes from 5)
```

### Offset Storage

- Kafka stores offsets in internal topic `__consumer_offsets`
- Stored per: `(consumer-group, topic, partition)`
- Survives consumer restarts

### Configuration

```yaml
spring.kafka.consumer:
  group-id: sbGroup                    # Consumer group identifier
  auto-offset-reset: earliest          # What to do if no offset exists
  enable-auto-commit: true             # Auto-commit offsets (default)
```

**`auto-offset-reset` options:**
- `earliest`: Start from beginning (only if no committed offset)
- `latest`: Start from newest messages only

### Viewing Offsets in Kafka UI

1. Go to Kafka UI (http://localhost:8080)
2. Navigate to "Consumers" tab
3. Find consumer group `sbGroup`
4. See current offset, lag, and last commit time

## Dead Letter Queue (DLQ)

### Our Implementation: Dual DLQ (Kafka + MongoDB)

Failed events go to both Kafka DLQ topic and MongoDB `failed_events` collection for maximum reliability and queryability.

**When events go to DLQ:**
- MongoDB connection failure
- Document validation errors
- Any exception during batch `repository.saveAll()`

**Flow:**
```
Batch Persistence Failure
    ↓
Send to Kafka DLQ topic (wikimedia_recent_change_dlq)
    ↓
Save to MongoDB failed_events collection
    ↓
DLQ Consumer picks up from Kafka topic
    ↓
Retry persistence to MongoDB
    ↓
On success: Remove from failed_events
On failure: Increment retryCount (max 3)
```

**DLQ Consumer Group:** `sbGroup-dlq`

**MongoDB Document Structure:**
```json
{
  "_id": "...",
  "eventData": "{\"title\": \"...\", ...}",
  "errorMessage": "Connection refused",
  "errorType": "MongoTimeoutException",
  "failedAt": "2024-01-15T10:30:00Z",
  "retryCount": 0
}
```

**Benefits of Dual DLQ:**
- Kafka provides automatic retry via consumer offset management
- MongoDB provides queryability for monitoring and manual intervention
- Events exceeding max retries (3) remain in MongoDB for investigation

## Message Retention & Cleanup

### Kafka Retention

Messages stay in Kafka based on retention policy:

```
# Default: 7 days or 1GB per partition
log.retention.hours=168
log.retention.bytes=1073741824
```

Even after consumer processes messages, they remain in Kafka until retention expires.

### Why Keep Processed Messages?

1. **Replay capability**: New consumer groups start from beginning
2. **Debugging**: Inspect what was sent
3. **Multiple consumers**: Different apps can process same messages

### Clearing Kafka Data

```bash
# Delete topic (loses all messages)
docker exec sb-kafka kafka-topics --delete --topic wikimedia_recent_change --bootstrap-server localhost:9092

# Or reset offsets to reprocess
docker exec sb-kafka kafka-consumer-groups --bootstrap-server localhost:9092 \
  --group sbGroup --topic wikimedia_recent_change --reset-offsets --to-earliest --execute
```

## Consumer Behavior Scenarios

### Scenario 1: Consumer Restarts
- Kafka has offsets for `sbGroup`
- Consumer resumes from last committed offset
- No duplicate processing

### Scenario 2: New Consumer Group
- New group `sbGroup2` has no offsets
- `auto-offset-reset: earliest` → starts from beginning
- `auto-offset-reset: latest` → only new messages

### Scenario 3: Consumer Down, Producer Running
- Messages accumulate in Kafka
- When consumer starts, processes backlog
- "Lag" = unprocessed messages

### Scenario 4: Consumer Crashes Mid-Batch
- Depends on commit strategy
- `auto-commit`: May lose some messages
- `manual commit`: Reprocess from last commit

## Data Flow in Detail

### 1. Producer Flow

```
Wikimedia SSE → WebClient → Filter JSON → KafkaTemplate.send() → Kafka Broker
```

- Async, non-blocking
- Retries on connection failure
- No persistence guarantee until Kafka ACK

### 2. Consumer Flow

```
Kafka Broker → @KafkaListener → BlockingQueue → Batch Worker → MongoDB
                     ↓
              Sinks.Many → SSE/WebSocket Clients
```

- Consumer thread adds to queue (fast)
- Separate worker thread batches and persists (async)
- SSE clients get real-time updates

### 3. Batch Persistence Flow

```
Queue (10,000 capacity)
    ↓
Poll events (100ms timeout)
    ↓
Batch full (100) OR Timer (1s)?
    ↓ Yes
MongoDB saveAll()
    ↓ Failure
Save each to failed_events
```

## Observability & Monitoring

### Prometheus Metrics

Both producer and consumer expose metrics at `/actuator/prometheus`.

**Custom Metrics:**

| Metric | Application | Description |
|--------|-------------|-------------|
| `wikimedia_events_produced_total` | Producer | Total events sent to Kafka |
| `wikimedia_stream_reconnects_total` | Producer | Reconnection attempts to Wikimedia |
| `wikimedia_events_persisted_total` | Consumer | Events persisted to MongoDB |
| `wikimedia_events_dlq_total` | Consumer | Events sent to DLQ |
| `wikimedia_events_queue_size` | Consumer | Current event queue size |

**Built-in Metrics:**
- Kafka producer/consumer metrics
- JVM memory/CPU metrics
- HTTP request latency histograms
- MongoDB operation metrics

### Grafana Dashboards

Access Grafana at http://localhost:3000 (admin/admin).

Pre-configured dashboard "Wikimedia Kafka Application" includes:
- Producer/Consumer message rates
- Event queue size
- DLQ events count
- JVM heap usage
- HTTP request latency (p99)

### OpenTelemetry Tracing

Distributed tracing configured with OTLP exporter to http://localhost:4318.

### Health Checks

```bash
# Consumer health
curl http://localhost:9091/actuator/health

# Producer health
curl http://localhost:9090/actuator/health

# Prometheus metrics
curl http://localhost:9091/actuator/prometheus
```

### Infrastructure Services

| Service | URL | Credentials |
|---------|-----|-------------|
| Kafka UI | http://localhost:8080 | - |
| Mongo Express | http://localhost:8082 | - |
| Prometheus | http://localhost:9093 | - |
| Grafana | http://localhost:3000 | admin/admin |

## Best Practices

### 1. Idempotent Processing
Design consumers to handle duplicate messages safely.

### 2. Monitor Consumer Lag
High lag = consumer can't keep up with producer.

### 3. Appropriate Batch Size
- Too small: High MongoDB overhead
- Too large: Memory pressure, delayed processing

### 4. DLQ Processing
Regularly check and process failed events:
```java
List<FailedEvent> retryable = failedEventRepository.findByRetryCountLessThan(3);
```

### 5. Offset Management
Consider manual commits for critical data:
```java
@KafkaListener(topics = "...", containerFactory = "manualAckFactory")
public void consume(String msg, Acknowledgment ack) {
    process(msg);
    ack.acknowledge(); // Manual commit
}
```

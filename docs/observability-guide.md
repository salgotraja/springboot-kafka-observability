# Observability Guide

This guide covers the complete observability stack for the Wikimedia Kafka application, including metrics, logs, traces, alerting, and dashboards.

## Architecture Overview

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│ Producer/       │     │ OpenTelemetry   │     │ Tempo           │
│ Consumer Apps   │────>│ Collector       │────>│ (Traces)        │
└────────┬────────┘     └─────────────────┘     └─────────────────┘
         │
         │ /actuator/prometheus
         v
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│ Prometheus      │────>│ Alertmanager    │────>│ Notifications   │
│ (Metrics)       │     │                 │     │ (Slack/Email)   │
└─────────────────┘     └─────────────────┘     └─────────────────┘
         │
         v
┌─────────────────┐
│ Grafana         │<────┐
│ (Visualization) │     │
└─────────────────┘     │
                        │
┌─────────────────┐     │
│ Loki            │─────┘
│ (Logs)          │
└─────────────────┘
         ^
         │
┌─────────────────┐
│ Promtail        │
│ (Log Collector) │
└─────────────────┘
```

## Services & Ports

| Service | Port | URL | Credentials |
|---------|------|-----|-------------|
| Prometheus | 9093 | http://localhost:9093 | - |
| Grafana | 3000 | http://localhost:3000 | admin/admin |
| Alertmanager | 9094 | http://localhost:9094 | - |
| Loki | 3100 | http://localhost:3100 | - |
| Tempo | 3200 | http://localhost:3200 | - |
| Zipkin | 9411 | http://localhost:9411 | - |
| OTEL Collector (gRPC) | 4317 | - | - |
| OTEL Collector (HTTP) | 4318 | http://localhost:4318 | - |

## Starting the Stack

```bash
# Start all infrastructure
docker-compose -f infra/docker/docker-compose.yml up -d

# Start only observability stack
docker-compose -f infra/docker/docker-compose.yml up -d prometheus grafana alertmanager loki tempo promtail otel-collector

# Check status
docker-compose -f infra/docker/docker-compose.yml ps
```

---

## Prometheus

### Overview

Prometheus collects metrics from applications via HTTP endpoints and stores them as time-series data.

### Accessing Prometheus

- UI: http://localhost:9093
- Targets: http://localhost:9093/targets
- Alerts: http://localhost:9093/alerts

### Custom Metrics

| Metric | Type | Application | Description |
|--------|------|-------------|-------------|
| `wikimedia_events_produced_total` | Counter | Producer | Total events sent to Kafka |
| `wikimedia_stream_reconnects_total` | Counter | Producer | Reconnection attempts |
| `wikimedia_events_persisted_total` | Counter | Consumer | Events saved to MongoDB |
| `wikimedia_events_dlq_total` | Counter | Consumer | Events sent to DLQ |
| `wikimedia_events_queue_size` | Gauge | Consumer | Current queue size |

### PromQL Query Examples

#### Basic Queries

```promql
# Current queue size
wikimedia_events_queue_size

# Total events produced
wikimedia_events_produced_total

# Events produced per second (rate over 5 minutes)
rate(wikimedia_events_produced_total[5m])

# Events persisted per second
rate(wikimedia_events_persisted_total[5m])
```

#### Aggregation Queries

```promql
# Sum of all events produced
sum(wikimedia_events_produced_total)

# Average queue size over time
avg_over_time(wikimedia_events_queue_size[1h])

# Max queue size in last hour
max_over_time(wikimedia_events_queue_size[1h])

# DLQ events in last 5 minutes
increase(wikimedia_events_dlq_total[5m])
```

#### Rate and Delta Queries

```promql
# Rate of events per second
rate(wikimedia_events_produced_total[5m])

# Increase in DLQ events over 10 minutes
increase(wikimedia_events_dlq_total[10m])

# Rate of change (derivative)
deriv(wikimedia_events_queue_size[5m])
```

#### JVM Metrics

```promql
# JVM heap usage percentage
jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"} * 100

# GC time percentage
rate(jvm_gc_pause_seconds_sum[5m]) / rate(jvm_gc_pause_seconds_count[5m])

# Active threads
jvm_threads_live_threads

# CPU usage
process_cpu_usage
```

#### Kafka Metrics

```promql
# Consumer lag (if available)
kafka_consumer_fetch_manager_records_lag_max

# Producer send rate
rate(kafka_producer_topic_record_send_total[5m])

# Consumer poll rate
rate(kafka_consumer_fetch_manager_fetch_total[5m])
```

#### HTTP Metrics

```promql
# Request count by endpoint
sum(rate(http_server_requests_seconds_count[5m])) by (uri)

# p99 latency by endpoint
histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket[5m])) by (le, uri))

# Error rate (5xx)
sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) / sum(rate(http_server_requests_seconds_count[5m])) * 100

# Successful request rate
sum(rate(http_server_requests_seconds_count{status=~"2.."}[5m]))
```

#### Complex Queries

```promql
# Processing efficiency (persisted / received)
rate(wikimedia_events_persisted_total[5m]) / rate(wikimedia_events_produced_total[5m]) * 100

# Queue growth rate
deriv(wikimedia_events_queue_size[5m])

# Alert: High queue with no persistence
wikimedia_events_queue_size > 1000 and rate(wikimedia_events_persisted_total[5m]) == 0

# Correlate producer and consumer rates
rate(wikimedia_events_produced_total[5m]) - rate(wikimedia_events_persisted_total[5m])
```

### Recording Rules

Add to `prometheus/recording_rules.yml` for pre-computed queries:

```yaml
groups:
  - name: wikimedia_recording_rules
    rules:
      - record: wikimedia:events_produced:rate5m
        expr: rate(wikimedia_events_produced_total[5m])

      - record: wikimedia:events_persisted:rate5m
        expr: rate(wikimedia_events_persisted_total[5m])

      - record: wikimedia:processing_efficiency:ratio
        expr: rate(wikimedia_events_persisted_total[5m]) / rate(wikimedia_events_produced_total[5m])
```

---

## Alerting

### Pre-configured Alerts

| Alert | Severity | Condition | Duration |
|-------|----------|-----------|----------|
| HighEventQueueSize | warning | queue > 5000 | 2m |
| CriticalEventQueueSize | critical | queue > 8000 | 1m |
| DLQEventsIncreasing | warning | DLQ +10 in 5m | 1m |
| ApplicationDown | critical | up == 0 | 1m |
| HighJVMMemoryUsage | warning | heap > 80% | 5m |
| NoEventsProduced | warning | rate == 0 | 5m |
| NoEventsPersisted | critical | queue > 0 && rate == 0 | 5m |
| HighHTTPLatency | warning | p99 > 2s | 5m |
| FrequentReconnects | warning | reconnects > 5 in 10m | 1m |

### Viewing Alerts

- Prometheus: http://localhost:9093/alerts
- Alertmanager: http://localhost:9094

### Custom Alert Examples

Add to `prometheus/alert_rules.yml`:

```yaml
# Kafka lag alert
- alert: HighKafkaLag
  expr: kafka_consumer_fetch_manager_records_lag_max > 10000
  for: 5m
  labels:
    severity: warning
  annotations:
    summary: "High Kafka consumer lag"
    description: "Lag is {{ $value }} messages"

# Memory pressure alert
- alert: MemoryPressure
  expr: (jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}) > 0.9 and rate(jvm_gc_pause_seconds_sum[5m]) > 0.1
  for: 5m
  labels:
    severity: critical
  annotations:
    summary: "Memory pressure detected"
    description: "High heap usage with increased GC activity"
```

### Configuring Alertmanager for Notifications

#### Slack Integration

Edit `alertmanager/alertmanager.yml`:

```yaml
global:
  slack_api_url: 'https://hooks.slack.com/services/YOUR/SLACK/WEBHOOK'

receivers:
  - name: 'slack-notifications'
    slack_configs:
      - channel: '#alerts'
        send_resolved: true
        title: '{{ .Status | toUpper }} {{ .CommonLabels.alertname }}'
        text: '{{ range .Alerts }}{{ .Annotations.description }}{{ end }}'
```

#### Email Integration

```yaml
global:
  smtp_smarthost: 'smtp.gmail.com:587'
  smtp_from: 'alertmanager@yourdomain.com'
  smtp_auth_username: 'your-email@gmail.com'
  smtp_auth_password: 'your-app-password'

receivers:
  - name: 'email-notifications'
    email_configs:
      - to: 'team@yourdomain.com'
        send_resolved: true
```

#### PagerDuty Integration

```yaml
receivers:
  - name: 'pagerduty-critical'
    pagerduty_configs:
      - service_key: 'YOUR_PAGERDUTY_SERVICE_KEY'
        severity: critical
```

#### Ticket System Integration (Jira/ServiceNow)

Use webhook receivers with a bridge service:

```yaml
receivers:
  - name: 'ticket-system'
    webhook_configs:
      - url: 'http://your-ticket-bridge:8080/create-ticket'
        send_resolved: true
```

Example bridge service that creates Jira tickets:

```python
from flask import Flask, request
import requests

app = Flask(__name__)

JIRA_URL = "https://your-domain.atlassian.net"
JIRA_AUTH = ("email", "api-token")

@app.route('/create-ticket', methods=['POST'])
def create_ticket():
    alert = request.json

    payload = {
        "fields": {
            "project": {"key": "OPS"},
            "summary": alert['alerts'][0]['labels']['alertname'],
            "description": alert['alerts'][0]['annotations']['description'],
            "issuetype": {"name": "Bug"},
            "priority": {"name": "High" if alert['alerts'][0]['labels']['severity'] == 'critical' else "Medium"}
        }
    }

    response = requests.post(
        f"{JIRA_URL}/rest/api/2/issue",
        json=payload,
        auth=JIRA_AUTH
    )

    return {"status": "created", "key": response.json().get('key')}

if __name__ == '__main__':
    app.run(port=8080)
```

---

## Grafana

### Overview

Grafana provides visualization for metrics (Prometheus), logs (Loki), and traces (Tempo).

### Pre-configured Dashboard

The "Wikimedia Kafka Application" dashboard includes:
- Producer/Consumer message rates
- Event queue size
- DLQ events count
- JVM heap usage
- HTTP request latency (p99)

### Creating Custom Dashboards

#### Step 1: Create Dashboard

1. Click "+" → "Dashboard"
2. Click "Add visualization"
3. Select data source (Prometheus)

#### Step 2: Add Panels

**Time Series Panel (Message Rate)**
```promql
rate(wikimedia_events_produced_total[5m])
```
- Title: "Events Produced Rate"
- Unit: events/sec

**Gauge Panel (Queue Size)**
```promql
wikimedia_events_queue_size
```
- Title: "Event Queue Size"
- Thresholds: Green < 1000, Yellow < 5000, Red >= 5000

**Stat Panel (Total Events)**
```promql
sum(wikimedia_events_produced_total)
```
- Title: "Total Events Produced"
- Unit: short

**Table Panel (Endpoint Stats)**
```promql
sum(rate(http_server_requests_seconds_count[5m])) by (uri, method, status)
```
- Title: "API Endpoint Statistics"

#### Step 3: Dashboard Variables

Add variables for filtering:

1. Dashboard settings → Variables → Add variable
2. Create `application` variable:
   - Type: Query
   - Data source: Prometheus
   - Query: `label_values(up, application)`
3. Use in queries: `wikimedia_events_queue_size{application="$application"}`

### Useful Panel Templates

#### Service Health Panel

```promql
# Query A - Up status
up{job=~"kafka-.*"}

# Query B - Memory usage
jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}
```

#### Processing Pipeline Panel

```promql
# Combined view of data flow
rate(wikimedia_events_produced_total[5m]) or
rate(wikimedia_events_persisted_total[5m]) or
wikimedia_events_queue_size
```

### Grafana Alerting (Unified Alerting)

#### Create Alert Rule

1. Go to Alerting → Alert rules
2. Click "New alert rule"
3. Configure:
   - Query: `wikimedia_events_queue_size > 5000`
   - Condition: Is above threshold
   - Evaluation: Every 1m, For 2m
4. Add labels: `severity: warning`
5. Configure notification policy

#### Contact Points

1. Go to Alerting → Contact points
2. Add new contact point:
   - Name: "slack-ops"
   - Type: Slack
   - Webhook URL: Your Slack webhook

#### Notification Policies

1. Go to Alerting → Notification policies
2. Create routing:
   - Match: `severity = critical`
   - Contact point: "pagerduty-critical"

---

## Loki (Logs)

### Querying Logs

Access via Grafana → Explore → Select "Loki"

#### LogQL Query Examples

```logql
# All logs from a container
{container_name="sb-kafka"}

# Filter by log level
{container_name=~".*producer.*"} |= "ERROR"

# JSON parsing
{job="containerlogs"} | json | level="error"

# Rate of errors
rate({container_name=~".*"} |= "ERROR" [5m])

# Extract metrics from logs
sum by (container_name) (count_over_time({container_name=~".*"} |= "ERROR" [1h]))
```

#### Correlating Logs with Traces

When you see a trace ID in logs:
1. Click on the TraceID link (auto-detected via derived fields)
2. View the trace in Tempo

---

## Tempo (Traces)

### Viewing Traces

1. Grafana → Explore → Select "Tempo"
2. Search by:
   - Trace ID
   - Service name
   - Duration
   - Tags

### Trace-to-Logs

Click on a span to see correlated logs from Loki.

### Adding Trace Context to Logs

Update your application's logback configuration:

```xml
<pattern>%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - traceId=%X{traceId} spanId=%X{spanId} - %msg%n</pattern>
```

---

## Best Practices

### Dashboard Design

1. **Use consistent colors**: Green=Good, Yellow=Warning, Red=Critical
2. **Set thresholds**: Configure meaningful thresholds for gauges
3. **Add descriptions**: Document what each panel shows
4. **Use variables**: Make dashboards reusable
5. **Group related panels**: Use rows to organize

### Alert Design

1. **Avoid alert fatigue**: Only alert on actionable conditions
2. **Use appropriate durations**: Avoid false positives
3. **Include runbook links**: Add URLs to documentation
4. **Set proper severities**: Critical vs Warning
5. **Test alerts**: Verify they fire correctly

### Query Optimization

1. **Use recording rules**: Pre-compute expensive queries
2. **Limit time ranges**: Don't query years of data
3. **Use appropriate rates**: 5m rate is usually good
4. **Avoid high cardinality**: Don't use unbounded labels

### Retention Policy

Configure data retention based on needs:

```yaml
# Prometheus (docker-compose command)
- '--storage.tsdb.retention.time=30d'

# Loki (loki-config.yml)
limits_config:
  retention_period: 720h  # 30 days

# Tempo (tempo-config.yml)
compactor:
  compaction:
    block_retention: 720h  # 30 days
```

---

## Troubleshooting

### Common Issues

#### Prometheus not scraping targets

```bash
# Check targets status
curl http://localhost:9093/api/v1/targets

# Verify application metrics endpoint
curl http://localhost:9091/actuator/prometheus
```

#### Grafana not showing data

1. Check data source configuration
2. Verify time range selection
3. Test query in Prometheus UI first

#### Alerts not firing

1. Check Prometheus → Alerts
2. Verify Alertmanager is receiving alerts
3. Check notification configuration

### Useful Commands

```bash
# Prometheus reload config
curl -X POST http://localhost:9093/-/reload

# Check Alertmanager config
curl http://localhost:9094/api/v2/status

# Loki health
curl http://localhost:3100/ready

# Tempo health
curl http://localhost:3200/ready
```

---

## Resources

- [Prometheus Documentation](https://prometheus.io/docs/)
- [PromQL Cheat Sheet](https://promlabs.com/promql-cheat-sheet/)
- [Grafana Documentation](https://grafana.com/docs/)
- [Loki Documentation](https://grafana.com/docs/loki/latest/)
- [Tempo Documentation](https://grafana.com/docs/tempo/latest/)
- [Alertmanager Configuration](https://prometheus.io/docs/alerting/latest/configuration/)

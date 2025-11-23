# Kubernetes Deployment Guide

This guide covers deploying the Wikimedia Kafka Streaming Application to Kubernetes with full HA (High Availability) configuration.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    Kind Cluster (4 nodes)                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐         │
│  │ Kafka       │    │ Kafka       │    │ Kafka       │         │
│  │ Broker 0    │    │ Broker 1    │    │ Broker 2    │         │
│  └─────────────┘    └─────────────┘    └─────────────┘         │
│                                                                 │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐         │
│  │ MongoDB     │    │ MongoDB     │    │ MongoDB     │         │
│  │ Primary     │    │ Secondary   │    │ Secondary   │         │
│  └─────────────┘    └─────────────┘    └─────────────┘         │
│                                                                 │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐         │
│  │ Producer    │    │ Consumer    │    │ Consumer    │         │
│  │ Pod (HPA)   │    │ Pod 1       │    │ Pod 2 (HPA) │         │
│  └─────────────┘    └─────────────┘    └─────────────┘         │
│                                                                 │
│  ┌───────────────────────────────────────────────────┐         │
│  │ Observability: Prometheus, Grafana, Loki, Tempo   │         │
│  └───────────────────────────────────────────────────┘         │
└─────────────────────────────────────────────────────────────────┘
```

## Prerequisites

- Docker
- Kind (Kubernetes in Docker)
- kubectl
- Helm (for Strimzi installation)

### Install Prerequisites

```bash
# Install Kind
curl -Lo ./kind https://kind.sigs.k8s.io/dl/v0.20.0/kind-linux-amd64
chmod +x ./kind
sudo mv ./kind /usr/local/bin/kind

# Install kubectl
curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
chmod +x kubectl
sudo mv kubectl /usr/local/bin/

# Install Helm
curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash
```

## Quick Start

### 1. Create Kind Cluster

```bash
cd infra/k8s
kind create cluster --config kind-config.yaml
```

### 2. Install NGINX Ingress Controller

```bash
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml

# Wait for ingress controller to be ready
kubectl wait --namespace ingress-nginx \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller \
  --timeout=90s
```

### 3. Install Strimzi Kafka Operator

```bash
# Create Strimzi namespace
kubectl create namespace strimzi-system

# Install Strimzi operator using Helm
helm repo add strimzi https://strimzi.io/charts/
helm repo update
helm install strimzi-kafka-operator strimzi/strimzi-kafka-operator \
  --namespace strimzi-system \
  --set watchNamespaces="{wikimedia}"

# Wait for operator to be ready
kubectl wait --namespace strimzi-system \
  --for=condition=ready pod \
  --selector=name=strimzi-cluster-operator \
  --timeout=300s
```

### 4. Deploy the Stack

```bash
# Create namespace
kubectl apply -f namespace.yaml

# Deploy secrets and configmaps
kubectl apply -f secrets/
kubectl apply -f configmaps/

# Deploy Kafka cluster (wait for it to be ready)
kubectl apply -f kafka/kafka-metrics-config.yaml
kubectl apply -f kafka/kafka-cluster.yaml

# Wait for Kafka to be ready (takes a few minutes)
kubectl wait kafka/wikimedia-kafka --for=condition=Ready --timeout=600s -n wikimedia

# Create Kafka topics
kubectl apply -f kafka/kafka-topics.yaml

# Deploy Kafka UI
kubectl apply -f kafka/kafka-ui.yaml

# Deploy MongoDB
kubectl apply -f mongodb/mongodb-keyfile-secret.yaml
kubectl apply -f mongodb/mongodb-statefulset.yaml

# Wait for MongoDB pods to be ready
kubectl wait --for=condition=ready pod -l app=mongodb -n wikimedia --timeout=300s

# Initialize MongoDB replica set
kubectl apply -f mongodb/mongodb-init-job.yaml

# Wait for init job to complete
kubectl wait --for=condition=complete job/mongodb-init-replica-set -n wikimedia --timeout=120s

# Deploy Mongo Express
kubectl apply -f mongodb/mongo-express.yaml

# Deploy observability stack
kubectl apply -f observability/

# Build and load application images
cd ../..
./build-and-load-images.sh

# Deploy applications
kubectl apply -f infra/k8s/apps/

# Deploy ingress
kubectl apply -f infra/k8s/ingress.yaml
```

### 5. Build and Load Application Images

Create this script at the project root:

```bash
#!/bin/bash
# build-and-load-images.sh

# Build the project
./mvnw clean package -DskipTests

# Build Docker images
docker build -t wikimedia/kafka-producer:latest -f kafka-producer-wikimedia/Dockerfile .
docker build -t wikimedia/kafka-consumer:latest -f kafka-consumer-database/Dockerfile .

# Load images into Kind cluster
kind load docker-image wikimedia/kafka-producer:latest --name wikimedia-kafka
kind load docker-image wikimedia/kafka-consumer:latest --name wikimedia-kafka
```

## Access Services

### NodePort Access

| Service | URL |
|---------|-----|
| Producer | http://localhost:30090 |
| Consumer | http://localhost:30091 |
| Kafka UI | http://localhost:30080 |
| Mongo Express | http://localhost:30082 |
| Prometheus | http://localhost:30093 |
| Grafana | http://localhost:31000 |
| Zipkin | http://localhost:30411 |

### Ingress Access (add to /etc/hosts)

```bash
# Add to /etc/hosts
127.0.0.1 producer.wikimedia.local
127.0.0.1 consumer.wikimedia.local
127.0.0.1 kafka-ui.wikimedia.local
127.0.0.1 grafana.wikimedia.local
127.0.0.1 prometheus.wikimedia.local
127.0.0.1 zipkin.wikimedia.local
127.0.0.1 mongo-express.wikimedia.local
```

Then access:
- http://producer.wikimedia.local
- http://consumer.wikimedia.local
- http://grafana.wikimedia.local (admin/admin)

## HA Features

### Kafka HA
- 3 Kafka brokers with replication factor 3
- 3 Zookeeper nodes for coordination
- Topics configured with `min.insync.replicas: 2`

### MongoDB HA
- 3-node replica set with automatic failover
- Primary election via replica set protocol
- Read preference can be configured for load distribution

### Application HA
- Producer: 1-5 replicas with HPA
- Consumer: 2-10 replicas with HPA
- Pod Disruption Budget ensures minimum availability
- Liveness/readiness probes for health checking

## Monitoring

### Check Cluster Status

```bash
# Check all pods
kubectl get pods -n wikimedia

# Check Kafka cluster status
kubectl get kafka -n wikimedia

# Check HPA status
kubectl get hpa -n wikimedia

# Check MongoDB replica set status
kubectl exec -it mongodb-0 -n wikimedia -- mongosh -u admin -p admin --authenticationDatabase admin --eval "rs.status()"
```

### View Logs

```bash
# Producer logs
kubectl logs -f -l app=kafka-producer -n wikimedia

# Consumer logs
kubectl logs -f -l app=kafka-consumer -n wikimedia

# Kafka broker logs
kubectl logs -f wikimedia-kafka-kafka-0 -n wikimedia
```

### Grafana Dashboards

Access Grafana at http://localhost:31000 (admin/admin). Pre-configured datasources:
- Prometheus
- Loki
- Tempo
- Zipkin

## Troubleshooting

### Kafka Not Ready

```bash
# Check Kafka operator logs
kubectl logs -f -l name=strimzi-cluster-operator -n strimzi-system

# Check Kafka pod status
kubectl describe pod wikimedia-kafka-kafka-0 -n wikimedia
```

### MongoDB Replica Set Issues

```bash
# Check MongoDB logs
kubectl logs -f mongodb-0 -n wikimedia

# Reinitialize replica set
kubectl delete job mongodb-init-replica-set -n wikimedia
kubectl apply -f mongodb/mongodb-init-job.yaml
```

### Application Not Starting

```bash
# Check if images are loaded
docker exec wikimedia-kafka-control-plane crictl images | grep wikimedia

# Reload images
kind load docker-image wikimedia/kafka-producer:latest --name wikimedia-kafka
kind load docker-image wikimedia/kafka-consumer:latest --name wikimedia-kafka
```

### Persistent Volume Issues

```bash
# Check PVC status
kubectl get pvc -n wikimedia

# If PVC is pending, check storage class
kubectl get sc
```

## Cleanup

```bash
# Delete the Kind cluster
kind delete cluster --name wikimedia-kafka
```

## Scaling

### Manual Scaling

```bash
# Scale consumer
kubectl scale deployment kafka-consumer --replicas=5 -n wikimedia

# Scale producer
kubectl scale deployment kafka-producer --replicas=3 -n wikimedia
```

### HPA Configuration

The HPA is configured to scale based on CPU and memory utilization:
- Producer: 1-5 replicas, scales at 70% CPU
- Consumer: 2-10 replicas, scales at 70% CPU

## Security Considerations

For production:
1. Use external secrets management (Vault, Sealed Secrets)
2. Enable TLS for Kafka and MongoDB
3. Configure network policies
4. Use RBAC for service accounts
5. Enable pod security policies/standards

#!/bin/bash
set -e

echo "Building Maven project..."
./mvnw clean package -DskipTests

echo "Building Docker images..."

# Build producer image
docker build -t wikimedia/kafka-producer:latest \
  --build-arg JAR_FILE=kafka-producer-wikimedia/target/*.jar \
  -f infra/k8s/docker/Dockerfile.producer .

# Build consumer image
docker build -t wikimedia/kafka-consumer:latest \
  --build-arg JAR_FILE=kafka-consumer-database/target/*.jar \
  -f infra/k8s/docker/Dockerfile.consumer .

echo "Loading images into Kind cluster..."
kind load docker-image wikimedia/kafka-producer:latest --name wikimedia-kafka
kind load docker-image wikimedia/kafka-consumer:latest --name wikimedia-kafka

echo "Done! Images loaded into Kind cluster."

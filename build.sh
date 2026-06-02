#!/usr/bin/env bash
# build.sh — builds all MailPulseAI AI microservices locally (without Docker)
# Run from the project root: ./build.sh
# For Docker builds, use: docker-compose up --build -d
set -e

SERVICES=(
  "eureka-server"
  "api-gateway"
  "email-ingestion-service"
  "ai-processing-service"
  "whatsapp-messaging-service"
  "outbound-mail-service"
)

echo "╔══════════════════════════════════════════════╗"
echo "║        MailPulseAI AI — Build All Services       ║"
echo "╚══════════════════════════════════════════════╝"
echo ""

for svc in "${SERVICES[@]}"; do
  echo "▶  Building $svc..."
  (cd "$svc" && mvn clean package -DskipTests -q)
  echo "   ✅ $svc built"
done

echo ""
echo "✅  All services built. Run: docker-compose up --build -d"

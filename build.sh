#!/usr/bin/env bash
# build.sh — builds the MailPulseAI monolithic application locally (without Docker)
# Run from the project root: ./build.sh
# For Docker builds, use: docker-compose up --build -d
set -e

echo "╔══════════════════════════════════════════════╗"
echo "║      MailPulseAI AI — Build Monolith App     ║"
echo "╚══════════════════════════════════════════════╝"
echo ""

echo "▶  Building mailpulseai-monolith..."
(cd mailpulseai-monolith && mvn clean package -DskipTests -q)
echo "   ✅ Monolith built"

echo ""
echo "✅  Build complete. Run: docker-compose up --build -d"

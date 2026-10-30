#!/usr/bin/env bash
set -e

echo "=========================================================="
echo " Starting API Rate Limiter Distributed Platform..."
echo "=========================================================="

if [ ! -f .env ]; then
    echo "Creating .env from .env.example..."
    cp .env.example .env
fi

echo "Building and starting Docker containers..."
docker compose up -d --build

echo ""
echo "Services started successfully!"
echo "----------------------------------------------------------"
echo "Frontend Dashboard:   http://localhost:3000"
echo "Backend API:          http://localhost:8080"
echo "Swagger Docs:         http://localhost:8080/swagger-ui/index.html"
echo "Gateway API:          http://localhost:8081"
echo "Prometheus:           http://localhost:9090"
echo "Grafana:              http://localhost:3001  (admin/admin)"
echo "----------------------------------------------------------"
echo "Demo Login: admin / admin123  or  developer / dev123"

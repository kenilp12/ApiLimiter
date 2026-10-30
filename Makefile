.PHONY: help build start stop restart logs clean test

help:
	@echo "API Rate Limiter — Distributed Platform Commands:"
	@echo "  make start      - Start all services with Docker Compose"
	@echo "  make stop       - Stop all services"
	@echo "  make restart    - Restart all services"
	@echo "  make build      - Build backend, gateway, and frontend"
	@echo "  make test       - Run backend and frontend tests"
	@echo "  make logs       - Tail logs from all containers"
	@echo "  make clean      - Clean build artifacts and docker volumes"

build:
	mvn clean package -DskipTests
	cd frontend && npm install && npm run build

test:
	mvn clean test
	cd frontend && npm test -- --run

start:
	docker compose up -d --build

stop:
	docker compose down

restart: stop start

logs:
	docker compose logs -f

clean:
	mvn clean
	docker compose down -v
	rm -rf frontend/dist frontend/node_modules

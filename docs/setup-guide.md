# Setup & Deployment Guide — Distributed API Rate Limiter

This guide walks through running the **API Rate Limiter** platform locally using either **Docker Compose** (recommended for production-like evaluation) or **local host execution** for rapid development.

---

## 1. Prerequisites

Ensure the following tools are installed on your machine:
- **Java**: JDK 21 or later (`java -version`)
- **Maven**: Version 3.9+ (`mvn -version` or use included `.\mvn.cmd`)
- **Node.js**: Node 18+ and NPM 9+ (`node -v`, `npm -v`)
- **Docker & Docker Compose**: Docker 24+ (`docker --version`, `docker compose version`)

---

## 2. Quickstart with Docker Compose

The simplest way to start the entire distributed platform (PostgreSQL, Redis, Backend, Gateway, Frontend, Prometheus, and Grafana) is via Docker Compose:

### Step 1: Clone and Configure Environment
```bash
# Copy example environment configuration
cp .env.example .env

# On Windows PowerShell:
Copy-Item .env.example .env
```

### Step 2: Build and Launch Containers
```bash
# Using Makefile
make start

# Or directly with Docker Compose
docker compose up -d --build
```

### Step 3: Verify Container Health
```bash
docker compose ps
```
All seven services will initialize and pass their automated health checks.

---

## 3. Local Development (Host Mode)

If developing individual services locally without containerizing the Java/React applications:

### Step 1: Start Supporting Infrastructure
Run only PostgreSQL and Redis via Docker:
```bash
docker compose up -d postgres redis
```

### Step 2: Run Management Backend Service
```bash
# In the root repository directory:
./mvn.cmd spring-boot:run -pl backend
```
- The backend will start on **port 8080**.
- Flyway migrations automatically initialize PostgreSQL tables.
- `DataInitializer` automatically seeds demo users (`admin`, `developer`), default policies, and sample API key.

### Step 3: Run Spring Cloud Gateway
```bash
# In a separate terminal:
./mvn.cmd spring-boot:run -pl gateway
```
- The gateway will start on **port 8081**.
- Connects to Redis on `localhost:6379` to execute Lua rate-limiting scripts.

### Step 4: Run React Frontend
```bash
cd frontend
npm install
npm run dev
```
- The Vite dev server will launch at [http://localhost:3000](http://localhost:3000).

---

## 4. Default Service URLs & Ports

| Service | Host Port | Internal URL | Purpose |
| :--- | :--- | :--- | :--- |
| **React Dashboard** | `3000` | `http://localhost:3000` | SaaS developer management dashboard |
| **Backend REST API** | `8080` | `http://localhost:8080` | Management API, Flyway, JWT auth |
| **Swagger UI Docs** | `8080` | `http://localhost:8080/swagger-ui.html` | Interactive OpenAPI documentation |
| **Spring Cloud Gateway** | `8081` | `http://localhost:8081` | Distributed rate limiter entry point |
| **Prometheus** | `9090` | `http://localhost:9090` | Metrics scraper and PromQL engine |
| **Grafana** | `3001` | `http://localhost:3001` | Provisioned dashboards (`admin/admin`) |
| **Redis** | `6379` | `localhost:6379` | Distributed in-memory state store |
| **PostgreSQL** | `5432` | `localhost:5432` | Durable relational database |

---

## 5. Running Automated Tests

### 5.1 Backend & Gateway Test Suites
```bash
# Run all unit, algorithm concurrency, and MockMvc tests:
./mvn.cmd clean test
```

### 5.2 Frontend Vitest & React Testing Library
```bash
cd frontend
npm test
```

---

## 6. Troubleshooting Common Issues

### Port Conflicts
If port `8080`, `8081`, `3000`, or `5432` is already in use:
1. Edit `.env` to assign alternative host ports (e.g., `BACKEND_PORT=8090`).
2. Re-run `docker compose up -d`.

### Redis Connection Error
If the gateway logs `RedisConnectionFailureException`:
1. Verify Redis is running: `docker compose ps redis` or `redis-cli ping`.
2. Check `SPRING_DATA_REDIS_HOST` in `.env` (use `localhost` for host mode, `redis` in Docker).

### Database Reset
To wipe local test data and re-run Flyway migrations from scratch:
```bash
docker compose down -v
docker compose up -d postgres
```

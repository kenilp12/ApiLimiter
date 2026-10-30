# REST API Specification — Distributed API Rate Limiter

This document details the public and internal REST API endpoints exposed by the Backend and Spring Cloud Gateway services.

Interactive Swagger/OpenAPI documentation is also available live at:
- **Swagger UI**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **OpenAPI JSON**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

---

## 1. Authentication Endpoints

### 1.1 Register Account
Registers a new user account. The first registered user is automatically assigned `ROLE_ADMIN`.

- **Method**: `POST`
- **Path**: `/api/auth/register`
- **Headers**: `Content-Type: application/json`
- **Request Body**:
```json
{
  "username": "developer",
  "email": "developer@example.com",
  "password": "securePassword123"
}
```
- **Response (201 Created)**:
```json
{
  "id": 2,
  "username": "developer",
  "email": "developer@example.com",
  "role": "ROLE_USER",
  "createdAt": "2026-10-24T08:00:00Z"
}
```

### 1.2 Authenticate / Login
Validates credentials and issues an HMAC-SHA256 signed JWT bearer token.

- **Method**: `POST`
- **Path**: `/api/auth/login`
- **Request Body**:
```json
{
  "username": "admin",
  "password": "admin123"
}
```
- **Response (200 OK)**:
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 86400000,
  "user": {
    "id": 1,
    "username": "admin",
    "email": "admin@apiratelimiter.io",
    "role": "ROLE_ADMIN",
    "createdAt": "2026-10-24T08:00:00Z"
  }
}
```

### 1.3 Get Current User Profile
- **Method**: `GET`
- **Path**: `/api/auth/me`
- **Headers**: `Authorization: Bearer <token>`
- **Response (200 OK)**: Returns the current `UserResponse`.

---

## 2. API Key Management

All key management endpoints require a valid JWT token.

### 2.1 Create API Key
Generates a cryptographically secure random API key prefixed with `arl_live_`.

> [!IMPORTANT]
> The full raw secret API key is returned **only once** in this response. The backend computes and stores only its SHA-256 digest.

- **Method**: `POST`
- **Path**: `/api/keys`
- **Headers**: `Authorization: Bearer <token>`
- **Request Body**:
```json
{
  "name": "Production Microservice Key",
  "policyId": 1,
  "expiresAt": null
}
```
- **Response (201 Created)**:
```json
{
  "apiKey": {
    "id": 5,
    "keyPrefix": "arl_live_8f9b...",
    "name": "Production Microservice Key",
    "status": "ACTIVE",
    "policyId": 1,
    "policyName": "Token Bucket Burst Tier",
    "policyAlgorithm": "TOKEN_BUCKET",
    "createdBy": "admin",
    "expiresAt": null,
    "createdAt": "2026-10-24T08:15:00Z"
  },
  "rawApiKey": "arl_live_8f9b4c2e1a3d5f6b7c8d9e0f1a2b3c4d",
  "warning": "Please copy and save your secret API key immediately. You will not be able to see it again."
}
```

### 2.2 List API Keys
- **Method**: `GET`
- **Path**: `/api/keys`
- **Headers**: `Authorization: Bearer <token>`
- **Response (200 OK)**: Array of `ApiKeyResponse` objects.

### 2.3 Revoke API Key
Marks the key as `REVOKED` and purges the key cache from Redis.

- **Method**: `DELETE`
- **Path**: `/api/keys/{id}`
- **Headers**: `Authorization: Bearer <token>`
- **Response (200 OK)**:
```json
{
  "message": "API key revoked successfully",
  "id": "5"
}
```

---

## 3. Rate Limit Policy Endpoints

Policies define the rate-limiting algorithm, quota limits, and time windows.

### 3.1 Create Rate Limit Policy (Admin Only)
- **Method**: `POST`
- **Path**: `/api/policies`
- **Headers**: `Authorization: Bearer <admin_token>`
- **Request Body**:
```json
{
  "name": "High Throughput API Tier",
  "description": "Token bucket allowing bursts up to 50 requests, refilling at 10/s",
  "algorithm": "TOKEN_BUCKET",
  "limitValue": 100,
  "timeWindowSeconds": 60,
  "burstCapacity": 50,
  "refillRate": 10.0,
  "isActive": true
}
```

### 3.2 List All Policies
- **Method**: `GET`
- **Path**: `/api/policies`
- **Headers**: `Authorization: Bearer <token>`

### 3.3 Update Policy (Admin Only)
- **Method**: `PUT`
- **Path**: `/api/policies/{id}`
- **Headers**: `Authorization: Bearer <admin_token>`

### 3.4 Delete Policy (Admin Only)
- **Method**: `DELETE`
- **Path**: `/api/policies/{id}`
- **Headers**: `Authorization: Bearer <admin_token>`

### 3.5 Activate / Deactivate Policy (Admin Only)
- **Method**: `PATCH`
- **Path**: `/api/policies/{id}/status?active=true`
- **Headers**: `Authorization: Bearer <admin_token>`

---

## 4. Analytics & Telemetry Endpoints

### 4.1 System Overview
- **Method**: `GET`
- **Path**: `/api/analytics/overview`
- **Headers**: `Authorization: Bearer <token>`
- **Response (200 OK)**:
```json
{
  "totalRequests": 1250,
  "allowedRequests": 1120,
  "blockedRequests": 130,
  "rejectionRatePercentage": 10.4,
  "averageResponseTimeMs": 24.5,
  "activeKeysCount": 4,
  "activePoliciesCount": 4,
  "algorithmBreakdown": {
    "TOKEN_BUCKET": 840,
    "SLIDING_WINDOW": 210,
    "FIXED_WINDOW": 120,
    "LEAKY_BUCKET": 80
  }
}
```

### 4.2 Paginated Requests Log
- **Method**: `GET`
- **Path**: `/api/analytics/requests?page=0&size=20`
- **Headers**: `Authorization: Bearer <token>`

### 4.3 Paginated Blocked (429) Requests
- **Method**: `GET`
- **Path**: `/api/analytics/blocked?page=0&size=20`
- **Headers**: `Authorization: Bearer <token>`

### 4.4 Time-Series Traffic Buckets
- **Method**: `GET`
- **Path**: `/api/analytics/timeseries?minutes=60`
- **Headers**: `Authorization: Bearer <token>`

---

## 5. Gateway Protected Endpoints (Port 8081)

All requests hitting the Gateway must supply an active API key via `X-API-Key` or `Authorization: Bearer <key>`.

### Sample 200 OK Response
```bash
curl -i -X GET http://localhost:8081/protected-api/data \
  -H "X-API-Key: arl_live_demo1234567890abcdef1234"
```

**Response Headers**:
```http
HTTP/1.1 200 OK
Content-Type: application/json
X-Correlation-ID: 7a3c8e9b-4f1d-48a0-9c2b-8a5f1e2c3d4e
X-RateLimit-Limit: 20
X-RateLimit-Remaining: 19
X-RateLimit-Reset: 10
X-RateLimit-Algorithm: TOKEN_BUCKET
```

**Response Body**:
```json
{
  "status": "success",
  "message": "Protected data payload retrieved successfully",
  "timestamp": "2026-10-24T08:30:00.120Z",
  "data": {
    "activeConnections": 1420,
    "cacheHitRatio": 0.942,
    "latencyP99Ms": 18.4
  }
}
```

### Sample HTTP 429 Too Many Requests Response
When rate limit is exceeded:

```bash
curl -i -X GET http://localhost:8081/protected-api/data \
  -H "X-API-Key: arl_live_demo1234567890abcdef1234"
```

**Response Headers**:
```http
HTTP/1.1 429 Too Many Requests
Content-Type: application/json
X-Correlation-ID: 8b4d9f0c-5e2a-49b1-ad3c-9b6a2f3d4e5f
X-RateLimit-Limit: 20
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 4
X-RateLimit-Algorithm: TOKEN_BUCKET
Retry-After: 4
```

**Response Body**:
```json
{
  "status": 429,
  "error": "Too Many Requests",
  "message": "Rate limit exceeded for policy 'Token Bucket Burst Tier'. Please retry after 4 seconds.",
  "algorithm": "TOKEN_BUCKET",
  "limit": 20,
  "remaining": 0,
  "retryAfterSeconds": 4,
  "correlationId": "8b4d9f0c-5e2a-49b1-ad3c-9b6a2f3d4e5f",
  "timestamp": "2026-10-24T08:30:05.450Z"
}
```

# Rate Limiter Service

A distributed rate limiting service built with Spring Boot, PostgreSQL, and Redis.

## Quick Start

```bash
# Start all services
docker compose up -d

# Verify health
curl http://localhost:8080/actuator/health
```

## API Endpoints

### Rate Limiting

```bash
# Check rate limit
curl -X POST http://localhost:8080/api/rate-limit/check \
  -H "Content-Type: application/json" \
  -d '{"identifier": "user-123", "scope": "USER", "policyId": "<policy-id>"}'
```

### Policies

```bash
# Create policy
curl -X POST http://localhost:8080/api/policies \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Standard Policy",
    "scope": "USER",
    "algorithm": "TOKEN_BUCKET",
    "maxRequests": 100,
    "windowSeconds": 60,
    "burstCapacity": 10,
    "refillRate": 5,
    "failMode": "FAIL_OPEN",
    "enabled": true
  }'

# List policies
curl http://localhost:8080/api/policies

# Get policy
curl http://localhost:8080/api/policies/<id>

# Delete policy
curl -X DELETE http://localhost:8080/api/policies/<id>
```

### Tenants

```bash
# Create tenant
curl -X POST http://localhost:8080/api/tenants \
  -H "Content-Type: application/json" \
  -d '{"name": "My Tenant", "tier": "BASIC", "enabled": true}'

# List tenants
curl http://localhost:8080/api/tenants
```

### IP Rules

```bash
# Create IP rule (CIDR range)
curl -X POST http://localhost:8080/api/ip-rules \
  -H "Content-Type: application/json" \
  -d '{
    "ipCidr": "192.168.0.0/16",
    "policyId": "<policy-id>",
    "description": "Internal network",
    "enabled": true
  }'

# Create IP rule (single IP)
curl -X POST http://localhost:8080/api/ip-rules \
  -H "Content-Type: application/json" \
  -d '{
    "ipAddress": "10.0.0.1",
    "policyId": "<policy-id>",
    "description": "VIP client",
    "enabled": true
  }'

# List IP rules
curl http://localhost:8080/api/ip-rules
```

### API Keys

```bash
# Create API key
curl -X POST http://localhost:8080/api/api-keys \
  -H "Content-Type: application/json" \
  -d '{
    "name": "My API Key",
    "tenantId": "<tenant-id>",
    "policyId": "<policy-id>",
    "enabled": true
  }'

# List API keys
curl http://localhost:8080/api/api-keys
```

### User Policies

```bash
# Assign policy to user
curl -X POST http://localhost:8080/api/user-policies \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user-123",
    "policyId": "<policy-id>",
    "tenantId": "<tenant-id>",
    "enabled": true
  }'

# Get user's policy
curl "http://localhost:8080/api/user-policies/user/user-123?tenantId=<tenant-id>"
```

### Alert Rules

```bash
# Create alert rule
curl -X POST http://localhost:8080/api/alert-rules \
  -H "Content-Type: application/json" \
  -d '{
    "name": "High Deny Rate",
    "policyId": "<policy-id>",
    "thresholdPercentage": 75,
    "windowSeconds": 60,
    "cooldownSeconds": 300,
    "enabled": true
  }'

# Test alert
curl -X POST http://localhost:8080/api/alert-rules/<id>/test
```

### Policy Rules (URL Pattern Matching)

```bash
# Create policy rule
curl -X POST http://localhost:8080/api/policy-rules \
  -H "Content-Type: application/json" \
  -d '{
    "policyId": "<policy-id>",
    "name": "Admin API",
    "resourcePattern": "/api/admin/**",
    "httpMethods": "GET,POST",
    "enabled": true
  }'

# Match policy rule
curl "http://localhost:8080/api/policy-rules/match?path=/api/admin/users&method=GET"
```

### Metrics & Monitoring

```bash
# Policy metrics
curl http://localhost:8080/api/policies/<id>/metrics

# Prometheus metrics
curl http://localhost:8080/actuator/prometheus

# Cache stats
curl http://localhost:8080/api/admin/cache
curl http://localhost:8080/api/admin/cache/policies/stats
```

## Rate Limiting Algorithms

| Algorithm | Description |
|-----------|-------------|
| `TOKEN_BUCKET` | Allows bursts up to capacity, refills at steady rate |
| `FIXED_WINDOW` | Fixed time windows with request counter |
| `SLIDING_LOG` | Precise sliding window using request timestamps |

## Fail Modes

| Mode | Behavior |
|------|----------|
| `FAIL_OPEN` | Allow requests when Redis is unavailable |
| `FAIL_CLOSED` | Deny requests when Redis is unavailable |

## Architecture

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   Client    │────▶│  Rate Limit │────▶│    Redis    │
│             │     │   Service   │     │  (counters) │
└─────────────┘     └──────┬──────┘     └─────────────┘
                           │
                    ┌──────▼──────┐
                    │  PostgreSQL │
                    │  (policies) │
                    └─────────────┘
```

## Configuration

Environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://postgres:5432/ratelimiter` | Database URL |
| `SPRING_DATASOURCE_USERNAME` | `root` | Database user |
| `SPRING_DATASOURCE_PASSWORD` | `root` | Database password |
| `SPRING_DATA_REDIS_HOST` | `redis` | Redis host |
| `SPRING_DATA_REDIS_PORT` | `6379` | Redis port |

## Development

```bash
# Build
./mvnw clean package -DskipTests

# Run locally (requires PostgreSQL and Redis)
./mvnw spring-boot:run
```

## Docker

```bash
# Build image
docker compose build

# Start services
docker compose up -d

# View logs
docker compose logs -f app

# Stop services
docker compose down

# Clean volumes
docker compose down -v
```

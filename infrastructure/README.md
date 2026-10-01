# Infrastructure

| Path | Contents | Phase |
|---|---|---|
| `docker/` | Docker Compose for local development | 4 |
| `config-repo/` | Non-secret configuration served by config-server (shared `application*.yml`, one file per service) | 4 |
| `k8s/` | Kubernetes manifests / Helm charts | 23–24 |
| `terraform/` | AWS infrastructure (ap-south-1) | 23–24 |

Design: [docs/13-deployment.md](../docs/13-deployment.md).

## Local environment

From the repository root, after copying `.env.example` to `.env` and replacing every `change-me` value:

```bash
docker compose --env-file .env -f infrastructure/docker/docker-compose.yml --profile infra --profile platform up -d --build
```

Stop with `... down`; add `-v` to delete the data volumes (the database init script runs again on the next start).

| Profile | Services |
|---|---|
| `infra` | postgres (PostGIS), redis, kafka (KRaft), kafka-ui, minio, mailpit |
| `media` | clamav (optional; needed by media-service virus scanning from Phase 6) |
| `platform` | config-server, service-discovery, api-gateway |
| `observability` | otel-collector, tempo, loki, alloy (log shipping), prometheus, grafana |
| `all` | everything above |

Profiles `core`, `commerce`, `fulfilment` and `insights` are added as their services are built.

### Ports (all bound to 127.0.0.1)

| Port | Service |
|---|---|
| 8080 | API gateway (set `GATEWAY_BIND_ADDRESS=0.0.0.0` to reach it from a phone) |
| 8888 / 8761 | config-server / service-discovery (Eureka dashboard) |
| 5432 | PostgreSQL. Databases `<service>_db`, roles `<service>_owner` and `<service>_app` |
| 6379 | Redis (password `REDIS_PASSWORD`) |
| 9092 | Kafka for clients on the host; containers use `kafka:29092` |
| 8085 | Kafka UI |
| 9000 / 9001 | MinIO API / console |
| 1025 / 8025 | Mailpit SMTP / web UI |
| 3310 | ClamAV (`media` profile) |
| 3000 | Grafana (`GRAFANA_ADMIN_USER` / `GRAFANA_ADMIN_PASSWORD`) |
| 9090 | Prometheus |
| 4317 / 4318 | OpenTelemetry collector (OTLP gRPC / HTTP) |

Management endpoints (health probes) run on the service port + 1000 inside the containers and are not published.

### Running a service from the IDE

Start `infra` (and usually `platform`) in Compose, then run the service with the variables from `.env`. Services find config-server at `http://localhost:8888` and Eureka at `http://localhost:8761/eureka/` by default. Set `FDP_LOGGING_FORMAT=plain` for human-readable console logs instead of JSON.

### Logs and traces

With the `observability` profile, Alloy ships every container's JSON logs to Loki. `service` and `level` are labels, and `traceId` and `correlationId` are structured metadata. In Grafana Explore: `{service="api-gateway"} | traceId="<id>"`. Set `TRACING_EXPORT_ENABLED=true` in `.env` to send spans to Tempo through the collector.

### Notes

- MinIO uses the pinned `bitnamilegacy/minio` image because the official image is no longer published (known issue KI-018). It is for local use only; deployed environments use Amazon S3.
- The image recipe for all backend services is `backend/Dockerfile` (multi-stage, non-root UID 10001, layered jar, health check on the liveness probe).

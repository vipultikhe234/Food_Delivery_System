# ADR-016: AWS as target cloud, cost-minimised for portfolio context
- Status: Accepted
- Date: 2026-10-01
- Related requirements: REQ-DEVOPS-003..005, NFR-AVAIL-001, OQ-01, OQ-02

## Context
This is a portfolio/learning build (OQ-01): design for full scale, demonstrate a modest load on a small budget. It still needs a real cloud deployment, CI/CD and DR evidence.

## Decision
- Target cloud: **AWS**, region `ap-south-1` (Mumbai).
- Services: EKS, RDS PostgreSQL, ElastiCache Redis, MSK, S3 + CloudFront, ECR, Secrets Manager, IAM OIDC for GitHub Actions. Everything is provisioned with Terraform.
- **Cost policy:**
  - `development` runs on a small node group, or reuses Docker Compose on a single VM.
  - `staging` is created **on demand** by Terraform for demonstrations (performance, security, DR drills) and destroyed afterwards.
  - `production` is a minimal multi-AZ footprint.
  - Kafka may use a single-node or serverless option outside production. The cost-saving trade-offs are documented per environment in 13-deployment.md.

## Consequences
### Positive
- Mature managed services matching every component. Region close to the India-first market.

### Negative / risks
- Managed Kafka and EKS have baseline costs, which is why on-demand environments are used.
- Production-scale availability (99.9%) cannot be proven long-term in a portfolio budget. Only the design and drills are demonstrated, and that is stated in the release evidence.

## Alternatives considered
- **GCP (GKE, Cloud SQL, Memorystore, Pub/Sub or Confluent):** equivalent.
- **Azure:** equivalent.
- **Single VM with Docker Compose for everything:** cheapest, but cannot demonstrate Kubernetes and HPA requirements.

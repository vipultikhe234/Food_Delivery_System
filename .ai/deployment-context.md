# Deployment Context (summary of docs/13-deployment.md, 14-disaster-recovery.md)

- **Environments:**
  - local: Docker Compose profiles `infra`, `platform`, `core`, `commerce`, `fulfilment`, `insights`, `observability`, `all`.
  - dev: auto-deploy on merge to `main`.
  - staging: on demand, for release candidates.
  - production: manual approval, auto-rollback.
- **Cloud:** AWS `ap-south-1` (EKS, RDS PostgreSQL, ElastiCache, MSK, S3 + CloudFront, ECR, Secrets Manager), provisioned with Terraform. Cost-minimised (ADR-016).
- **Images:** multi-stage, Temurin 21 JRE or distroless, non-root, read-only root filesystem, tagged by git SHA, Trivy-scanned, SBOM, cosign-signed.
- **Kubernetes:** shared Helm chart `fd-service`, per-service values; probes, HPA, PDB, NetworkPolicy default-deny, ExternalSecrets, IRSA; Flyway runs as a pre-upgrade Job.
- **CI/CD:** GitHub Actions (13 §5.2). Trunk-based development; protected `main`; Conventional Commits with requirement IDs.
- **Migrations:** expand → migrate → contract; indexes created `CONCURRENTLY`; never rename in place.
- **DR:** RPO ≤ 5 min / RTO ≤ 1 h (proposed, verified only by drills); PITR 14 days; cross-region snapshot copies; backup-and-restore regional strategy.
- **Never deploy from a local machine.**

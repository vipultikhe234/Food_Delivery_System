# ADR-011: Eureka + Spring Cloud Config in all environments (v1)
- Status: Accepted
- Date: 2026-10-01
- Related requirements: REQ-PLAT-002, REQ-PLAT-003

## Context
The brief requires service discovery (registration, health, load balancing) and central configuration. On Kubernetes these capabilities exist natively (DNS Services, ConfigMaps), so running Eureka there duplicates them.

## Decision
- For v1, run **Eureka** and **Spring Cloud Config Server** (Git-backed) in every environment, including Kubernetes. That gives one mental model and one code path, and satisfies MP §7–8 visibly.
- Secrets never go into the Config Server repository. They come from environment variables or External Secrets.
- Services use Spring Cloud's `DiscoveryClient` abstraction only, so switching to Spring Cloud Kubernetes later is a dependency and profile change.

## Consequences
### Positive
- Identical behaviour locally and in the cloud. Discovery and config are explicit and demonstrable.

### Negative / risks
- Redundant with Kubernetes primitives, plus two more highly available components to run in production.
- Revisit trigger: Kubernetes is used in production long-term, or Eureka causes operational incidents. That would be a new ADR superseding this one.

## Alternatives considered
- **Kubernetes-native discovery and ConfigMaps in clusters, Eureka only locally:** two code paths.
- **Consul:** an additional technology.

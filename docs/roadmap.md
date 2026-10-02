# Delivery phases

1. Portal: catalog, service form, request detail, build/Argo/endpoint links, audit and metadata scorecards.
2. Templates: tested Spring Boot endpoints, health probes, immutable image CI, Helm, runbook and ADR.
3. Git: one complete initial commit; durable idempotent request tracking.
4. CI: Java tests, Helm validation, OPA runtime guardrails, commit-tagged GHCR image.
5. GitOps: wait for passing CI; pin the image; verify live Argo CD and endpoint before READY.
6. IaC: approved PostgreSQL workflow, encrypted remote state, private RDS and managed secret references.
7. Ops: generated metrics/tracing/dashboard/alerts/SLO metadata, durable audit, approved production/promotion/cleanup and same-repository preview workflow.

Connected GitHub, Argo CD, Kubernetes, DNS, secret manager and observability must be
configured to demonstrate the live end-to-end outcome. Local tests validate code
and generated artifacts, not the availability of those external systems.

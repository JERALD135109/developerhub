# DeveloperHub status

## Implemented

- Branded Backstage portal: catalog, service creation, build/deployment history,
  audit and catalog plus operational-readiness scorecards.
- Authenticated provisioning API with durable single-instance request/audit state,
  idempotency, validation, production approvals and administrator retry.
- Tested Spring Boot golden-path template, CI, immutable images, secure Helm,
  runbook, catalog metadata, TechDocs, metrics and dashboard/alert artifacts.
- GitOps reconciles CI and Terraform runs for the current request, pins images to
  their exact SHA, registers the catalog, verifies Argo CD and endpoint readiness.
- AWS RDS Terraform with OIDC, approvals, managed credentials and External
  Secrets. GHCR image pull credentials are installed per service namespace.
- Staging promotion, same-repository PR previews and monitored dev cleanup.

## Verified locally

- Portal TypeScript, lint and formatting passed; 4 unit and 2 browser tests passed.
- 12 API tests passed before final cleanup/stale-run reconciliation changes. Final
  Java changes compile successfully; the last changes were not retested.
- Generated service HTTP/health tests, Helm lint/render, Terraform initialization
  and validation passed. Portal/backend production builds passed before the last
  source copy. No infrastructure plan or apply ran.

## Needed for a live deployment

Configure GitHub owner/token and workflow permissions, Backstage catalog token,
Kubernetes/Argo CD, ingress/DNS/TLS, AWS OIDC and private database network, GitHub
environment reviewers, External Secrets, GHCR pull secret, and the Prometheus /
Grafana / OTLP stack. No cloud resources or repositories were created here.

Production still needs a real login provider and permission policy, verified user
and team membership for audit/authorization, and PostgreSQL-backed multi-instance
state. Cluster preview TTL and namespace retention also depend on the target
platform's policy. These are called out in the root README.

Project setup and launch instructions: `../README.md`, `../gitops/README.md`,
and `app-config.example.yaml`. Pre-change parent files are backed up in
`portal/.completion/backups`; staging/tools are in ignored `portal/.completion`.

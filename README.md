# DeveloperHub

DeveloperHub is a portfolio project for exploring an internal developer platform. It combines a Backstage portal, a Spring Boot provisioning API, a service template, GitHub Actions, Helm, Argo CD manifests, policy checks, and Terraform examples.

The repository contains workflows for repository creation, image builds, GitOps deployment, production approval, promotion, cleanup, preview environments, and optional PostgreSQL infrastructure. These workflows require external GitHub, Kubernetes, Argo CD, and AWS configuration. The repository has not been verified as a live end-to-end cloud deployment. The checks listed below are local build and test results.

## Local demo

Requirements: Java 17, Maven, Node 24, and Yarn 4. Install portal dependencies from the lockfile, then run the portal from `portal` with `yarn start`.

To demonstrate the service template without creating cloud resources or GitHub repositories, render a local service and run its tests:

```powershell
./scripts/generate.ps1 -Name example-api -Owner platform
mvn -B -f generated/example-api/pom.xml verify
```

The API listens on port 8080. With a valid GitHub token and repository access configured, provisioning requests create real GitHub repositories and can trigger connected workflows. Keep tokens out of source control. Production approval and administrative operations use the separate `PLATFORM_ADMIN_TOKEN`; remote API clients also require `PLATFORM_API_TOKEN`.

## What the project demonstrates

- A Backstage service request form, request status, audit history, and scorecards.
- A Spring Boot API with file-backed request and audit persistence, idempotency, production approval, and administrative endpoints.
- Generated Spring Boot services with tests, health probes, container configuration, Helm charts, and operational documentation.
- GitHub Actions workflows for service CI and image publication, plus GitOps and preview workflow examples.
- Argo CD configuration and policy checks for controlled deployment patterns.
- Terraform and External Secrets examples for an optional PostgreSQL path.

These describe code and configuration in the repository. A feature that calls a connected external service is only demonstrated after that service is configured and the workflow succeeds.

## Validation

The checks run for this checkout are recorded in [docs/validation.md](docs/validation.md). API and portal tests, portal compilation and lint, a portal backend build, browser E2E tests, and a rendered service build passed. Helm and Terraform checks were not rerun in this environment because those tools were unavailable. Live GitHub, Kubernetes, and AWS provisioning was not performed.

## Connected deployment requirements

A connected demo needs GitHub credentials, an Argo CD repository and API configuration, a Kubernetes cluster, an ingress and service domain, and catalog access. The PostgreSQL path also needs an AWS account, approved GitHub environments, OIDC, Terraform state storage, private networking, RDS, and External Secrets Operator. See [gitops/README.md](gitops/README.md) for infrastructure setup notes.

The API stores state in a local JSON file and is intended for a single instance with persistent storage. It is not a multi-replica production database. Production use also requires a real identity provider, authorization policy, secret management, and network controls.

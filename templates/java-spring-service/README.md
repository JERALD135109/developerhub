# ${{values.name}}

Spring Boot service owned by **${{values.owner}}**.

Run `mvn spring-boot:run`; browse http://localhost:8080.
Run `mvn verify` to test service responses and Kubernetes health probes.

CI builds and tests the service, validates Helm/policies, and publishes an image
with its commit SHA. DeveloperHub promotes that SHA into GitOps after CI succeeds.
Use the portal's deployment refresh to verify Argo CD sync, health and endpoint.

PostgreSQL credentials are supplied via External Secrets. Never commit credentials.
See [runbook](docs/runbook.md) and [architecture decision](docs/adr/0001-golden-path.md).

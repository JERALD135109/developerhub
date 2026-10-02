# DeveloperHub portal

Backstage portal for the DeveloperHub platform. Includes the software catalog,
Java/Spring Boot service creation, provisioning history, audit events, and
catalog metadata scorecards.

## Local setup

Use Node.js 24 (or 22) and the bundled Yarn 4 release.

```sh
yarn install --immutable
yarn start
```

Open http://localhost:3000 and sign in as Guest. The backend runs on port 7007.
Start the sibling platform-api Spring Boot application on port 8080 to use service
creation and audit history. Catalog browsing works without the platform API.

The portal calls the platform API through the authenticated Backstage proxy.
Copy `app-config.example.yaml` to ignored `app-config.local.yaml` for API authentication and automatic catalog registration. Override its target when the API is remote:

```yaml
proxy:
  endpoints:
    '/platform-api':
      target: http://localhost:8080
      changeOrigin: true
      credentials: require
integrations:
  github:
    - host: github.com
      token: ${GITHUB_TOKEN}
```

The token is optional for local catalog browsing. Configure the platform API's
GitHub and GitOps credentials separately; portal integration credentials do not
configure that application.

## Service workflow

1. Open Create Java Service. Enter a lowercase service name and owner team.
2. Select dev, staging or prod, database preference, and observability preference.
3. Submit once and inspect the returned status and workflow steps. Production
   requests return PENDING_APPROVAL without creating resources.
4. Open the returned repository and import its catalog-info.yaml through Catalog
   Import. The API automatically registers the catalog when its scoped catalog token is configured; Catalog Import remains available for existing services.
5. Review provisioning outcomes in Deployments and events in Audit. Use Argo CD
   or the catalog Kubernetes tab for actual deployment health.
6. Scorecards show declared ownership, lifecycle, documentation and the optional
   `developerhub.io/slo` annotation. They do not claim to verify CI or live SLOs.

The API persists workflow history and reconciles CI, Terraform and Argo CD. Configure external systems as described in the root README. READY requires passing CI, healthy Argo CD, catalog registration and a working readiness endpoint.

## Validation and build

```sh
yarn tsc
yarn lint:all
yarn test --watch=false
yarn build:all
yarn test:e2e
```

E2E tests start frontend/backend locally and never submit a provisioning request.

For a container, build both frontend and backend on Linux with Node 24, then run
`yarn build-image`. Windows-built native dependencies cannot be used in the Linux
image. Configure app/backend public URLs, PostgreSQL, GitHub authentication and a
production permission policy before deployment. Guest authentication and the
allow-all permission policy are development defaults.

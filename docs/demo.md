# Connected demo

1. Start platform API and portal; select Create Java Service.
2. Request a dev service with Database None for the first connected demo.
3. Show the complete repository and passing CI with an immutable GHCR image.
4. Show Argo CD sync after the API promotes that successful image SHA.
5. Open the verified endpoint, catalog, CI, dashboard and ownership metadata.
6. Run an operational readiness evaluation and show artifact checks versus live CI.
7. Request prod and demonstrate no resource creation before admin approval.
8. Enable configured Terraform/External Secrets, then request PostgreSQL and show
   an isolated private database plus a managed credential reference in GitOps.
9. Dispatch an approved PR preview and remove it after review.
10. Execute approved dev cleanup; retain the source repository and audit record.

Never describe generated configuration as a deployed resource. READY requires
successful CI, Argo CD Synced/Healthy, matching desired image, catalog registration
and a successful readiness endpoint response.

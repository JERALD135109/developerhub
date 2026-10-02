# Local validation

These results are from local checks of the current working tree. They validate builds and generated artifacts; they do not verify a live GitHub, Kubernetes, or AWS deployment.

- Platform API: `mvn verify` passed 12 tests.
- Portal: Yarn install completed; TypeScript compilation and lint passed; 4 unit tests passed.
- Portal backend: production build passed.
- Portal browser checks: 2 E2E tests passed, including the service request form. The tests did not submit a provisioning request.
- Generated service: a service rendered with `scripts/generate.ps1` passed `mvn verify` with 1 test.
- npm registry: HEAD request returned HTTP 200.
- Helm and Terraform: not rerun in this environment because the executables were unavailable.
- Live GitHub, Kubernetes, and AWS provisioning: not performed.

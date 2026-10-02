# Golden path runbook

Owner: ${{values.owner}}. Service: ${{values.name}}.

## Service unavailable

1. Check the portal's deployment request, GitHub CI and Argo CD application.
2. Inspect pod events and logs in the team environment namespace.
3. Verify readiness/liveness probes and resource limits.
4. For PostgreSQL, inspect ExternalSecret readiness and database reachability.
   Never print secret values while debugging.
5. For a bad release, revert the GitOps image tag to a previously verified commit
   SHA through a reviewed pull request. Do not rebuild mutable tags.

## Telemetry

Prometheus metrics are at `/actuator/prometheus`. Grafana imports the dashboard
ConfigMap through its sidecar. Prometheus Operator loads the generated alerts.
OpenTelemetry traces go to the approved OTLP collector. Confirm scrape targets,
alert selection labels and trace ingestion during environment bootstrap.

## Cleanup

Dev cleanup requires a platform administrator. It removes the Argo CD application
and dispatches database destruction when configured. Shared team namespaces and
source repositories are retained. Production cleanup is deliberately blocked.

# Service overview

Owner: ${{values.owner}}. Service: ${{values.name}}.

Health probes: `/actuator/health/liveness` and `/actuator/health/readiness`.
Metrics: `/actuator/prometheus`. The root endpoint reports service identity.

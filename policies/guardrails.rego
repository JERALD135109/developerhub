package guardrails
import rego.v1

containers := input.spec.template.spec.containers

deny contains "privileged containers not allowed" if {
  some c in containers
  c.securityContext.privileged == true
}
deny contains "privilege escalation must be disabled" if {
  some c in containers
  object.get(c.securityContext, "allowPrivilegeEscalation", true) != false
}
deny contains "resource limits required" if {
  some c in containers
  not c.resources.limits
}
deny contains "health endpoints required" if {
  some c in containers
  not c.livenessProbe
}
deny contains msg if {
  some c in containers
  not startswith(c.image, "ghcr.io/")
  msg := sprintf("unsupported registry: %s", [c.image])
}
deny contains "immutable image tag required" if {
  some c in containers
  endswith(c.image, ":latest")
}
deny contains "containers must run as non-root" if {
  input.kind == "Deployment"
  input.spec.template.spec.securityContext.runAsNonRoot != true
}

package guardrails
allowed_registries := ["ghcr.io/company/"]
deny[msg] { c := input.spec.template.spec.containers[_]; c.securityContext.privileged; msg := "privileged containers not allowed" }
deny[msg] { c := input.spec.template.spec.containers[_]; not startswith(c.image, allowed_registries[_]); msg := sprintf("unsupported registry: %v", [c.image]) }
deny[msg] { c := input.spec.template.spec.containers[_]; not c.resources.limits; msg := "resource limits required" }
deny[msg] { c := input.spec.template.spec.containers[_]; not c.livenessProbe; msg := "health endpoint required" }

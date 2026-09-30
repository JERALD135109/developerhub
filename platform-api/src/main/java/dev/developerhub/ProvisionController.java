package dev.developerhub;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.*;

@RestController @RequestMapping("/api")
public class ProvisionController {
  public record ServiceRequest(
    @Pattern(regexp="^[a-z][a-z0-9-]{2,40}$") String name,
    @Pattern(regexp="Java") String language,
    @Pattern(regexp="Spring Boot") String framework,
    @Pattern(regexp="PostgreSQL|None") String database,
    @Pattern(regexp="dev|staging|prod") String environment,
    @NotBlank String owner, boolean observability, String requestedBy) {}
  public record Result(String id, String status, List<String> steps, String repoUrl, String dashboardUrl) {}
  public record AuditEvent(String actor, String action, String resource, String result, Instant timestamp) {}
  private final List<AuditEvent> audit = new ArrayList<>();

  /** Provisioning workflow: 8 steps. Each step is an integration point (Git API, Terraform runner, GitOps repo, catalog). */
  @PostMapping("/provision")
  public Result provision(@Valid @RequestBody ServiceRequest r) {
    List<String> steps = new ArrayList<>();
    steps.add("1 validated name, ownership, resources");
    steps.add("2 repository created from controlled template");           // TODO GitProvider.createFromTemplate
    steps.add("3 committed service metadata + CI workflow");
    if ("PostgreSQL".equals(r.database())) steps.add("4 terraform request created: postgres"); // TODO TerraformRunner
    steps.add("5 gitops values created for " + r.environment());
    steps.add("6 registered in catalog and ownership model");
    steps.add("7 argo cd deploys after first CI image");
    steps.add("8 dashboard + runbook links created");
    String status = "prod".equals(r.environment()) ? "PENDING_APPROVAL" : "IN_PROGRESS"; // prod needs approval
    audit.add(new AuditEvent(r.requestedBy(), "provision", r.name(), status, Instant.now()));
    return new Result(UUID.randomUUID().toString(), status, steps,
      "https://github.com/developerhub-org/" + r.name(), "https://grafana.example.com/d/" + r.name());
  }
  @GetMapping("/audit") public List<AuditEvent> audit() { return audit; }
}

package dev.developerhub;

import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import static dev.developerhub.Models.*;

@RestController
@RequestMapping("/api")
public class ProvisionController {
  private final ProvisioningService workflow;
  private final StateStore store;
  public ProvisionController(ProvisioningService workflow, StateStore store) { this.workflow = workflow; this.store = store; }
  @PostMapping("/provision")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public Provision provision(@Valid @RequestBody ServiceRequest request, @RequestHeader(value="Idempotency-Key", required=false) String key) {
    return workflow.create(request, key);
  }
  @GetMapping("/requests") public List<Provision> requests() { return store.all(); }
  @GetMapping("/requests/{id}") public Provision request(@PathVariable String id) { return store.get(id); }
  @PostMapping("/requests/{id}/refresh") public Provision refresh(@PathVariable String id) { return workflow.refresh(id); }
  @PostMapping("/requests/{id}/approve") public Provision approve(@PathVariable String id, @RequestHeader(value="X-Admin-Token", required=false) String token) { return workflow.approve(id, token); }
  @PostMapping("/requests/{id}/cleanup") public Provision cleanup(@PathVariable String id, @RequestHeader(value="X-Admin-Token", required=false) String token) { return workflow.cleanup(id, token); }
  @PostMapping("/requests/{id}/promote") public Provision promote(@PathVariable String id, @RequestHeader(value="X-Admin-Token", required=false) String token) { return workflow.promote(id, token); }
  @PostMapping("/requests/{id}/retry") public Provision retry(@PathVariable String id, @RequestHeader(value="X-Admin-Token", required=false) String token) { return workflow.retry(id, token); }
  @GetMapping("/services") public List<Provision> services() { return store.all().stream().filter(p -> p.repoUrl() != null).toList(); }
  @GetMapping("/services/{id}/scorecard") public List<Score> scorecard(@PathVariable String id) { return workflow.scorecard(id); }
  @GetMapping("/audit") public List<AuditEvent> audit() { return store.audit(); }
  @ExceptionHandler(GitHubClient.GitHubException.class)
  public ResponseEntity<Map<String,String>> github(GitHubClient.GitHubException e) { return ResponseEntity.status(e.status == 422 ? 409 : e.status).body(Map.of("error", e.getMessage())); }
  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<Map<String,String>> state(IllegalStateException e) { return ResponseEntity.status(503).body(Map.of("error", e.getMessage())); }
}


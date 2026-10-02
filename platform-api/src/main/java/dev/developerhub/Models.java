package dev.developerhub;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class Models {
  private Models() {}
  public record ServiceRequest(
      @NotBlank @Pattern(regexp="^[a-z][a-z0-9-]{2,40}$") String name,
      @NotBlank @Pattern(regexp="Java") String language,
      @NotBlank @Pattern(regexp="Spring Boot") String framework,
      @NotBlank @Pattern(regexp="PostgreSQL|None") String database,
      @NotBlank @Pattern(regexp="dev|staging|prod") String environment,
      @NotBlank @Pattern(regexp="^[a-z][a-z0-9-]{1,30}$") String owner,
      boolean observability, @NotBlank String requestedBy) {}
  public record Provision(String id, ServiceRequest request, String status, List<String> steps,
      String repoUrl, String buildUrl, String buildStatus, String argoUrl, String syncStatus,
      String healthStatus, String serviceUrl, String dashboardUrl, String catalogUrl,
      String imageTag, String error, Instant createdAt, Instant updatedAt) {
    public Provision update(String next, List<String> log, String repo, String build, String buildState,
        String argo, String sync, String health, String endpoint, String dashboard, String catalog,
        String tag, String failure) {
      return new Provision(id, request, next, List.copyOf(log), repo, build, buildState, argo, sync,
          health, endpoint, dashboard, catalog, tag, failure, createdAt, Instant.now());
    }
  }
  public record AuditEvent(String actor, String action, String resource, String result, Instant timestamp) {}
  public record Score(String rule, String status, String details) {}
}

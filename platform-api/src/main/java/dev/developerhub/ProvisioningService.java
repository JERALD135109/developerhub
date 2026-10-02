package dev.developerhub;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;
import static dev.developerhub.Models.*;

@Service
public class ProvisioningService {
  private final StateStore store;
  private final TemplateRenderer renderer;
  private final GitHubClient github;
  private final GitOpsWriter gitops;
  private final PlatformClients clients;
  @Value("${developerhub.admin-token:}") private String adminToken;
  @Value("${developerhub.public-domain}") private String domain;
  @Value("${developerhub.argo-url}") private String argoUrl;
  @Value("${developerhub.grafana-url}") private String grafanaUrl;
  @Value("${developerhub.catalog-url}") private String catalogUrl;
  @Value("${developerhub.gitops-repo}") private String gitopsRepo;
  @Value("${developerhub.terraform-enabled:false}") private boolean terraform;
  public ProvisioningService(StateStore store, TemplateRenderer renderer, GitHubClient github, GitOpsWriter gitops, PlatformClients clients) {
    this.store=store; this.renderer=renderer; this.github=github; this.gitops=gitops; this.clients=clients;
  }
  public synchronized Provision create(ServiceRequest r, String key) {
    String id = key == null || key.isBlank() ? UUID.randomUUID().toString() : key;
    if (!id.matches("[a-zA-Z0-9-]{1,80}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Idempotency-Key");
    Optional<Provision> existing = store.all().stream().filter(p -> p.id().equals(id)).findFirst();
    if (existing.isPresent()) {
      if (!existing.get().request().equals(r)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key belongs to a different request");
      return existing.get();
    }
    if (store.all().stream().anyMatch(p -> p.request().name().equals(r.name()) && p.request().environment().equals(r.environment()) && !p.status().equals("DELETED")))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Service already requested; use its existing request");
    Instant now=Instant.now();
    Provision p = new Provision(id,r,r.environment().equals("prod") ? "PENDING_APPROVAL" : "VALIDATED",List.of("Validated approved service options"),null,null,"PENDING",null,"Unknown","Unknown",null,null,null,null,null,now,now);
    store.save(p,r.requestedBy(),"request");
    return p.status().equals("PENDING_APPROVAL") ? p : execute(p);
  }
  private Provision execute(Provision p) {
    var r=p.request();
    List<String> steps=new ArrayList<>(p.steps());
    try {
      if (r.database().equals("PostgreSQL") && !terraform) throw new IllegalStateException("Configure Terraform workflow and TERRAFORM_ENABLED before requesting PostgreSQL");
      String login=github.login();
      var files=renderer.render(r.name(),r.owner(),r.environment(),r.database(),r.observability());
      files.replaceAll((path,content) -> content.replace("${{values.repoOwner}}",login.toLowerCase()).replace("${{values.domain}}",domain));
      Optional<Provision> known=store.all().stream().filter(other -> other.repoUrl()!=null && other.request().name().equals(r.name())).findFirst();
      if(known.isPresent() && !known.get().request().owner().equals(r.owner())) throw new ResponseStatusException(HttpStatus.CONFLICT,"Service belongs to another team");
      String repo=known.isPresent() ? known.get().repoUrl() : github.createRepoAndPush(r.name(),"DeveloperHub service owned by " + r.owner(),files);
      steps.add("Repository, tests, CI, Helm, runbook and ownership metadata committed");
      p=p.update("REPO_CREATED",steps,repo,null,"PENDING",null,"Unknown","Unknown",null,null,null,null,null);
      store.save(p,r.requestedBy(),"repository-created");
      gitops.write(r.name(),r.owner(),r.environment(),"pending",r.database(),r.observability(),domain);
      steps.add("GitOps application created; deployment waits for the first successful immutable image build");
      String application=r.name()+"-"+r.environment();
      String endpoint=(domain.endsWith("localhost") ? "http://" : "https://")+application+"."+domain;
      String dashboard=r.observability() ? grafanaUrl+"/d/developerhub-service?var-service="+r.name()+"&var-namespace="+r.owner()+"-"+r.environment() : null;
      p=p.update("WAITING_FOR_BUILD",steps,repo,repo+"/actions","PENDING",argoUrl+"/applications/argocd/"+application,"Unknown","Unknown",endpoint,dashboard,null,null,null);
      store.save(p,r.requestedBy(),"gitops-created");
      if(r.database().equals("PostgreSQL")) {
        github.upsertFile(login,gitopsRepo,"infrastructure/"+r.environment()+"/"+r.name()+".json", "{\"service\":\""+r.name()+"\",\"environment\":\""+r.environment()+"\",\"owner\":\""+r.owner()+"\"}","Request PostgreSQL");
        github.call("POST","/repos/"+login+"/"+gitopsRepo+"/actions/workflows/infrastructure.yml/dispatches",Map.of("ref","main","inputs",Map.of("service",r.name(),"environment",r.environment(),"operation","apply")));
        steps.add("Terraform workflow dispatched; environment approval and secret store references are required");
      }
      try {
        clients.register(repo);
        steps.add("Registered catalog location and ownership metadata");
        p=p.update(p.status(),steps,p.repoUrl(),p.buildUrl(),p.buildStatus(),p.argoUrl(),p.syncStatus(),p.healthStatus(),p.serviceUrl(),p.dashboardUrl(),catalogUrl+"/catalog/default/component/"+r.name(),null,null);
      } catch (IllegalStateException e) { steps.add("Catalog registration pending: "+e.getMessage()); }
      p=p.update(p.status(),steps,p.repoUrl(),p.buildUrl(),p.buildStatus(),p.argoUrl(),p.syncStatus(),p.healthStatus(),p.serviceUrl(),p.dashboardUrl(),p.catalogUrl(),null,null);
      store.save(p,r.requestedBy(),"provision");
      return p;
    } catch(RuntimeException e) {
      steps.add("Workflow stopped: "+e.getMessage());
      p=p.update("FAILED",steps,p.repoUrl(),p.buildUrl(),p.buildStatus(),p.argoUrl(),p.syncStatus(),p.healthStatus(),p.serviceUrl(),p.dashboardUrl(),p.catalogUrl(),p.imageTag(),e.getMessage());
      store.save(p,r.requestedBy(),"provision-failed");
      return p;
    }
  }
  public synchronized Provision approve(String id,String token) {
    admin(token);
    Provision p=store.get(id);
    if(!p.status().equals("PENDING_APPROVAL")) throw new ResponseStatusException(HttpStatus.CONFLICT,"Request is not pending approval");
    store.save(p,"platform-admin","approve");
    return execute(p);
  }
  public synchronized Provision retry(String id,String token) {
    admin(token); Provision p=store.get(id);
    if(p.status().equals("CLEANUP_FAILED")) return cleanup(id,token);
    if(!Set.of("FAILED","REPO_CREATED","VALIDATED").contains(p.status())) throw new ResponseStatusException(HttpStatus.CONFLICT,"Only stopped workflows may be retried");
    store.save(p,"platform-admin","retry"); return execute(p);
  }
  public synchronized Provision refresh(String id) {
    Provision p=store.get(id);
    if(p.status().equals("CLEANUP_REQUESTED")) return reconcileCleanup(p);
    if(p.repoUrl()==null || Set.of("DELETED","PENDING_APPROVAL","FAILED","CLEANUP_REQUESTED").contains(p.status())) return p;
    var r=p.request(); List<String> steps=new ArrayList<>(p.steps());
    String login=github.login();
    String pinned=steps.stream().filter(s -> s.startsWith("Approved release SHA: ")).map(s -> s.substring(22)).findFirst().orElse(null);
    JsonNode runs=github.call("GET","/repos/"+login+"/"+r.name()+"/actions/workflows/ci.yml/runs?branch=main&event=push&per_page=1"+(pinned==null ? "" : "&head_sha="+pinned),null).path("workflow_runs");
    if(runs.isEmpty()) return p;
    JsonNode run=runs.get(0);
    String build=run.path("conclusion").isNull() ? run.path("status").asText() : run.path("conclusion").asText();
    String tag=run.path("head_sha").asText();
    String status=build.equals("success") ? "DEPLOYING" : build.equals("failure") || build.equals("cancelled") ? "BUILD_FAILED" : "WAITING_FOR_BUILD";
    String sync="Unknown",health="Unknown",failure=null;
    if(build.equals("success") && r.database().equals("PostgreSQL")) {
      JsonNode infra=github.call("GET","/repos/"+login+"/"+gitopsRepo+"/actions/workflows/infrastructure.yml/runs?event=workflow_dispatch&per_page=50",null).path("workflow_runs");
      JsonNode matching=null;
      for(JsonNode candidate:infra) if(candidate.path("display_title").asText().equals("PostgreSQL "+r.name()+" "+r.environment()+" apply") && workflowStartedAfter(candidate,p.createdAt())) { matching=candidate; break; }
      if(matching==null || !matching.path("conclusion").asText().equals("success")) {
        status=matching!=null && matching.path("conclusion").asText().equals("failure") ? "INFRA_FAILED" : "WAITING_FOR_INFRA";
        p=p.update(status,steps,p.repoUrl(),run.path("html_url").asText(),build,p.argoUrl(),sync,health,p.serviceUrl(),p.dashboardUrl(),p.catalogUrl(),p.imageTag(),"Infrastructure workflow must finish before deployment");
        store.save(p,"platform","refresh"); return p;
      }
    }
    if(build.equals("success")) {
      if(!tag.equals(p.imageTag())) {
        gitops.write(r.name(),r.owner(),r.environment(),tag,r.database(),r.observability(),domain);
        steps.add("Promoted successful CI image "+tag+" into GitOps");
      }
      try {
        JsonNode app=clients.deployment(r.name()+"-"+r.environment());
        sync=app.path("status").path("sync").path("status").asText("Unknown");
        health=app.path("status").path("health").path("status").asText("Unknown");
        boolean imageMatches=app.path("spec").path("source").path("helm").path("valuesObject").path("image").path("tag").asText().equals(tag);
        if(imageMatches && sync.equals("Synced") && health.equals("Healthy") && p.catalogUrl()!=null && clients.healthy(p.serviceUrl())) status="READY";
      } catch(IllegalStateException e) { failure=e.getMessage(); }
      if(p.catalogUrl()==null) {
        try { clients.register(p.repoUrl()); p=p.update(p.status(),steps,p.repoUrl(),p.buildUrl(),p.buildStatus(),p.argoUrl(),p.syncStatus(),p.healthStatus(),p.serviceUrl(),p.dashboardUrl(),catalogUrl+"/catalog/default/component/"+r.name(),p.imageTag(),null); }
        catch(IllegalStateException e) { failure=e.getMessage(); }
      }
    }
    p=p.update(status,steps,p.repoUrl(),run.path("html_url").asText(),build,p.argoUrl(),sync,health,p.serviceUrl(),p.dashboardUrl(),p.catalogUrl(),build.equals("success") ? tag : p.imageTag(),failure);
    store.save(p,"platform","refresh");
    return p;
  }
  public synchronized Provision cleanup(String id,String token) {
    admin(token); Provision p=store.get(id); var r=p.request();
    if(!r.environment().equals("dev")) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Cleanup is limited to dev environments");
    String login=github.login();
    gitops.disable(login,r.name(),r.environment());
    List<String> steps=new ArrayList<>(p.steps()); steps.add("GitOps application removed; Argo CD finalizer removes workloads. Namespace and database cleanup require the infrastructure workflow.");
    if(r.database().equals("PostgreSQL")) {
      try { github.call("POST","/repos/"+login+"/"+gitopsRepo+"/actions/workflows/infrastructure.yml/dispatches",Map.of("ref","main","inputs",Map.of("service",r.name(),"environment",r.environment(),"operation","destroy"))); }
      catch(RuntimeException e) {
        List<String> failed=new ArrayList<>(p.steps()); failed.add("Cleanup dispatch failed; retry after fixing GitHub workflow access");
        Provision stopped=p.update("CLEANUP_FAILED",failed,p.repoUrl(),p.buildUrl(),p.buildStatus(),p.argoUrl(),"Deletion requested","Unknown",p.serviceUrl(),p.dashboardUrl(),p.catalogUrl(),p.imageTag(),e.getMessage());
        store.save(stopped,"platform-admin","cleanup-failed"); return stopped;
      }
    }
    p=p.update("CLEANUP_REQUESTED",steps,p.repoUrl(),p.buildUrl(),p.buildStatus(),p.argoUrl(),"Deleting","Unknown",p.serviceUrl(),p.dashboardUrl(),p.catalogUrl(),p.imageTag(),null);
    store.save(p,"platform-admin","cleanup"); return p;
  }
  public synchronized Provision promote(String id,String token) {
    admin(token); Provision p=store.get(id);
    if(!p.status().equals("READY") || !p.request().environment().equals("staging")) throw new ResponseStatusException(HttpStatus.CONFLICT,"Only a verified staging release can be promoted");
    var r=p.request();
    Optional<Provision> existing=store.all().stream().filter(other -> other.request().name().equals(r.name()) && other.request().environment().equals("prod")).findFirst();
    Provision production;
    if(existing.isPresent()) {
      production=existing.get();
      if(!Set.of("READY","DEPLOYING","WAITING_FOR_BUILD","BUILD_FAILED").contains(production.status())) throw new ResponseStatusException(HttpStatus.CONFLICT,"Production environment must finish its current workflow first");
    } else {
      production=create(new ServiceRequest(r.name(),r.language(),r.framework(),r.database(),"prod",r.owner(),r.observability(),r.requestedBy()),null);
      store.save(production,"platform-admin","approve-promotion");
      production=execute(production);
      if(production.status().equals("FAILED")) return production;
    }
    List<String> steps=new ArrayList<>(production.steps());
    steps.removeIf(step -> step.startsWith("Approved release SHA: "));
    steps.add("Approved release SHA: "+p.imageTag());
    production=production.update("WAITING_FOR_BUILD",steps,production.repoUrl(),production.buildUrl(),production.buildStatus(),production.argoUrl(),production.syncStatus(),production.healthStatus(),production.serviceUrl(),production.dashboardUrl(),production.catalogUrl(),null,null);
    store.save(production,"platform-admin","promote-to-prod"); return production;
  }
  public List<Score> scorecard(String id) {
    Provision p=store.get(id); String login=github.login(); String base="/repos/"+login+"/"+p.request().name()+"/contents/";
    List<Score> scores=new ArrayList<>();
    for(var rule: Map.of("docs","docs/runbook.md","alerts","observability/alerts.yaml","tests","src/test","security",".github/workflows/ci.yml","slo","observability/slo.yaml").entrySet()) {
      try { github.call("GET",base+rule.getValue(),null); scores.add(new Score(rule.getKey(),"DECLARED","Required artifact exists; verify runtime compliance separately")); }
      catch(GitHubClient.GitHubException e) { if(e.status!=404) throw e; scores.add(new Score(rule.getKey(),"MISSING",rule.getValue()+" is missing")); }
    }
    scores.add(new Score("ci",p.buildStatus().equals("success") ? "PASS" : "PENDING",p.buildStatus())); return scores;
  }
  @org.springframework.scheduling.annotation.Scheduled(fixedDelayString="${developerhub.refresh-interval:30000}")
  public void reconcile() {
    for(Provision p:store.all()) {
      if(p.status().equals("CLEANUP_REQUESTED")) { try { reconcileCleanup(p); } catch(RuntimeException e) { org.slf4j.LoggerFactory.getLogger(ProvisioningService.class).warn("Cleanup reconciliation failed for request {}: {}",p.id(),e.getMessage()); } }
      if(Set.of("WAITING_FOR_BUILD","BUILD_FAILED","WAITING_FOR_INFRA","INFRA_FAILED","DEPLOYING").contains(p.status())) {
        try { refresh(p.id()); }
        catch(RuntimeException e) { org.slf4j.LoggerFactory.getLogger(ProvisioningService.class).warn("Reconciliation failed for request {}: {}",p.id(),e.getMessage()); }
      }
    }
  }
  private boolean workflowStartedAfter(JsonNode run,Instant submittedAt) {
    try { return !Instant.parse(run.path("created_at").asText()).plusSeconds(5).isBefore(submittedAt); }
    catch(RuntimeException e) { return false; }
  }
  private Provision reconcileCleanup(Provision p) {
    if(!clients.applicationDeleted(p.request().name()+"-"+p.request().environment())) return p;
    if(p.request().database().equals("PostgreSQL")) {
      String login=github.login();
      JsonNode runs=github.call("GET","/repos/"+login+"/"+gitopsRepo+"/actions/workflows/infrastructure.yml/runs?event=workflow_dispatch&per_page=50",null).path("workflow_runs");
      JsonNode matching=null;
      for(JsonNode run:runs) if(run.path("display_title").asText().equals("PostgreSQL "+p.request().name()+" "+p.request().environment()+" destroy") && workflowStartedAfter(run,p.updatedAt())) { matching=run; break; }
      if(matching==null || matching.path("status").asText().equals("in_progress") || matching.path("status").asText().equals("queued")) return p;
      if(!matching.path("conclusion").asText().equals("success")) {
        List<String> steps=new ArrayList<>(p.steps()); steps.add("Infrastructure cleanup failed; admin retry required");
        p=p.update("CLEANUP_FAILED",steps,p.repoUrl(),p.buildUrl(),p.buildStatus(),p.argoUrl(),"Deleted","Unknown",p.serviceUrl(),p.dashboardUrl(),p.catalogUrl(),p.imageTag(),"Terraform destroy did not succeed");
        store.save(p,"platform","cleanup-failed"); return p;
      }
    }
    List<String> steps=new ArrayList<>(p.steps()); steps.add("Argo CD application and approved infrastructure cleanup verified");
    p=p.update("DELETED",steps,p.repoUrl(),p.buildUrl(),p.buildStatus(),p.argoUrl(),"Deleted","Deleted",p.serviceUrl(),p.dashboardUrl(),p.catalogUrl(),p.imageTag(),null);
    store.save(p,"platform","cleanup-complete"); return p;
  }
  private void admin(String supplied) {
    if(adminToken.isBlank() || supplied==null || !java.security.MessageDigest.isEqual(adminToken.getBytes(java.nio.charset.StandardCharsets.UTF_8),supplied.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN,"A configured platform admin token is required");
  }
}










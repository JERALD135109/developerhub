package dev.developerhub;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import jakarta.validation.Validation;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static dev.developerhub.Models.*;

class ProvisioningServiceTests {
  @TempDir Path temporary;
  StateStore store;
  GitHubClient github;
  ProvisioningService workflow;
  TemplateRenderer renderer;
  GitOpsWriter gitops;
  PlatformClients clients;
  @BeforeEach void setup() throws Exception {
    store=new StateStore(new ObjectMapper().findAndRegisterModules(),temporary.resolve("state.json").toString());
    github=mock(GitHubClient.class); renderer=mock(TemplateRenderer.class);
    gitops=mock(GitOpsWriter.class); clients=mock(PlatformClients.class); workflow=new ProvisioningService(store,renderer,github,gitops,clients);
    ReflectionTestUtils.setField(workflow,"adminToken","test-admin");
    ReflectionTestUtils.setField(workflow,"terraform",false);
  }
  ServiceRequest request(String environment) { return new ServiceRequest("payment-service","Java","Spring Boot","None",environment,"payments",true,"user:default/tester"); }
  @Test void productionHasNoExternalSideEffectsBeforeApproval() {
    Provision p=workflow.create(request("prod"),"test-key");
    assertThat(p.status()).isEqualTo("PENDING_APPROVAL");
    verifyNoInteractions(github,renderer);
    assertThat(store.audit()).hasSize(1);
  }
  @Test void idempotencyReturnsOriginalAndRejectsDifferentRequest() {
    Provision p=workflow.create(request("prod"),"test-key");
    assertThat(workflow.create(request("prod"),"test-key")).isEqualTo(p);
    assertThatThrownBy(() -> workflow.create(request("dev"),"test-key")).hasMessageContaining("different request");
    assertThat(store.all()).hasSize(1);
  }
  @Test void productionApprovalRequiresConfiguredToken() {
    Provision p=workflow.create(request("prod"),null);
    assertThatThrownBy(() -> workflow.approve(p.id(),"wrong")).hasMessageContaining("admin token");
    verifyNoInteractions(github,renderer);
  }
  @Test void stateAndAuditSurviveRestart() throws Exception {
    Provision p=workflow.create(request("prod"),null);
    StateStore reloaded=new StateStore(new ObjectMapper().findAndRegisterModules(),temporary.resolve("state.json").toString());
    assertThat(reloaded.get(p.id())).isEqualTo(p);
    assertThat(reloaded.audit()).hasSize(1);
  }
  @Test void databaseRequestFailsBeforeCreatingAnythingWhenRunnerIsNotConfigured() {
    Provision p=workflow.create(new ServiceRequest("payments-api","Java","Spring Boot","PostgreSQL","dev","payments",true,"tester"),null);
    assertThat(p.status()).isEqualTo("FAILED");
    assertThat(p.error()).contains("TERRAFORM_ENABLED");
    verifyNoInteractions(github,renderer);
  }
  @Test void missingNamesAndPathTraversalAreRejected() {
    try(var factory=Validation.buildDefaultValidatorFactory()) {
      var validator=factory.getValidator();
      assertThat(validator.validate(new ServiceRequest(null,"Java","Spring Boot","None","dev","payments",true,"tester"))).isNotEmpty();
      assertThat(validator.validate(new ServiceRequest("../escape","Java","Spring Boot","None","dev","payments",true,"tester"))).isNotEmpty();
      assertThat(validator.validate(request("prod"))).isEmpty();
    }
  }
  @Test void readyRequiresPassingCiMatchingImageArgoHealthAndEndpoint() throws Exception {
    ReflectionTestUtils.setField(workflow,"domain","example.com");
    var request=request("dev"); var now=java.time.Instant.now();
    Provision pending=new Provision("ci-request",request,"WAITING_FOR_BUILD",java.util.List.of(),"https://github.com/developer/payment-service",null,"pending","http://argo/app","Unknown","Unknown","http://service",null,"http://catalog",null,null,now,now);
    store.save(pending,"tester","request");
    when(github.login()).thenReturn("developer");
    var json=new ObjectMapper();
    when(github.call(eq("GET"),contains("/actions/workflows/ci.yml/runs"),isNull())).thenReturn(json.readTree("{\"workflow_runs\":[{\"conclusion\":\"success\",\"head_sha\":\"abc123\",\"html_url\":\"http://ci\"}]}"));
    when(clients.deployment("payment-service-dev")).thenReturn(json.readTree("{\"spec\":{\"source\":{\"helm\":{\"valuesObject\":{\"image\":{\"tag\":\"abc123\"}}}}},\"status\":{\"sync\":{\"status\":\"Synced\"},\"health\":{\"status\":\"Healthy\"}}}"));
    when(clients.healthy("http://service")).thenReturn(false);
    assertThat(workflow.refresh("ci-request").status()).isEqualTo("DEPLOYING");
    verify(gitops).write("payment-service","payments","dev","abc123","None",true,"example.com");
    when(clients.healthy("http://service")).thenReturn(true);
    assertThat(workflow.refresh("ci-request").status()).isEqualTo("READY");
  }
  @Test void failedCiNeverPromotesImage() throws Exception {
    var now=java.time.Instant.now();
    store.save(new Provision("failed-ci",request("dev"),"WAITING_FOR_BUILD",java.util.List.of(),"http://repo",null,"pending",null,"Unknown","Unknown",null,null,null,null,null,now,now),"tester","request");
    when(github.login()).thenReturn("developer");
    when(github.call(eq("GET"),contains("/actions/workflows/ci.yml/runs"),isNull())).thenReturn(new ObjectMapper().readTree("{\"workflow_runs\":[{\"conclusion\":\"failure\",\"head_sha\":\"bad\"}]}"));
    assertThat(workflow.refresh("failed-ci").status()).isEqualTo("BUILD_FAILED");
    verifyNoInteractions(gitops,clients);
  }
  @Test void manifestsPinCommitAndWaitForInitialBuild() {
    String pending=GitOpsWriter.application("payments-api","payments","dev","developer","pending","None",true,"example.com");
    assertThat(pending).doesNotContain("automated:");
    String ready=GitOpsWriter.application("payments-api","payments","dev","developer","abc123","PostgreSQL",true,"example.com");
    assertThat(ready).contains("automated:","tag: \"abc123\"","enabled: true","resources-finalizer");
  }
}


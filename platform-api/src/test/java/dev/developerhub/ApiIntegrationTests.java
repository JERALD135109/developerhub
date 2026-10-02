package dev.developerhub;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"developerhub.state-file=./target/http-test-state.json","developerhub.api-token=integration-token","developerhub.admin-token=integration-admin","developerhub.refresh-interval=3600000"})
@AutoConfigureMockMvc
class ApiIntegrationTests {
  @Autowired MockMvc http;
  @Test void rejectsMissingApiAuthentication() throws Exception {
    http.perform(get("/api/requests")).andExpect(status().isUnauthorized());
  }
  @Test void validatesBeforeRunningWorkflow() throws Exception {
    http.perform(post("/api/provision").header("X-Platform-Token","integration-token").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"../escape\",\"language\":\"Java\",\"framework\":\"Spring Boot\",\"database\":\"None\",\"environment\":\"dev\",\"owner\":\"payments\",\"requestedBy\":\"tester\"}"))
        .andExpect(status().isBadRequest());
  }
  @Test void productionReturnsDurableApprovalRequestWithoutProvisioning() throws Exception {
    http.perform(post("/api/provision").header("X-Platform-Token","integration-token").header("Idempotency-Key","integration-production").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"approval-service\",\"language\":\"Java\",\"framework\":\"Spring Boot\",\"database\":\"None\",\"environment\":\"prod\",\"owner\":\"payments\",\"requestedBy\":\"tester\"}"))
        .andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("PENDING_APPROVAL")).andExpect(jsonPath("$.repoUrl").isEmpty());
    http.perform(get("/api/requests/integration-production").header("X-Platform-Token","integration-token"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
    http.perform(post("/api/requests/integration-production/approve").header("X-Platform-Token","integration-token").header("X-Admin-Token","wrong"))
        .andExpect(status().isForbidden());
  }
}

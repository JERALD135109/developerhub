package app;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationTests {
  @Autowired TestRestTemplate http;
  @Test void serviceAndHealthAreAvailable() {
    assertThat(http.getForEntity("/", String.class).getBody()).contains("${{values.name}}", "running");
    assertThat(http.getForEntity("/actuator/health/readiness", String.class).getStatusCode().value()).isEqualTo(200);
    assertThat(http.getForEntity("/actuator/health/liveness", String.class).getStatusCode().value()).isEqualTo(200);
  }
}

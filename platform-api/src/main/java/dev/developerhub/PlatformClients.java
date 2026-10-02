package dev.developerhub;

import com.fasterxml.jackson.databind.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;

@Component
public class PlatformClients {
  private final ObjectMapper json;
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  @Value("${developerhub.backend-url}") private String backend;
  @Value("${developerhub.catalog-token:}") private String catalogToken;
  @Value("${developerhub.argo-url}") private String argo;
  public PlatformClients(ObjectMapper json) { this.json = json; }
  public void register(String repository) {
    if (catalogToken.isBlank()) throw new IllegalStateException("BACKSTAGE_CATALOG_TOKEN is required for automatic catalog registration");
    String target=repository+"/blob/main/catalog-info.yaml";
    JsonNode locations=call("GET",backend+"/api/catalog/locations",catalogToken,null);
    for(JsonNode location:locations) if(location.path("data").path("target").asText().equals(target) || location.path("target").asText().equals(target)) return;
    call("POST", backend + "/api/catalog/locations", catalogToken, Map.of("type", "url", "target", target));
  }
  public JsonNode deployment(String application) {
    String token = System.getenv("ARGOCD_TOKEN");
    if (token == null || token.isBlank()) throw new IllegalStateException("ARGOCD_TOKEN is required for deployment verification");
    return call("GET", argo + "/api/v1/applications/" + application, token, null);
  }
  public boolean applicationDeleted(String application) {
    String token = System.getenv("ARGOCD_TOKEN");
    if (token == null || token.isBlank()) throw new IllegalStateException("ARGOCD_TOKEN is required to verify cleanup");
    try {
      HttpRequest request = HttpRequest.newBuilder(URI.create(argo + "/api/v1/applications/" + application))
          .timeout(Duration.ofSeconds(20)).header("Authorization", "Bearer " + token).GET().build();
      int status = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
      if (status == 404) return true;
      if (status >= 300) throw new IllegalStateException("Argo CD cleanup check returned HTTP " + status);
      return false;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Argo CD cleanup check interrupted", e);
    } catch (Exception e) {
      throw new IllegalStateException(e.getMessage(), e);
    }
  }
  public boolean healthy(String endpoint) {
    try { return http.send(HttpRequest.newBuilder(URI.create(endpoint + "/actuator/health/readiness")).timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode() == 200; }
    catch (Exception e) { return false; }
  }
  private JsonNode call(String method, String url, String token, Object body) {
    try {
      HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).header("Authorization", "Bearer " + token);
      if (body == null) b.GET();
      else b.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
      HttpResponse<String> response = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 300) throw new IllegalStateException("Platform integration returned HTTP " + response.statusCode());
      return json.readTree(response.body());
    } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("Integration interrupted", e); }
    catch (Exception e) { throw new IllegalStateException(e.getMessage(), e); }
  }
}


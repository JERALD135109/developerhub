package dev.developerhub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/** Creates a repo on the token owner's account and pushes rendered template files. Token comes only from env GITHUB_TOKEN. */
@Component
public class GitHubClient {
  public static class GitHubException extends RuntimeException {
    public final int status;
    public GitHubException(int status, String msg) { super(msg); this.status = status; }
  }

  private static final String API = "https://api.github.com";
  private final HttpClient http = HttpClient.newHttpClient();
  private final ObjectMapper json = new ObjectMapper();

  private String token() {
    String t = System.getenv("GITHUB_TOKEN");
    if (t == null || t.isBlank()) throw new GitHubException(503, "GITHUB_TOKEN is not set");
    return t;
  }

  private JsonNode call(String method, String path, Object body) {
    try {
      HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(API + path))
          .header("Authorization", "Bearer " + token())
          .header("Accept", "application/vnd.github+json")
          .header("X-GitHub-Api-Version", "2022-11-28");
      if (body == null) b.method(method, HttpRequest.BodyPublishers.noBody());
      else b.header("Content-Type", "application/json")
            .method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
      HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
      JsonNode n = r.body().isBlank() ? json.createObjectNode() : json.readTree(r.body());
      if (r.statusCode() >= 300)
        throw new GitHubException(r.statusCode(), "GitHub " + r.statusCode() + ": " + n.path("message").asText());
      return n;
    } catch (GitHubException e) { throw e; }
    catch (Exception e) { throw new GitHubException(502, "GitHub call failed: " + e.getMessage()); }
  }

  private JsonNode callWithRetry(String method, String path, Object body) {
    for (int i = 0; ; i++) {
      try { return call(method, path, body); }
      catch (GitHubException e) {
        if (e.status != 409 || i >= 3) throw e;   // consecutive commits can briefly conflict
        try { Thread.sleep(700); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); throw e; }
      }
    }
  }

  /** @return html url of the new repository */
  public String createRepoAndPush(String name, String description, Map<String, String> files) {
    JsonNode repo = call("POST", "/user/repos",
        Map.of("name", name, "description", description, "private", true));
    String owner = repo.path("owner").path("login").asText();
    for (Map.Entry<String, String> f : files.entrySet()) {
      String b64 = Base64.getEncoder().encodeToString(f.getValue().getBytes(StandardCharsets.UTF_8));
      callWithRetry("PUT", "/repos/" + owner + "/" + name + "/contents/" + f.getKey(),
          Map.of("message", "Add " + f.getKey() + " (DeveloperHub golden path)", "content", b64));
    }
    return repo.path("html_url").asText();
  }
}

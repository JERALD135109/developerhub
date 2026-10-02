package dev.developerhub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Creates a repo on the token owner's account and pushes rendered template files. Token comes only from env GITHUB_TOKEN. */
@Component
public class GitHubClient {
  public static class GitHubException extends RuntimeException {
    public final int status;
    public GitHubException(int status, String msg) { super(msg); this.status = status; }
  }

  private static final String API = "https://api.github.com";
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(15)).build();
  private final ObjectMapper json = new ObjectMapper();

  private String token() {
    String t = System.getenv("GITHUB_TOKEN");
    if (t == null || t.isBlank()) throw new GitHubException(503, "GITHUB_TOKEN is not set");
    return t;
  }

  public JsonNode call(String method, String path, Object body) {
    try {
      HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(API + path))
          .timeout(java.time.Duration.ofSeconds(30)).header("Authorization", "Bearer " + token())
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

  public String login() { return call("GET", "/user", null).path("login").asText(); }

  public boolean repoExists(String owner, String name) {
    try { call("GET", "/repos/" + owner + "/" + name, null); return true; }
    catch (GitHubException e) { if (e.status == 404) return false; throw e; }
  }

  public JsonNode createRepo(String name, String description, boolean isPrivate) {
    return call("POST", "/user/repos", Map.of("name", name, "description", description, "private", isPrivate, "auto_init", true));
  }

  /** Creates a new file (fails if it already exists). */
  public void putFile(String owner, String repo, String path, String content, String message) {
    String b64 = Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));
    callWithRetry("PUT", "/repos/" + owner + "/" + repo + "/contents/" + path,
        Map.of("message", message, "content", b64));
  }

  /** Creates or updates a file. */
  public void upsertFile(String owner, String repo, String path, String content, String message) {
    String b64 = Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));
    Map<String, Object> body = new HashMap<>();
    body.put("message", message);
    body.put("content", b64);
    try {
      JsonNode existing = call("GET", "/repos/" + owner + "/" + repo + "/contents/" + path, null);
      body.put("sha", existing.path("sha").asText());
    } catch (GitHubException e) { if (e.status != 404) throw e; }
    callWithRetry("PUT", "/repos/" + owner + "/" + repo + "/contents/" + path, body);
  }

  /** @return html url of the new repository */
  public String createRepoAndPush(String name, String description, Map<String, String> files) {
    JsonNode repo = createRepo(name, description, true);
    String owner = repo.path("owner").path("login").asText();
    String branch = repo.path("default_branch").asText("main");
    String base = call("GET", "/repos/" + owner + "/" + name + "/git/ref/heads/" + branch, null).path("object").path("sha").asText();
    List<Map<String,Object>> tree = files.entrySet().stream().map(f -> Map.<String,Object>of("path", f.getKey(), "mode", "100644", "type", "blob", "content", f.getValue())).toList();
    String treeSha = call("POST", "/repos/" + owner + "/" + name + "/git/trees", Map.of("tree", tree)).path("sha").asText();
    String commit = call("POST", "/repos/" + owner + "/" + name + "/git/commits", Map.of("message", "DeveloperHub golden path", "tree", treeSha, "parents", List.of(base))).path("sha").asText();
    if (branch.equals("main")) call("PATCH", "/repos/" + owner + "/" + name + "/git/refs/heads/main", Map.of("sha", commit));
    else {
      call("POST", "/repos/" + owner + "/" + name + "/git/refs", Map.of("ref", "refs/heads/main", "sha", commit));
      call("PATCH", "/repos/" + owner + "/" + name, Map.of("default_branch", "main"));
    }
    return repo.path("html_url").asText();
  }
}



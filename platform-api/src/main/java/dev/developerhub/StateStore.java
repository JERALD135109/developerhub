package dev.developerhub;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.nio.file.*;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import static dev.developerhub.Models.*;

/** Single-instance durable store. Atomic replacement prevents partial JSON writes. */
@Component
public class StateStore {
  public record State(Map<String, Provision> requests, List<AuditEvent> audit) {}
  private final Path file;
  private final ObjectMapper json;
  private State state;
  public StateStore(ObjectMapper json, @Value("${developerhub.state-file:./data/state.json}") String file) throws IOException {
    this.json = json;
    this.file = Path.of(file).toAbsolutePath().normalize();
    state = Files.exists(this.file) ? json.readValue(this.file.toFile(), State.class) : new State(new LinkedHashMap<>(), new ArrayList<>());
  }
  public synchronized List<Provision> all() { return List.copyOf(state.requests().values()); }
  public synchronized Provision get(String id) {
    Provision p = state.requests().get(id);
    if (p == null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Request not found");
    return p;
  }
  public synchronized List<AuditEvent> audit() { return List.copyOf(state.audit()); }
  public synchronized void save(Provision p, String actor, String action) {
    Map<String, Provision> requests = new LinkedHashMap<>(state.requests());
    requests.put(p.id(), p);
    List<AuditEvent> audit = new ArrayList<>(state.audit());
    audit.add(new AuditEvent(actor, action, p.request().name(), p.status(), Instant.now()));
    State next = new State(requests, audit);
    try {
      Files.createDirectories(file.getParent());
      Path temporary = Files.createTempFile(file.getParent(), "state-", ".tmp");
      json.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), next);
      try { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
      catch (AtomicMoveNotSupportedException e) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
      state = next;
    } catch (IOException e) { throw new IllegalStateException("Cannot persist workflow state", e); }
  }
}

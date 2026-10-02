package dev.developerhub;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/** Copies templates/java-spring-service into memory, replacing only ${{values.name}} and ${{values.owner}}. */
@Component
public class TemplateRenderer {
  @Value("${developerhub.template-dir:../templates/java-spring-service}")
  private String templateDir;

  public Map<String, String> render(String name, String owner, String environment, String database, boolean observability) {
    Path root = Path.of(templateDir).toAbsolutePath().normalize();
    if (!Files.isDirectory(root)) throw new IllegalStateException("Template not found: " + root);
    Map<String, String> out = new TreeMap<>();
    try (Stream<Path> s = Files.walk(root)) {
      List<Path> files = s.filter(Files::isRegularFile).toList();
      for (Path p : files) {
        String rel = root.relativize(p).toString().replace('\\', '/');
        if (rel.startsWith("target/")) continue;
        String c = Files.readString(p, StandardCharsets.UTF_8)
            .replace("${{values.name}}", name)
            .replace("${{values.owner}}", owner).replace("${{values.environment}}", environment).replace("${{values.database}}", database).replace("${{values.observability}}", Boolean.toString(observability));
        out.put(rel, c);
      }
    } catch (IOException e) { throw new UncheckedIOException(e); }
    return out;
  }
}


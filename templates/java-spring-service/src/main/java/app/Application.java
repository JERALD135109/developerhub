package app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@SpringBootApplication
@RestController
public class Application {
  public static void main(String[] args) { SpringApplication.run(Application.class, args); }
  @GetMapping("/") public Map<String, String> service() { return Map.of("service", "${{values.name}}", "owner", "${{values.owner}}", "status", "running"); }
}

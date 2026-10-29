package dev.localllm.harness.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the harness CLI.
 */
@SpringBootApplication(scanBasePackages = "dev.localllm.harness")
public class HarnessApplication {

  /**
   * Starts the context, runs the command and exits with its exit code.
   *
   * @param args command-line arguments, parsed by picocli
   */
  public static void main(String[] args) {
    SpringApplication application = new SpringApplication(HarnessApplication.class);
    application.setWebApplicationType(WebApplicationType.NONE);
    application.setAddCommandLineProperties(false);
    System.exit(SpringApplication.exit(application.run(args)));
  }
}

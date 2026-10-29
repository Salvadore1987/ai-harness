package dev.localllm.harness.infrastructure.config;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Shared terminal used by the REPL and by the turn presentation.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(HarnessProperties.class)
public class TerminalConfig {

  /**
   * Opens the system terminal, falling back to a dumb terminal when output is redirected.
   *
   * @return the terminal
   */
  @Bean(destroyMethod = "close")
  public Terminal terminal() {
    try {
      return TerminalBuilder.builder()
          .name("harness")
          .system(true)
          .dumb(true)
          .build();
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot open the terminal", e);
    }
  }
}

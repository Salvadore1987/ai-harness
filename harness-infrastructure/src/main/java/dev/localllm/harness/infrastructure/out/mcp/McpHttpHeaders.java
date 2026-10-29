package dev.localllm.harness.infrastructure.out.mcp;

import java.util.LinkedHashMap;
import java.util.Map;

import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.customizer.McpClientCustomizer;
import org.springframework.stereotype.Component;

import dev.localllm.harness.infrastructure.config.HarnessProperties;

/**
 * Adds the headers of {@code harness.mcp.headers.<server>} to every request of a Streamable
 * HTTP server, typically {@code Authorization} filled from an environment variable.
 *
 * <p>A header with a blank value, such as an unset {@code ${TOKEN:}}, is not sent.
 */
@Component
public class McpHttpHeaders
    implements McpClientCustomizer<HttpClientStreamableHttpTransport.Builder> {

  private static final Logger log = LoggerFactory.getLogger(McpHttpHeaders.class);

  private final Map<String, Map<String, String>> headers;

  /**
   * Creates the customizer.
   *
   * @param properties harness settings with the headers by server name
   */
  public McpHttpHeaders(HarnessProperties properties) {
    this.headers = properties.mcp().headers();
  }

  @Override
  public void customize(String name, HttpClientStreamableHttpTransport.Builder builder) {
    Map<String, String> sent = new LinkedHashMap<>();
    headers.getOrDefault(name, Map.of()).forEach((header, value) -> {
      if (value.isBlank()) {
        log.warn("Header {} of MCP server {} is blank and not sent", header, name);
      } else {
        sent.put(header, value);
      }
    });
    if (!sent.isEmpty()) {
      builder.addHttpRequestCustomizer((request, method, endpoint, body, context) ->
          sent.forEach(request::header));
    }
  }
}

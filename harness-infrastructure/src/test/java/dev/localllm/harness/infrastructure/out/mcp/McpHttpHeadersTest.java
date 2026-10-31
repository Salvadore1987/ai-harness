package dev.localllm.harness.infrastructure.out.mcp;

import java.nio.file.Path;
import java.util.Map;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.localllm.harness.infrastructure.TestProperties;
import dev.localllm.harness.infrastructure.config.HarnessProperties;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Headers of {@code harness.mcp.headers} on the requests of a Streamable HTTP server.
 */
class McpHttpHeadersTest {

  @RegisterExtension
  static WireMockExtension server = WireMockExtension.newInstance()
      .options(wireMockConfig().dynamicPort())
      .build();

  @Test
  void customize_ConfiguredServer_SendsNonBlankHeaders() {
    // Arrange
    server.stubFor(post("/mcp").willReturn(serverError()));
    McpSyncClient client = client("remote", Map.of("remote",
        Map.of("Authorization", "Bearer secret", "X-Empty", "")));

    // Act & Assert
    assertThatThrownBy(client::initialize).isInstanceOf(RuntimeException.class);
    server.verify(postRequestedFor(urlEqualTo("/mcp"))
        .withHeader("Authorization", equalTo("Bearer secret"))
        .withHeader("X-Empty", absent()));
    client.close();
  }

  @Test
  void customize_OtherServer_SendsNoHeaders() {
    // Arrange
    server.stubFor(post("/mcp").willReturn(serverError()));
    McpSyncClient client = client("remote", Map.of("github",
        Map.of("Authorization", "Bearer secret")));

    // Act & Assert
    assertThatThrownBy(client::initialize).isInstanceOf(RuntimeException.class);
    server.verify(postRequestedFor(urlEqualTo("/mcp"))
        .withHeader("Authorization", absent()));
    client.close();
  }

  private static McpSyncClient client(String name, Map<String, Map<String, String>> headers) {
    HarnessProperties defaults =
        TestProperties.create(Path.of("build/harness-home"), false);
    HarnessProperties properties = new HarnessProperties(defaults.model(), defaults.home(),
        defaults.thinking(), defaults.context(), defaults.agent(), defaults.tools(),
        new HarnessProperties.Mcp(headers));
    HttpClientStreamableHttpTransport.Builder builder =
        HttpClientStreamableHttpTransport.builder(server.baseUrl())
            .endpoint("/mcp")
            .jsonMapper(McpTestSupport.json());
    new McpHttpHeaders(properties).customize(name, builder);
    return McpTestSupport.client(name, builder.build());
  }
}

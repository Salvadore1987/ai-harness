package dev.localllm.harness.infrastructure.out.tools;

import java.nio.file.Path;
import java.util.List;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.mapstruct.factory.Mappers;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import dev.localllm.harness.application.model.ToolCall;
import dev.localllm.harness.application.model.ToolResult;
import dev.localllm.harness.infrastructure.TestProperties;
import dev.localllm.harness.infrastructure.config.HarnessProperties;
import dev.localllm.harness.infrastructure.out.mcp.McpTestSupport;
import dev.localllm.harness.infrastructure.out.tools.mapper.ToolSpecSpringAiMapper;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.temporaryRedirect;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code http_fetch} called through the registry against a local HTTP server.
 */
class HttpToolsTest {

  @RegisterExtension
  static WireMockExtension server = WireMockExtension.newInstance()
      .options(wireMockConfig().dynamicPort())
      .build();

  @TempDir
  private Path cwd;

  private SpringAiToolExecutorAdapter executor;

  @BeforeEach
  void setUp() {
    HarnessProperties properties =
        TestProperties.create(cwd.resolve("home"), "http://localhost:11434", false);
    executor = new SpringAiToolExecutorAdapter(
        List.of(MethodToolCallbackProvider.builder()
            .toolObjects(new HttpTools(properties)).build()),
        McpTestSupport.noServers(), Mappers.getMapper(ToolSpecSpringAiMapper.class), properties);
  }

  @Test
  void httpFetch_HtmlPage_ReturnsSimplifiedText() {
    // Arrange
    server.stubFor(get("/docs").willReturn(aResponse()
        .withHeader("Content-Type", "text/html; charset=utf-8")
        .withBody("""
            <!DOCTYPE html><html><head><title>Guide &amp; Notes</title>
            <style>body { color: red; }</style><script>var x = "<p>";</script></head>
            <body><!-- hidden --><h1>Install</h1><p>Run <code>gradle&nbsp;build</code>.</p>
            <ul><li>first</li><li>second &lt;tag&gt; &#8212; &#x2713;</li></ul>
            <table><tr><td>a</td><td>b</td></tr></table></body></html>
            """)));

    // Act
    ToolResult result = fetch(server.baseUrl() + "/docs");

    // Assert
    assertThat(result.error()).isFalse();
    assertThat(result.output()).isEqualTo("""
        HTTP 200 text/html;charset=utf-8

        # Guide & Notes

        # Install

        Run gradle build.

        - first
        - second <tag> — ✓

        a | b |
        """);
  }

  @Test
  void httpFetch_Json_ReturnsBodyAsIs() {
    // Arrange
    server.stubFor(get("/api").willReturn(okJson("{\"version\":\"1.2\"}")));

    // Act
    ToolResult result = fetch(server.baseUrl() + "/api");

    // Assert
    assertThat(result.output()).isEqualTo("HTTP 200 application/json\n\n{\"version\":\"1.2\"}");
  }

  @Test
  void httpFetch_Redirect_FollowsIt() {
    // Arrange
    server.stubFor(get("/old").willReturn(temporaryRedirect("/api")));
    server.stubFor(get("/api").willReturn(okJson("{}")));

    // Act
    ToolResult result = fetch(server.baseUrl() + "/old");

    // Assert
    assertThat(result.output()).startsWith("HTTP 200");
  }

  @Test
  void httpFetch_NotFound_ReturnsErrorWithBody() {
    // Arrange
    server.stubFor(get("/missing").willReturn(aResponse().withStatus(404)
        .withHeader("Content-Type", "text/plain").withBody("no such page")));

    // Act
    ToolResult result = fetch(server.baseUrl() + "/missing");

    // Assert
    assertThat(result.error()).isTrue();
    assertThat(result.output())
        .isEqualTo("Error: HTTP 404 from " + server.baseUrl() + "/missing\nno such page");
  }

  @Test
  void httpFetch_Image_ReturnsError() {
    // Arrange
    server.stubFor(get("/logo.png").willReturn(aResponse()
        .withHeader("Content-Type", "image/png").withBody(new byte[] {1, 2, 3})));

    // Act
    ToolResult result = fetch(server.baseUrl() + "/logo.png");

    // Assert
    assertThat(result.error()).isTrue();
    assertThat(result.output()).isEqualTo(
        "Error: HTTP 200: binary content image/png is not supported");
  }

  @Test
  void httpFetch_FileUrl_IsRefused() {
    // Act
    ToolResult result = fetch("file:///etc/passwd");

    // Assert
    assertThat(result.error()).isTrue();
    assertThat(result.output()).isEqualTo(
        "Error: Only absolute http and https URLs are supported: file:///etc/passwd");
  }

  private ToolResult fetch(String url) {
    return executor.execute(
        new ToolCall("c1", "http_fetch", "{\"url\":\"" + url + "\"}"), cwd);
  }
}

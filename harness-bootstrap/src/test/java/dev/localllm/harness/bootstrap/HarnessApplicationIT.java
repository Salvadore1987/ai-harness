package dev.localllm.harness.bootstrap;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import picocli.CommandLine;

import dev.localllm.harness.infrastructure.in.cli.HarnessCommand;
import dev.localllm.harness.infrastructure.in.cli.handlers.PicocliExceptionHandler;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts the whole application with the real {@code application.yml} against a stubbed Ollama.
 */
@SpringBootTest(
    classes = HarnessApplication.class,
    properties = {"harness.cli.enabled=false", "harness.home=build/test-harness-home"})
class HarnessApplicationIT {

  @RegisterExtension
  static WireMockExtension ollama = WireMockExtension.newInstance()
      .options(wireMockConfig().dynamicPort())
      .build();

  @Autowired
  private HarnessCommand command;

  @Autowired
  private PicocliExceptionHandler exceptionHandler;

  @DynamicPropertySource
  static void ollamaUrl(DynamicPropertyRegistry registry) {
    registry.add("harness.ollama-base-url", ollama::baseUrl);
  }

  @Test
  void printMode_WithStubbedOllama_AnswersAndSendsConfiguredOptions() {
    // Arrange
    ollama.stubFor(post("/api/show").willReturn(okJson(
        "{\"details\":{\"parameter_size\":\"27B\"},"
            + "\"capabilities\":[\"completion\",\"thinking\"]}")));
    ollama.stubFor(post("/api/chat").willReturn(aResponse()
        .withHeader("Content-Type", "application/x-ndjson")
        .withBody("{\"model\":\"qwen3.5:27b\","
            + "\"message\":{\"role\":\"assistant\",\"content\":\"hi\"},"
            + "\"done\":true,\"prompt_eval_count\":5,\"eval_count\":1}\n")));

    // Act
    int exitCode = new CommandLine(command)
        .setExecutionExceptionHandler(exceptionHandler)
        .execute("-p", "say hi");

    // Assert
    assertThat(exitCode).isZero();
    ollama.verify(postRequestedFor(urlEqualTo("/api/chat"))
        .withRequestBody(matchingJsonPath("$.model", equalTo("qwen3.5:27b")))
        .withRequestBody(matchingJsonPath("$.think", equalTo("true")))
        .withRequestBody(matchingJsonPath("$.keep_alive", equalTo("30m")))
        .withRequestBody(matchingJsonPath("$.options.num_ctx", equalTo("65536"))));
  }
}

package dev.localllm.harness.infrastructure.out.ollama;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.localllm.harness.application.exception.LlmUnavailableException;
import dev.localllm.harness.application.exception.ModelNotFoundException;
import dev.localllm.harness.application.model.ChatChunk;
import dev.localllm.harness.application.model.ChatCompletion;
import dev.localllm.harness.application.model.ChatMessage;
import dev.localllm.harness.application.model.ChatRequest;
import dev.localllm.harness.application.model.Role;
import dev.localllm.harness.application.model.TokenUsage;
import dev.localllm.harness.application.model.TurnStatus;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OllamaChatAdapterIT {

  @RegisterExtension
  static WireMockExtension ollama = WireMockExtension.newInstance()
      .options(wireMockConfig().dynamicPort())
      .build();

  private static final String CHUNK = "{\"model\":\"qwen3.5:27b\","
      + "\"created_at\":\"2026-10-29T10:00:00Z\",\"message\":{\"role\":\"assistant\",%s},%s}\n";

  private static final String STREAM = thinking("Hmm") + content("Hel") + content("lo")
      + String.format(CHUNK, "\"content\":\"\"",
          "\"done\":true,\"done_reason\":\"stop\",\"prompt_eval_count\":12,\"eval_count\":7");

  @Test
  void stream_WithThinkingAndContent_EmitsChunksInOrderAndReportsUsage() {
    // Arrange
    stubChat(STREAM);
    OllamaChatAdapter adapter = OllamaTestSupport.chatAdapter(ollama.baseUrl());
    List<ChatChunk> chunks = new ArrayList<>();

    // Act
    ChatCompletion completion = adapter.stream(request(true), chunks::add);

    // Assert
    assertThat(chunks).containsExactly(
        new ChatChunk.Thinking("Hmm"),
        new ChatChunk.Content("Hel"),
        new ChatChunk.Content("lo"));
    assertThat(completion.content()).isEqualTo("Hello");
    assertThat(completion.status()).isEqualTo(TurnStatus.COMPLETED);
    assertThat(completion.usage()).isEqualTo(new TokenUsage(12, 7));
  }

  @Test
  void stream_WithThinkingEnabled_SendsThinkFlagAndKeepsDefaultOptions() {
    // Arrange
    stubChat(STREAM);
    OllamaChatAdapter adapter = OllamaTestSupport.chatAdapter(ollama.baseUrl());

    // Act
    adapter.stream(request(true), chunk -> { });

    // Assert
    ollama.verify(postRequestedFor(urlEqualTo("/api/chat"))
        .withRequestBody(matchingJsonPath("$.model", equalTo(OllamaTestSupport.MODEL)))
        .withRequestBody(matchingJsonPath("$.think", equalTo("true")))
        .withRequestBody(matchingJsonPath("$.stream", equalTo("true")))
        .withRequestBody(matchingJsonPath("$.keep_alive", equalTo(OllamaTestSupport.KEEP_ALIVE)))
        .withRequestBody(matchingJsonPath(
            "$.options.num_ctx", equalTo(String.valueOf(OllamaTestSupport.NUM_CTX))))
        .withRequestBody(matchingJsonPath("$.messages[0].content", equalTo("hi"))));
  }

  @Test
  void stream_WithThinkingDisabled_SendsThinkFalse() {
    // Arrange
    stubChat(STREAM);
    OllamaChatAdapter adapter = OllamaTestSupport.chatAdapter(ollama.baseUrl());

    // Act
    adapter.stream(request(false), chunk -> { });

    // Assert
    ollama.verify(postRequestedFor(urlEqualTo("/api/chat"))
        .withRequestBody(matchingJsonPath("$.think", equalTo("false"))));
  }

  @Test
  void stream_WhenModelMissing_ThrowsModelNotFound() {
    // Arrange
    ollama.stubFor(post("/api/chat").willReturn(aResponse()
        .withStatus(404)
        .withHeader("Content-Type", "application/json")
        .withBody("{\"error\":\"model 'qwen3.5:27b' not found\"}")));
    OllamaChatAdapter adapter = OllamaTestSupport.chatAdapter(ollama.baseUrl());

    // Act & Assert
    assertThatThrownBy(() -> adapter.stream(request(true), chunk -> { }))
        .isInstanceOf(ModelNotFoundException.class)
        .hasMessageContaining(OllamaTestSupport.MODEL);
  }

  @Test
  void stream_WhenServerDown_ThrowsLlmUnavailable() {
    // Arrange
    OllamaChatAdapter adapter = OllamaTestSupport.chatAdapter("http://localhost:1");

    // Act & Assert
    assertThatThrownBy(() -> adapter.stream(request(true), chunk -> { }))
        .isInstanceOf(LlmUnavailableException.class)
        .hasMessageContaining("http://localhost:1");
  }

  @Test
  void stream_WhenBrokenAfterPartialAnswer_ReturnsInterruptedWithPartialContent()
      throws IOException {
    // Arrange: WireMock delivers a body in one piece, so a JDK server flushes line by line like
    // Ollama does. OllamaApi releases a chunk only when the next one arrives, so the chunk right
    // before the break is lost.
    HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext("/api/chat", exchange -> {
      exchange.getResponseHeaders().add("Content-Type", "application/x-ndjson");
      exchange.sendResponseHeaders(200, 0);
      try (OutputStream body = exchange.getResponseBody()) {
        for (String line : List.of(content("Hel"), content("lo"), "{\"model\": broken\n")) {
          body.write(line.getBytes(StandardCharsets.UTF_8));
          body.flush();
          sleep(150);
        }
      }
    });
    server.start();
    OllamaChatAdapter adapter = OllamaTestSupport.chatAdapter(
        "http://localhost:" + server.getAddress().getPort());

    // Act
    ChatCompletion completion;
    try {
      completion = adapter.stream(request(true), chunk -> { });
    } finally {
      server.stop(0);
    }

    // Assert
    assertThat(completion.status()).isEqualTo(TurnStatus.INTERRUPTED);
    assertThat(completion.content()).isNotEmpty();
    assertThat("Hello").startsWith(completion.content());
  }

  @Test
  void cancelActive_DuringSlowResponse_EndsTurnAsCancelledQuickly() throws Exception {
    // Arrange
    ollama.stubFor(post("/api/chat").willReturn(aResponse()
        .withHeader("Content-Type", "application/x-ndjson")
        .withBody(STREAM)
        .withFixedDelay(5_000)));
    OllamaChatAdapter adapter = OllamaTestSupport.chatAdapter(ollama.baseUrl());
    CompletableFuture<ChatCompletion> turn =
        CompletableFuture.supplyAsync(() -> adapter.stream(request(true), chunk -> { }));
    TimeUnit.MILLISECONDS.sleep(300);

    // Act
    adapter.cancelActive();

    // Assert
    ChatCompletion completion = turn.get(2, TimeUnit.SECONDS);
    assertThat(completion.status()).isEqualTo(TurnStatus.CANCELLED);
    assertThat(completion.elapsed()).isLessThan(Duration.ofSeconds(2));
  }

  private static String content(String text) {
    return String.format(CHUNK, "\"content\":\"" + text + "\"", "\"done\":false");
  }

  private static String thinking(String text) {
    return String.format(CHUNK, "\"content\":\"\",\"thinking\":\"" + text + "\"", "\"done\":false");
  }

  private static void sleep(long millis) {
    try {
      TimeUnit.MILLISECONDS.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private static void stubChat(String ndjson) {
    ollama.stubFor(post("/api/chat").willReturn(aResponse()
        .withHeader("Content-Type", "application/x-ndjson")
        .withBody(ndjson)));
  }

  private static ChatRequest request(boolean thinking) {
    return new ChatRequest(
        OllamaTestSupport.MODEL, List.of(new ChatMessage(Role.USER, "hi")), thinking);
  }
}

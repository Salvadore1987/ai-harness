package dev.localllm.harness.adapter.ollama;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.localllm.harness.adapter.ollama.config.OllamaProperties.ToolCallModeType;
import dev.localllm.harness.adapter.ollama.fixture.OllamaTestProperties;
import dev.localllm.harness.adapter.ollama.fixture.OllamaTestSupport;
import dev.localllm.harness.application.exception.LlmUnavailableException;
import dev.localllm.harness.application.exception.ModelNotFoundException;
import dev.localllm.harness.application.model.ChatChunk;
import dev.localllm.harness.application.model.ChatCompletion;
import dev.localllm.harness.application.model.ChatMessage;
import dev.localllm.harness.application.model.ChatRequest;
import dev.localllm.harness.application.model.Role;
import dev.localllm.harness.application.model.TokenUsage;
import dev.localllm.harness.application.model.ToolCall;
import dev.localllm.harness.application.model.ToolResult;
import dev.localllm.harness.application.model.ToolSource;
import dev.localllm.harness.application.model.ToolSpec;
import dev.localllm.harness.application.model.TurnStatus;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
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
            "$.options.num_ctx", equalTo(String.valueOf(OllamaTestProperties.NUM_CTX))))
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
  void stream_WithToolCallInStream_ReturnsToolCallWithoutExecutingIt() {
    // Arrange
    stubChat(thinking("need the file")
        + String.format(CHUNK, "\"content\":\"\",\"tool_calls\":[{\"id\":\"call_1\","
            + "\"function\":{\"index\":0,\"name\":\"read_file\","
            + "\"arguments\":{\"path\":\"Calc.java\"}}}]", "\"done\":false")
        + String.format(CHUNK, "\"content\":\"\"",
            "\"done\":true,\"done_reason\":\"stop\",\"prompt_eval_count\":30,"
                + "\"eval_count\":9"));
    OllamaChatAdapter adapter = OllamaTestSupport.chatAdapter(ollama.baseUrl());
    List<ChatChunk> chunks = new ArrayList<>();

    // Act
    ChatCompletion completion = adapter.stream(toolRequest(List.of()), chunks::add);

    // Assert
    assertThat(completion.toolCalls()).containsExactly(
        new ToolCall("call_1", "read_file", "{\"path\":\"Calc.java\"}"));
    assertThat(completion.status()).isEqualTo(TurnStatus.COMPLETED);
    assertThat(completion.usage()).isEqualTo(new TokenUsage(30, 9));
    assertThat(chunks).containsExactly(new ChatChunk.Thinking("need the file"));
  }

  @Test
  void stream_WithToolCallWithoutId_GeneratesId() {
    // Arrange
    stubChat(String.format(CHUNK, "\"content\":\"\",\"tool_calls\":[{\"function\":"
            + "{\"name\":\"list_dir\",\"arguments\":{}}}]", "\"done\":false")
        + String.format(CHUNK, "\"content\":\"\"", "\"done\":true,\"done_reason\":\"stop\""));
    OllamaChatAdapter adapter = OllamaTestSupport.chatAdapter(ollama.baseUrl());

    // Act
    ChatCompletion completion = adapter.stream(toolRequest(List.of()), chunk -> { });

    // Assert
    assertThat(completion.toolCalls()).singleElement().satisfies(call -> {
      assertThat(call.id()).isNotBlank();
      assertThat(call.name()).isEqualTo("list_dir");
      assertThat(call.argumentsJson()).isEqualTo("{}");
    });
  }

  @Test
  void stream_WithToolsAndToolHistory_SendsDefinitionsCallsAndResults() {
    // Arrange
    stubChat(STREAM);
    OllamaChatAdapter adapter = OllamaTestSupport.chatAdapter(ollama.baseUrl());
    ToolCall call = new ToolCall("call_1", "read_file", "{\"path\":\"Calc.java\"}");
    List<ChatMessage> history = List.of(
        ChatMessage.assistant("", List.of(call)),
        ChatMessage.toolResult(new ToolResult("call_1", "read_file", "1\tclass Calc {}", false,
            false, Duration.ZERO)));

    // Act
    adapter.stream(toolRequest(history), chunk -> { });

    // Assert
    ollama.verify(postRequestedFor(urlEqualTo("/api/chat"))
        .withRequestBody(matchingJsonPath("$.tools[0].type", equalTo("function")))
        .withRequestBody(matchingJsonPath("$.tools[0].function.name", equalTo("read_file")))
        .withRequestBody(matchingJsonPath(
            "$.tools[0].function.parameters.required[0]", equalTo("path")))
        .withRequestBody(matchingJsonPath("$.messages[1].role", equalTo("assistant")))
        .withRequestBody(matchingJsonPath(
            "$.messages[1].tool_calls[0].function.name", equalTo("read_file")))
        .withRequestBody(matchingJsonPath(
            "$.messages[1].tool_calls[0].function.arguments.path", equalTo("Calc.java")))
        .withRequestBody(matchingJsonPath("$.messages[2].role", equalTo("tool")))
        .withRequestBody(matchingJsonPath("$.messages[2].content", equalTo("1\tclass Calc {}"))));
  }

  @Test
  void stream_InTextJsonMode_SendsToolsInPromptAndHistoryAsText() {
    // Arrange
    stubChat(STREAM);
    OllamaChatAdapter adapter = textJsonAdapter();
    ToolCall call = new ToolCall("call_1", "read_file", "{\"path\":\"Calc.java\"}");
    List<ChatMessage> history = List.of(
        ChatMessage.assistant("", List.of(call)),
        ChatMessage.toolResult(new ToolResult("call_1", "read_file", "1\tclass Calc {}", false,
            false, Duration.ZERO)));

    // Act
    adapter.stream(toolRequest(history), chunk -> { });

    // Assert
    ollama.verify(postRequestedFor(urlEqualTo("/api/chat"))
        .withRequestBody(containing("\"tools\":[]"))
        .withRequestBody(matchingJsonPath("$.messages[0].role", equalTo("system")))
        .withRequestBody(matchingJsonPath("$.messages[0].content", containing("- read_file:")))
        .withRequestBody(matchingJsonPath("$.messages[2].role", equalTo("assistant")))
        .withRequestBody(matchingJsonPath("$.messages[2].content", equalTo(
            "```tool\n{\"name\":\"read_file\",\"args\":{\"path\":\"Calc.java\"}}\n```")))
        .withRequestBody(matchingJsonPath("$.messages[3].role", equalTo("user")))
        .withRequestBody(matchingJsonPath("$.messages[3].content",
            equalTo("TOOL_RESULT read_file:\n1\tclass Calc {}"))));
  }

  @Test
  void stream_InTextJsonMode_HidesBlockReturnsCallAndStopsAfterIt() {
    // Arrange
    stubChat(content("Reading.\\n``") + content("`tool\\n{\\\"name\\\": \\\"read_file\\\", ")
        + content("\\\"args\\\": {\\\"path\\\": \\\"Calc.java\\\"}}\\n```")
        + content("\\nTOOL_RESULT read_file: invented")
        + String.format(CHUNK, "\"content\":\"\"",
            "\"done\":true,\"done_reason\":\"stop\",\"prompt_eval_count\":12,\"eval_count\":7"));
    OllamaChatAdapter adapter = textJsonAdapter();
    List<ChatChunk> chunks = new ArrayList<>();

    // Act
    ChatCompletion completion = adapter.stream(toolRequest(List.of()), chunks::add);

    // Assert
    assertThat(chunks).containsExactly(new ChatChunk.Content("Reading.\n"));
    assertThat(completion.status()).isEqualTo(TurnStatus.COMPLETED);
    assertThat(completion.content()).isEqualTo("Reading.");
    assertThat(completion.toolCalls()).singleElement().satisfies(call -> {
      assertThat(call.name()).isEqualTo("read_file");
      assertThat(call.argumentsJson()).isEqualTo("{\"path\":\"Calc.java\"}");
    });
    assertThat(completion.toolCallError()).isNull();
    // the stream is dropped right after the block, before the final chunk with the counters
    assertThat(completion.usage()).isEqualTo(TokenUsage.EMPTY);
  }

  @Test
  void stream_InTextJsonMode_WithInvalidBlock_ReturnsErrorAndRawContent() {
    // Arrange
    stubChat(content("```tool\\n{\\\"name\\\": \\\"rm\\\"}\\n```")
        + String.format(CHUNK, "\"content\":\"\"", "\"done\":true"));
    OllamaChatAdapter adapter = textJsonAdapter();

    // Act
    ChatCompletion completion = adapter.stream(toolRequest(List.of()), chunk -> { });

    // Assert
    assertThat(completion.toolCalls()).isEmpty();
    assertThat(completion.content()).isEqualTo("```tool\n{\"name\": \"rm\"}\n```");
    assertThat(completion.toolCallError())
        .isEqualTo("unknown tool rm; available tools: read_file");
  }

  @Test
  void stream_WhenServerEndsWithoutAnyToken_AsksOnceMore() {
    // Arrange
    ollama.stubFor(post("/api/chat").inScenario("empty").whenScenarioStateIs(STARTED)
        .willReturn(aResponse().withHeader("Content-Type", "application/x-ndjson")
            .withBody(String.format(CHUNK, "\"content\":\"\"",
                "\"done\":true,\"done_reason\":\"stop\",\"prompt_eval_count\":904")))
        .willSetStateTo("retried"));
    ollama.stubFor(post("/api/chat").inScenario("empty").whenScenarioStateIs("retried")
        .willReturn(aResponse().withHeader("Content-Type", "application/x-ndjson")
            .withBody(STREAM)));
    OllamaChatAdapter adapter = OllamaTestSupport.chatAdapter(ollama.baseUrl());

    // Act
    ChatCompletion completion = adapter.stream(request(false), chunk -> { });

    // Assert
    assertThat(completion.content()).isEqualTo("Hello");
    ollama.verify(2, postRequestedFor(urlEqualTo("/api/chat")));
  }

  @Test
  void stream_WithoutTools_SendsEmptyToolList() {
    // Arrange
    stubChat(STREAM);
    OllamaChatAdapter adapter = OllamaTestSupport.chatAdapter(ollama.baseUrl());

    // Act
    adapter.stream(request(false), chunk -> { });

    // Assert
    ollama.verify(postRequestedFor(urlEqualTo("/api/chat"))
        .withRequestBody(containing("\"tools\":[]")));
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

  private static OllamaChatAdapter textJsonAdapter() {
    return OllamaTestSupport.chatAdapter(ollama.baseUrl(),
        Map.of("qwen3.5", ToolCallModeType.TEXT_JSON));
  }

  private static void stubChat(String ndjson) {
    ollama.stubFor(post("/api/chat").willReturn(aResponse()
        .withHeader("Content-Type", "application/x-ndjson")
        .withBody(ndjson)));
  }

  private static ChatRequest toolRequest(List<ChatMessage> history) {
    List<ChatMessage> messages = new ArrayList<>();
    messages.add(new ChatMessage(Role.USER, "what does add do?"));
    messages.addAll(history);
    return new ChatRequest(OllamaTestSupport.MODEL, messages, true, List.of(new ToolSpec(
        "read_file", "Read a file",
        "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"}},"
            + "\"required\":[\"path\"]}",
        ToolSource.BUILTIN, null)));
  }

  private static ChatRequest request(boolean thinking) {
    return new ChatRequest(
        OllamaTestSupport.MODEL, List.of(new ChatMessage(Role.USER, "hi")), thinking);
  }
}

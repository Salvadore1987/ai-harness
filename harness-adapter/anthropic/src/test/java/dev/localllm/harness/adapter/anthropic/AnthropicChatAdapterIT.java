package dev.localllm.harness.adapter.anthropic;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mapstruct.factory.Mappers;

import dev.localllm.harness.adapter.anthropic.config.AnthropicProperties;
import dev.localllm.harness.adapter.anthropic.mapper.ChatMessageAnthropicMapper;
import dev.localllm.harness.adapter.anthropic.mapper.ToolSpecAnthropicMapper;
import dev.localllm.harness.adapter.anthropic.mapper.UsageAnthropicMapper;
import dev.localllm.harness.adapter.anthropic.toolcall.ToolInputValidator;
import dev.localllm.harness.application.exception.LlmResponseException;
import dev.localllm.harness.application.model.ChatChunk;
import dev.localllm.harness.application.model.ChatCompletion;
import dev.localllm.harness.application.model.ChatMessage;
import dev.localllm.harness.application.model.ChatRequest;
import dev.localllm.harness.application.model.LlmProvider;
import dev.localllm.harness.application.model.Role;
import dev.localllm.harness.application.model.TokenUsage;
import dev.localllm.harness.application.model.ToolCall;
import dev.localllm.harness.application.model.ToolSource;
import dev.localllm.harness.application.model.ToolSpec;
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

class AnthropicChatAdapterIT {

  @RegisterExtension
  static WireMockExtension claude = WireMockExtension.newInstance()
      .options(wireMockConfig().dynamicPort())
      .build();

  private static final String MODEL = "claude-opus-5-5";

  private static final ToolSpec READ_FILE = new ToolSpec("read_file", "Reads a file",
      "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"}},"
          + "\"required\":[\"path\"],\"additionalProperties\":false}",
      ToolSource.BUILTIN, null);

  private static final String MESSAGE_START = event("message_start",
      "{\"type\":\"message_start\",\"message\":{\"id\":\"msg_1\",\"type\":\"message\","
          + "\"role\":\"assistant\",\"model\":\"" + MODEL + "\",\"content\":[],"
          + "\"stop_reason\":null,\"stop_sequence\":null,"
          + "\"usage\":{\"input_tokens\":12,\"output_tokens\":1,"
          + "\"cache_read_input_tokens\":3}}}");

  private static final String THINKING_BLOCK = blockStart(0,
      "{\"type\":\"thinking\",\"thinking\":\"\",\"signature\":\"\"}")
      + delta(0, "{\"type\":\"thinking_delta\",\"thinking\":\"Hmm\"}")
      + delta(0, "{\"type\":\"signature_delta\",\"signature\":\"c2ln\"}")
      + blockStop(0);

  private static final String TEXT_BLOCK = blockStart(1, "{\"type\":\"text\",\"text\":\"\"}")
      + delta(1, "{\"type\":\"text_delta\",\"text\":\"Hel\"}")
      + delta(1, "{\"type\":\"text_delta\",\"text\":\"lo\"}")
      + blockStop(1);

  private static final String ANSWER = MESSAGE_START + THINKING_BLOCK + TEXT_BLOCK
      + messageDelta("{\"stop_reason\":\"end_turn\",\"stop_sequence\":null}", 7) + messageStop();

  @Test
  void stream_WithThinkingAndText_EmitsChunksInOrderAndReportsUsage() {
    // Arrange
    stubMessages(ANSWER);
    List<ChatChunk> chunks = new ArrayList<>();

    // Act
    ChatCompletion completion = adapter().stream(request(true), chunks::add);

    // Assert
    assertThat(chunks).containsExactly(
        new ChatChunk.Thinking("Hmm"),
        new ChatChunk.Content("Hel"),
        new ChatChunk.Content("lo"));
    assertThat(completion.content()).isEqualTo("Hello");
    assertThat(completion.status()).isEqualTo(TurnStatus.COMPLETED);
    assertThat(completion.usage()).isEqualTo(new TokenUsage(15, 7));
    assertThat(completion.toolCalls()).isEmpty();
  }

  @Test
  void stream_WithThinkingEnabled_SendsSummarizedAdaptiveThinkingEffortAndSystem() {
    // Arrange
    stubMessages(ANSWER);

    // Act
    adapter().stream(request(true), chunk -> { });

    // Assert
    claude.verify(postRequestedFor(urlEqualTo("/v1/messages"))
        .withRequestBody(matchingJsonPath("$.model", equalTo(MODEL)))
        .withRequestBody(matchingJsonPath("$.stream", equalTo("true")))
        .withRequestBody(matchingJsonPath("$.max_tokens", equalTo("64000")))
        .withRequestBody(matchingJsonPath("$.thinking.type", equalTo("adaptive")))
        .withRequestBody(matchingJsonPath("$.thinking.display", equalTo("summarized")))
        .withRequestBody(matchingJsonPath("$.output_config.effort", equalTo("high")))
        .withRequestBody(matchingJsonPath("$.system", equalTo("Be brief")))
        .withRequestBody(matchingJsonPath("$.messages[0].role", equalTo("user")))
        .withRequestBody(matchingJsonPath("$.messages[0].content[0].text", equalTo("hi"))));
  }

  @Test
  void stream_WithThinkingDisabled_OmitsTheReasoningButKeepsAdaptiveThinking() {
    // Arrange
    stubMessages(ANSWER);

    // Act
    adapter().stream(request(false), chunk -> { });

    // Assert
    claude.verify(postRequestedFor(urlEqualTo("/v1/messages"))
        .withRequestBody(matchingJsonPath("$.thinking.type", equalTo("adaptive")))
        .withRequestBody(matchingJsonPath("$.thinking.display", equalTo("omitted"))));
  }

  @Test
  void stream_WithToolUse_ReturnsCallAndOffersToolsWithEagerInputStreaming() {
    // Arrange
    stubMessages(MESSAGE_START + TEXT_BLOCK + toolUse(2, "toolu_01", "{\\\"path\\\":",
        "\\\"Calc.java\\\"}") + messageDelta("{\"stop_reason\":\"tool_use\"}", 20)
        + messageStop());

    // Act
    ChatCompletion completion = adapter().stream(toolRequest(), chunk -> { });

    // Assert
    assertThat(completion.status()).isEqualTo(TurnStatus.COMPLETED);
    assertThat(completion.toolCalls())
        .containsExactly(new ToolCall("toolu_01", "read_file", "{\"path\":\"Calc.java\"}"));
    assertThat(completion.toolCallError()).isNull();
    claude.verify(postRequestedFor(urlEqualTo("/v1/messages"))
        .withRequestBody(matchingJsonPath("$.tools[0].name", equalTo("read_file")))
        .withRequestBody(matchingJsonPath("$.tools[0].eager_input_streaming", equalTo("true")))
        .withRequestBody(matchingJsonPath(
            "$.tools[0].input_schema.properties.path.type", equalTo("string")))
        .withRequestBody(matchingJsonPath(
            "$.tools[0].input_schema.required[0]", equalTo("path")))
        .withRequestBody(matchingJsonPath(
            "$.tools[0].input_schema.additionalProperties", equalTo("false"))));
  }

  @Test
  void stream_WithToolInputMissingRequiredProperty_ReturnsErrorInsteadOfCall() {
    // Arrange
    stubMessages(MESSAGE_START + toolUse(0, "toolu_01", "{\\\"file\\\":\\\"a\\\"}")
        + messageDelta("{\"stop_reason\":\"tool_use\"}", 5) + messageStop());

    // Act
    ChatCompletion completion = adapter().stream(toolRequest(), chunk -> { });

    // Assert
    assertThat(completion.toolCalls()).isEmpty();
    assertThat(completion.toolCallError())
        .isEqualTo("the input of tool read_file is invalid: "
            + "required property \"path\" is missing");
  }

  @Test
  void stream_WithToolInputThatIsNotJson_ReturnsErrorInsteadOfCall() {
    // Arrange
    stubMessages(MESSAGE_START + toolUse(0, "toolu_01", "{\\\"path\\\": Calc")
        + messageDelta("{\"stop_reason\":\"tool_use\"}", 5) + messageStop());

    // Act
    ChatCompletion completion = adapter().stream(toolRequest(), chunk -> { });

    // Assert
    assertThat(completion.toolCalls()).isEmpty();
    assertThat(completion.toolCallError()).startsWith("the input of tool read_file is invalid: "
        + "not valid JSON");
  }

  @Test
  void stream_WhenMaxTokensCutsToolInput_EndsInterruptedWithoutCalls() {
    // Arrange
    stubMessages(MESSAGE_START + TEXT_BLOCK + toolUse(2, "toolu_01", "{\\\"path\\\":\\\"Ca")
        + messageDelta("{\"stop_reason\":\"max_tokens\"}", 64000) + messageStop());

    // Act
    ChatCompletion completion = adapter().stream(toolRequest(), chunk -> { });

    // Assert
    assertThat(completion.status()).isEqualTo(TurnStatus.INTERRUPTED);
    assertThat(completion.content()).isEqualTo("Hello");
    assertThat(completion.toolCalls()).isEmpty();
  }

  @Test
  void stream_WithRefusal_ThrowsWithCategoryAndExplanation() {
    // Arrange
    stubMessages(MESSAGE_START + TEXT_BLOCK + messageDelta("{\"stop_reason\":\"refusal\","
        + "\"stop_details\":{\"type\":\"refusal\",\"category\":\"cyber\","
        + "\"explanation\":\"Not allowed\"}}", 3) + messageStop());

    // Act & Assert
    assertThatThrownBy(() -> adapter().stream(request(true), chunk -> { }))
        .isInstanceOf(LlmResponseException.class)
        .hasMessage("Claude declined the request (category: cyber): Not allowed");
  }

  @Test
  void stream_WhenStreamEndsWithoutStopReason_EndsInterrupted() {
    // Arrange
    stubMessages(MESSAGE_START + TEXT_BLOCK);

    // Act
    ChatCompletion completion = adapter().stream(request(true), chunk -> { });

    // Assert
    assertThat(completion.status()).isEqualTo(TurnStatus.INTERRUPTED);
    assertThat(completion.content()).isEqualTo("Hello");
  }

  @Test
  void stream_WhenApiAnswersWithError_ThrowsLlmResponseExceptionWithoutTheKey() {
    // Arrange
    claude.stubFor(post("/v1/messages").willReturn(aResponse()
        .withStatus(401)
        .withHeader("Content-Type", "application/json")
        .withBody("{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\","
            + "\"message\":\"invalid x-api-key\"}}")));

    // Act & Assert
    assertThatThrownBy(() -> adapter().stream(request(true), chunk -> { }))
        .isInstanceOf(LlmResponseException.class)
        .hasMessageContaining("invalid x-api-key")
        .hasMessageNotContaining("test-key");
  }

  @Test
  void cancelActive_WhileWaitingForTheResponse_EndsTurnAsCancelledQuickly() throws Exception {
    // Arrange
    claude.stubFor(post("/v1/messages").willReturn(aResponse()
        .withHeader("Content-Type", "text/event-stream")
        .withBody(ANSWER)
        .withFixedDelay(5_000)));
    AnthropicChatAdapter adapter = adapter();
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

  private static AnthropicChatAdapter adapter() {
    return new AnthropicChatAdapter(
        AnthropicOkHttpClient.builder()
            .apiKey("test-key")
            .baseUrl(claude.baseUrl())
            .maxRetries(0)
            .build(),
        new AnthropicProperties("test-key", claude.baseUrl(), MODEL, 64_000,
            AnthropicProperties.Effort.HIGH),
        Mappers.getMapper(ChatMessageAnthropicMapper.class),
        Mappers.getMapper(ToolSpecAnthropicMapper.class),
        Mappers.getMapper(UsageAnthropicMapper.class),
        new ToolInputValidator(),
        new AnthropicErrorTranslator());
  }

  private static ChatRequest request(boolean thinking) {
    return new ChatRequest(LlmProvider.ANTHROPIC, MODEL,
        List.of(new ChatMessage(Role.SYSTEM, "Be brief"), new ChatMessage(Role.USER, "hi")),
        thinking);
  }

  private static ChatRequest toolRequest() {
    return new ChatRequest(LlmProvider.ANTHROPIC, MODEL,
        List.of(new ChatMessage(Role.USER, "what does Calc do?")), true, List.of(READ_FILE));
  }

  private static void stubMessages(String sse) {
    claude.stubFor(post("/v1/messages").willReturn(aResponse()
        .withHeader("Content-Type", "text/event-stream")
        .withBody(sse)));
  }

  private static String toolUse(int index, String id, String... partialJson) {
    StringBuilder events = new StringBuilder(blockStart(index,
        "{\"type\":\"tool_use\",\"id\":\"" + id + "\",\"name\":\"read_file\",\"input\":{}}"));
    for (String fragment : partialJson) {
      events.append(delta(index,
          "{\"type\":\"input_json_delta\",\"partial_json\":\"" + fragment + "\"}"));
    }
    return events.append(blockStop(index)).toString();
  }

  private static String blockStart(int index, String contentBlock) {
    return event("content_block_start", "{\"type\":\"content_block_start\",\"index\":" + index
        + ",\"content_block\":" + contentBlock + "}");
  }

  private static String delta(int index, String delta) {
    return event("content_block_delta", "{\"type\":\"content_block_delta\",\"index\":" + index
        + ",\"delta\":" + delta + "}");
  }

  private static String blockStop(int index) {
    return event("content_block_stop",
        "{\"type\":\"content_block_stop\",\"index\":" + index + "}");
  }

  private static String messageDelta(String delta, int outputTokens) {
    return event("message_delta", "{\"type\":\"message_delta\",\"delta\":" + delta
        + ",\"usage\":{\"output_tokens\":" + outputTokens + "}}");
  }

  private static String messageStop() {
    return event("message_stop", "{\"type\":\"message_stop\"}");
  }

  private static String event(String name, String data) {
    return "event: " + name + "\ndata: " + data + "\n\n";
  }
}

package dev.localllm.harness.infrastructure.out.memory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mapstruct.factory.Mappers;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import tools.jackson.databind.json.JsonMapper;

import dev.localllm.harness.application.model.ChatMessage;
import dev.localllm.harness.application.model.Role;
import dev.localllm.harness.application.model.SessionId;
import dev.localllm.harness.application.model.SessionInfo;
import dev.localllm.harness.application.model.ToolCall;
import dev.localllm.harness.application.model.ToolResult;
import dev.localllm.harness.infrastructure.TestProperties;
import dev.localllm.harness.infrastructure.config.HarnessProperties;
import dev.localllm.harness.infrastructure.out.memory.mapper.ChatMessageMemoryMapper;
import dev.localllm.harness.infrastructure.out.memory.mapper.JsonlLineMapper;

import static org.assertj.core.api.Assertions.assertThat;

class JsonlSessionStorageTest {

  private static final Instant NOW = Instant.parse("2026-10-29T10:00:00Z");

  @TempDir
  private Path home;

  private final SessionId sessionId = SessionId.newId();
  private JsonlChatMemoryRepository repository;
  private TokenWindowChatMemory memory;

  @BeforeEach
  void setUp() {
    repository = repository(false);
    memory = new TokenWindowChatMemory(repository);
  }

  @Test
  void saveAll_WithGrowingHistory_AppendsOnlyNewLines() throws IOException {
    // Arrange
    repository.saveAll(id(), List.of(new UserMessage("q1"), new AssistantMessage("a1")));

    // Act
    repository.saveAll(id(), List.of(new UserMessage("q1"), new AssistantMessage("a1"),
        new UserMessage("q2")));

    // Assert
    assertThat(fileLines()).hasSize(3);
    assertThat(repository.findByConversationId(id())).extracting(Message::getText)
        .containsExactly("q1", "a1", "q2");
  }

  @Test
  void saveAll_WithDifferentHistory_RewritesFileKeepingMetadata() throws IOException {
    // Arrange
    repository.writeMeta(info());
    repository.saveAll(id(), List.of(new UserMessage("q1"), new AssistantMessage("a1")));

    // Act
    repository.saveAll(id(), List.of(new UserMessage("other")));

    // Assert
    assertThat(fileLines()).hasSize(2).first().asString().contains("\"type\":\"meta\"");
    assertThat(repository.findByConversationId(id())).extracting(Message::getText)
        .containsExactly("other");
  }

  @Test
  void findByConversationId_WithCorruptedLine_SkipsIt() throws IOException {
    // Arrange
    repository.saveAll(id(), List.of(new UserMessage("q1")));
    Files.writeString(file(), "{not json\n", StandardCharsets.UTF_8,
        StandardOpenOption.APPEND);
    repository.saveAll(id(), List.of(new UserMessage("q1"), new AssistantMessage("a1")));

    // Act
    List<Message> messages = repository.findByConversationId(id());

    // Assert
    assertThat(messages).extracting(Message::getText).containsExactly("q1", "a1");
  }

  @Test
  void readSession_AfterTurns_ReturnsMetadataWithMessageCount() {
    // Arrange
    repository.writeMeta(info());
    memory.add(id(), List.of(new UserMessage("q1"), new AssistantMessage("a1")));

    // Act & Assert
    assertThat(repository.readSession(id())).contains(
        new SessionInfo(sessionId, Path.of("/work/project"), "qwen3.5:27b", "Explain", NOW, 2));
  }

  @Test
  void get_AfterCompaction_ReturnsSummaryAndKeptMessages() {
    // Arrange
    memory.add(id(), List.of(new UserMessage("q1"), new AssistantMessage("a1"),
        new UserMessage("q2"), new AssistantMessage("a2")));

    // Act
    memory.compact(id(), "summary", 2);
    memory.add(id(), List.of(new UserMessage("q3")));

    // Assert
    assertThat(memory.get(id())).extracting(Message::getText)
        .containsExactly("summary", "q2", "a2", "q3");
    assertThat(repository.findByConversationId(id())).hasSize(5);
  }

  @Test
  void compact_WithoutSummary_DropsOlderMessagesAndNeverRestoresThem() {
    // Arrange
    memory.add(id(), List.of(new UserMessage("q1"), new AssistantMessage("a1"),
        new UserMessage("q2"), new AssistantMessage("a2")));
    memory.compact(id(), "", 2);

    // Act
    memory.compact(id(), "", 4);

    // Assert
    assertThat(memory.get(id())).extracting(Message::getText).containsExactly("q2", "a2");
  }

  @Test
  void conversationAdapter_RoundTripsCoreMessagesAndPersistsThinkingOnlyWhenEnabled()
      throws IOException {
    // Arrange
    JsonlChatMemoryRepository persisting = repository(true);
    ChatMemoryConversationAdapter adapter = new ChatMemoryConversationAdapter(
        new TokenWindowChatMemory(persisting), persisting,
        Mappers.getMapper(ChatMessageMemoryMapper.class),
        TestProperties.create(home, "http://localhost:11434", true));

    // Act
    adapter.append(sessionId, new ChatMessage(Role.USER, "hi"));
    adapter.append(sessionId, new ChatMessage(Role.ASSISTANT, "hello"));
    adapter.recordThinking(sessionId, "reasoning");

    // Assert
    assertThat(adapter.history(sessionId)).containsExactly(
        new ChatMessage(Role.USER, "hi"), new ChatMessage(Role.ASSISTANT, "hello"));
    assertThat(fileLines()).anyMatch(line -> line.contains("\"type\":\"thinking\""));
  }

  @Test
  void conversationAdapter_WithToolCallsAndResults_StoresAndRestoresThem() throws IOException {
    // Arrange
    ChatMemoryConversationAdapter adapter = adapter();
    ToolCall call = new ToolCall("call_1", "read_file", "{\"path\":\"Calc.java\"}");
    ChatMessage assistant = ChatMessage.assistant("Let me read it", List.of(call));
    ChatMessage result = ChatMessage.toolResult(new ToolResult("call_1", "read_file",
        "1\tclass Calc {}", false, false, Duration.ZERO));

    // Act
    adapter.append(sessionId, new ChatMessage(Role.USER, "what is Calc?"));
    adapter.append(sessionId, assistant);
    adapter.append(sessionId, result);

    // Assert
    assertThat(adapter().history(sessionId)).containsExactly(
        new ChatMessage(Role.USER, "what is Calc?"), assistant, result);
    assertThat(fileLines()).hasSize(3);
    assertThat(fileLines().get(1)).contains(
        "\"toolCalls\":[{\"id\":\"call_1\",\"name\":\"read_file\","
            + "\"argsJson\":\"{\\\"path\\\":\\\"Calc.java\\\"}\"}]");
    assertThat(fileLines().get(2)).contains("\"role\":\"TOOL_RESULT\"",
        "\"callId\":\"call_1\"", "\"toolName\":\"read_file\"");
  }

  @Test
  void saveAll_WithDifferentToolResults_DetectsChangedPrefix() throws IOException {
    // Arrange
    ChatMemoryConversationAdapter adapter = adapter();
    adapter.append(sessionId, ChatMessage.toolResult(
        new ToolResult("c1", "glob", "first", false, false, Duration.ZERO)));
    ChatMessageMemoryMapper mapper = Mappers.getMapper(ChatMessageMemoryMapper.class);

    // Act
    repository.saveAll(id(), List.of(mapper.toSpringAiMessage(ChatMessage.toolResult(
        new ToolResult("c1", "glob", "second", false, false, Duration.ZERO)))));

    // Assert
    assertThat(adapter.history(sessionId)).extracting(ChatMessage::content)
        .containsExactly("second");
    assertThat(fileLines()).hasSize(1);
  }

  @Test
  void catalog_ListsSessionsOfDirectoryNewestFirst() {
    // Arrange
    JsonlSessionCatalogAdapter catalog = new JsonlSessionCatalogAdapter(repository);
    SessionId newer = SessionId.newId();
    catalog.register(info());
    catalog.register(new SessionInfo(newer, Path.of("/work/project"), "qwen3.5:27b", "Newer",
        NOW.plusSeconds(60), 0));
    catalog.register(new SessionInfo(SessionId.newId(), Path.of("/work/other"), "qwen3.5:27b",
        "Elsewhere", NOW, 0));

    // Act
    List<SessionInfo> sessions = catalog.list(Path.of("/work/project"));

    // Assert
    assertThat(sessions).extracting(SessionInfo::title).containsExactly("Newer", "Explain");
    assertThat(catalog.find(newer)).isPresent();
  }

  private ChatMemoryConversationAdapter adapter() {
    return new ChatMemoryConversationAdapter(memory, repository,
        Mappers.getMapper(ChatMessageMemoryMapper.class),
        TestProperties.create(home, "http://localhost:11434", false));
  }

  private JsonlChatMemoryRepository repository(boolean persistThinking) {
    HarnessProperties properties =
        TestProperties.create(home, "http://localhost:11434", persistThinking);
    return new JsonlChatMemoryRepository(properties, JsonMapper.builder().build(),
        Mappers.getMapper(JsonlLineMapper.class), Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private SessionInfo info() {
    return new SessionInfo(sessionId, Path.of("/work/project"), "qwen3.5:27b", "Explain", NOW, 0);
  }

  private String id() {
    return sessionId.toString();
  }

  private Path file() {
    return home.resolve("sessions").resolve(id() + ".jsonl");
  }

  private List<String> fileLines() throws IOException {
    return Files.readAllLines(file(), StandardCharsets.UTF_8);
  }
}

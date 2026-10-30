package dev.localllm.harness.application.service;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import dev.localllm.harness.application.dto.command.SaveAnswerCommand;
import dev.localllm.harness.application.dto.result.SavedAnswerResult;
import dev.localllm.harness.application.exception.AnswerNotFoundException;
import dev.localllm.harness.application.fixture.InMemoryAnswerFilePort;
import dev.localllm.harness.application.fixture.InMemoryConversationPort;
import dev.localllm.harness.application.mapper.TurnApplicationMapper;
import dev.localllm.harness.application.model.ChatMessage;
import dev.localllm.harness.application.model.Role;
import dev.localllm.harness.application.model.SessionId;
import dev.localllm.harness.application.model.ToolCall;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SaveAnswerServiceTest {

  private static final Path CWD = Path.of("/work/project");
  private static final Instant NOW = Instant.parse("2026-10-30T12:34:56Z");
  private static final String ANSWER = "# CAP\n\nСогласованность, доступность, устойчивость.\n";

  private final InMemoryConversationPort conversation = new InMemoryConversationPort();
  private final InMemoryAnswerFilePort files = new InMemoryAnswerFilePort();
  private final SaveAnswerService service = new SaveAnswerService(conversation, files,
      Clock.fixed(NOW, ZoneOffset.UTC), Mappers.getMapper(TurnApplicationMapper.class));
  private final SessionId sessionId = SessionId.newId();

  @Test
  void execute_RelativePath_WritesLatestAnswerVerbatim() {
    // Arrange
    conversation.append(sessionId, new ChatMessage(Role.USER, "Что такое CAP?"));
    conversation.append(sessionId, new ChatMessage(Role.ASSISTANT, "старый ответ"));
    conversation.append(sessionId, new ChatMessage(Role.USER, "Подробнее"));
    conversation.append(sessionId, new ChatMessage(Role.ASSISTANT, ANSWER));

    // Act
    SavedAnswerResult result = service.execute(command("docs/cap.md", false));

    // Assert
    assertThat(result.file()).isEqualTo(Path.of("/work/project/docs/cap.md"));
    assertThat(result.characters()).isEqualTo(ANSWER.length());
    assertThat(files.files()).containsExactlyEntriesOf(
        Map.of(Path.of("/work/project/docs/cap.md"), ANSWER));
  }

  @Test
  void execute_AgentTurn_SavesFinalAnswerNotTheRemarkBeforeToolCall() {
    // Arrange
    conversation.append(sessionId, new ChatMessage(Role.USER, "Что делает Calc?"));
    conversation.append(sessionId, ChatMessage.assistant("Посмотрю файл",
        List.of(new ToolCall("c1", "read_file", "{}"))));
    conversation.append(sessionId, new ChatMessage(Role.TOOL_RESULT, "class Calc {}"));
    conversation.append(sessionId, ChatMessage.assistant(ANSWER, List.of()));

    // Act
    service.execute(command("calc.md", false));

    // Assert
    assertThat(files.files()).containsEntry(Path.of("/work/project/calc.md"), ANSWER);
  }

  @Test
  void execute_SkipCurrentTurn_SavesAnswerBeforeTheSaveRequest() {
    // Arrange
    conversation.append(sessionId, new ChatMessage(Role.USER, "Что такое CAP?"));
    conversation.append(sessionId, new ChatMessage(Role.ASSISTANT, ANSWER));
    conversation.append(sessionId, new ChatMessage(Role.USER, "Сохрани этот текст в cap.md"));
    conversation.append(sessionId, ChatMessage.assistant("Сохраняю",
        List.of(new ToolCall("c2", "save_answer", "{\"path\":\"cap.md\"}"))));

    // Act
    service.execute(command("cap.md", true));

    // Assert
    assertThat(files.files()).containsEntry(Path.of("/work/project/cap.md"), ANSWER);
  }

  @Test
  void execute_AfterEarlierSave_SkipsTheConfirmationAndSavesTheSameText() {
    // Arrange
    conversation.append(sessionId, new ChatMessage(Role.USER, "Что такое CAP?"));
    conversation.append(sessionId, new ChatMessage(Role.ASSISTANT, ANSWER));
    conversation.append(sessionId, new ChatMessage(Role.USER, "Сохрани в cap.md"));
    conversation.append(sessionId, ChatMessage.assistant("",
        List.of(new ToolCall("c1", SaveAnswerService.TOOL_NAME, "{}"))));
    conversation.append(sessionId, new ChatMessage(Role.TOOL_RESULT, "Saved"));
    conversation.append(sessionId, ChatMessage.assistant("Сохранено в cap.md", List.of()));

    // Act
    service.execute(command("copy.md", false));

    // Assert
    assertThat(files.files()).containsEntry(Path.of("/work/project/copy.md"), ANSWER);
  }

  @Test
  void execute_EmptyPath_GeneratesTimestampedNameInWorkingDirectory() {
    // Arrange
    conversation.append(sessionId, new ChatMessage(Role.USER, "Привет"));
    conversation.append(sessionId, new ChatMessage(Role.ASSISTANT, ANSWER));
    String stamp = LocalDateTime.ofInstant(NOW, ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));

    // Act
    SavedAnswerResult result = service.execute(command("", false));

    // Assert
    assertThat(result.file()).isEqualTo(CWD.resolve("answer-" + stamp + ".md"));
  }

  @Test
  void execute_ExistingDirectory_GeneratesNameInsideIt() {
    // Arrange
    files.withDirectory(Path.of("/work/project/notes"));
    conversation.append(sessionId, new ChatMessage(Role.USER, "Привет"));
    conversation.append(sessionId, new ChatMessage(Role.ASSISTANT, ANSWER));

    // Act
    SavedAnswerResult result = service.execute(command("notes", false));

    // Assert
    assertThat(result.file().getParent()).isEqualTo(Path.of("/work/project/notes"));
    assertThat(result.file().getFileName().toString()).startsWith("answer-").endsWith(".md");
  }

  @Test
  void execute_NoAnswerYet_ThrowsAnswerNotFound() {
    // Arrange
    conversation.append(sessionId, new ChatMessage(Role.USER, "Сохрани ответ"));

    // Act / Assert
    assertThatThrownBy(() -> service.execute(command("a.md", true)))
        .isInstanceOf(AnswerNotFoundException.class);
    assertThat(files.files()).isEmpty();
  }

  private SaveAnswerCommand command(String path, boolean skipCurrentTurn) {
    return new SaveAnswerCommand(sessionId, CWD, path, skipCurrentTurn);
  }
}

package dev.localllm.harness.application.service;

import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import dev.localllm.harness.application.dto.command.SaveAnswerCommand;
import dev.localllm.harness.application.dto.result.SavedAnswerResult;
import dev.localllm.harness.application.exception.AnswerNotFoundException;
import dev.localllm.harness.application.mapper.TurnApplicationMapper;
import dev.localllm.harness.application.model.ChatMessage;
import dev.localllm.harness.application.model.Role;
import dev.localllm.harness.application.port.in.SaveAnswerUseCase;
import dev.localllm.harness.application.port.out.AnswerFilePort;
import dev.localllm.harness.application.port.out.ConversationPort;

/**
 * Copies the latest answer from the stored history into a file, so the model never has to
 * repeat it.
 *
 * <p>The answer is the last assistant message with text; in an agent turn that is the final
 * answer, not the remarks before tool calls. Turns that called {@link #TOOL_NAME} are skipped:
 * their answer only confirms the save, so saving again copies the same text as before. Without
 * a file name, or with a directory, the file
 * is named {@code answer-<date>-<time>.md} in local time.
 */
public class SaveAnswerService implements SaveAnswerUseCase {

  private static final DateTimeFormatter FILE_TIMESTAMP =
      DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT).withZone(ZoneId.systemDefault());

  private final ConversationPort conversationPort;
  private final AnswerFilePort answerFilePort;
  private final Clock clock;
  private final TurnApplicationMapper mapper;

  /**
   * Creates the service.
   *
   * @param conversationPort conversation history storage
   * @param answerFilePort writes the file
   * @param clock source of the generated file name
   * @param mapper converts the outcome into the result
   */
  public SaveAnswerService(ConversationPort conversationPort, AnswerFilePort answerFilePort,
      Clock clock, TurnApplicationMapper mapper) {
    this.conversationPort = conversationPort;
    this.answerFilePort = answerFilePort;
    this.clock = clock;
    this.mapper = mapper;
  }

  @Override
  public SavedAnswerResult execute(SaveAnswerCommand command) {
    String answer = latestAnswer(command);
    Path file = target(command);
    answerFilePort.write(file, answer);
    return mapper.toSavedAnswerResult(file, answer.length());
  }

  private String latestAnswer(SaveAnswerCommand command) {
    List<ChatMessage> history = conversationPort.history(command.sessionId());
    int turnEnd = command.skipCurrentTurn()
        ? lastUserMessage(history, history.size()) : history.size();
    while (turnEnd > 0) {
      int turnStart = lastUserMessage(history, turnEnd);
      List<ChatMessage> turn = history.subList(turnStart, turnEnd);
      Optional<String> answer = savesAnswer(turn) ? Optional.empty() : answer(turn);
      if (answer.isPresent()) {
        return answer.get();
      }
      turnEnd = turnStart;
    }
    throw new AnswerNotFoundException();
  }

  private static int lastUserMessage(List<ChatMessage> history, int before) {
    for (int i = before - 1; i >= 0; i--) {
      if (history.get(i).role() == Role.USER) {
        return i;
      }
    }
    return 0;
  }

  private static boolean savesAnswer(List<ChatMessage> turn) {
    return turn.stream()
        .flatMap(message -> message.toolCalls().stream())
        .anyMatch(call -> TOOL_NAME.equals(call.name()));
  }

  private static Optional<String> answer(List<ChatMessage> turn) {
    for (int i = turn.size() - 1; i >= 0; i--) {
      ChatMessage message = turn.get(i);
      if (message.role() == Role.ASSISTANT && !message.content().isBlank()) {
        return Optional.of(message.content());
      }
    }
    return Optional.empty();
  }

  private Path target(SaveAnswerCommand command) {
    Path requested = command.workingDirectory().resolve(command.path().strip())
        .toAbsolutePath().normalize();
    if (command.path().isBlank() || answerFilePort.isDirectory(requested)) {
      return requested.resolve(
          "answer-" + FILE_TIMESTAMP.format(clock.instant()) + ".md");
    }
    return requested;
  }
}

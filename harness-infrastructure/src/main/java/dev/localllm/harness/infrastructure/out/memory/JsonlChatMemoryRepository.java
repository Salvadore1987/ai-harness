package dev.localllm.harness.infrastructure.out.memory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import dev.localllm.harness.application.model.SessionInfo;
import dev.localllm.harness.infrastructure.config.HarnessProperties;
import dev.localllm.harness.infrastructure.out.memory.dto.JsonlLine;
import dev.localllm.harness.infrastructure.out.memory.mapper.JsonlLineMapper;

/**
 * Spring AI chat memory repository on JSONL files, one file per conversation:
 * {@code <home>/sessions/<conversationId>.jsonl}.
 *
 * <p>The repository methods cover the message lines. Session metadata, compactions and reasoning
 * are lines of the same file, handled by the additional methods. Files are append-only;
 * {@link #saveAll} rewrites a file atomically only when the stored messages are not a prefix of
 * the new list. Lines that cannot be parsed are skipped and logged.
 */
@Component
public class JsonlChatMemoryRepository implements ChatMemoryRepository {

  private static final Logger log = LoggerFactory.getLogger(JsonlChatMemoryRepository.class);
  private static final String EXTENSION = ".jsonl";

  private final Path directory;
  private final JsonMapper jsonMapper;
  private final JsonlLineMapper lineMapper;
  private final Clock clock;

  /**
   * Creates the repository.
   *
   * @param properties harness settings with the sessions directory
   * @param jsonMapper JSON mapper for session lines
   * @param lineMapper converts lines to messages and metadata
   * @param clock source of line timestamps
   */
  public JsonlChatMemoryRepository(
      HarnessProperties properties,
      @Qualifier("jsonlMapper") JsonMapper jsonMapper,
      JsonlLineMapper lineMapper,
      Clock clock) {
    this.directory = properties.sessionsDirectory();
    this.jsonMapper = jsonMapper;
    this.lineMapper = lineMapper;
    this.clock = clock;
  }

  @Override
  public synchronized List<String> findConversationIds() {
    if (!Files.isDirectory(directory)) {
      return List.of();
    }
    try (Stream<Path> files = Files.list(directory)) {
      return files
          .map(path -> path.getFileName().toString())
          .filter(name -> name.endsWith(EXTENSION))
          .map(name -> name.substring(0, name.length() - EXTENSION.length()))
          .sorted()
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot list " + directory, e);
    }
  }

  @Override
  public synchronized List<Message> findByConversationId(String conversationId) {
    return lines(conversationId, JsonlLine.MESSAGE).stream().map(lineMapper::toMessage).toList();
  }

  @Override
  public synchronized void saveAll(String conversationId, List<Message> messages) {
    List<Message> stored = findByConversationId(conversationId);
    if (isPrefix(stored, messages)) {
      Instant now = Instant.now(clock);
      append(conversationId, messages.subList(stored.size(), messages.size()).stream()
          .map(message -> lineMapper.toMessageLine(message, now))
          .toList());
      return;
    }
    rewrite(conversationId, messages);
  }

  @Override
  public synchronized void deleteByConversationId(String conversationId) {
    try {
      Files.deleteIfExists(file(conversationId));
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot delete session " + conversationId, e);
    }
  }

  /**
   * Writes the metadata line unless the file already has one.
   *
   * @param info session metadata
   */
  public synchronized void writeMeta(SessionInfo info) {
    String conversationId = info.sessionId().toString();
    if (lines(conversationId, JsonlLine.META).isEmpty()) {
      List<JsonlLine> all = new ArrayList<>();
      all.add(lineMapper.toMetaLine(info));
      all.addAll(readAll(conversationId));
      write(conversationId, all);
    }
  }

  /**
   * Reads the session metadata with the current message count.
   *
   * @param conversationId session identifier
   * @return metadata, or empty if the file has no meta line
   */
  public synchronized Optional<SessionInfo> readSession(String conversationId) {
    List<JsonlLine> all = readAll(conversationId);
    int messageCount = (int) all.stream().filter(line -> isType(line, JsonlLine.MESSAGE)).count();
    return all.stream()
        .filter(line -> isType(line, JsonlLine.META))
        .findFirst()
        .map(meta -> lineMapper.toSessionInfo(meta, messageCount));
  }

  /**
   * Appends a compaction line.
   *
   * @param conversationId session identifier
   * @param summary summary of the replaced messages, possibly empty
   * @param replacedCount number of leading messages it replaces
   */
  public synchronized void appendCompaction(
      String conversationId, String summary, int replacedCount) {
    append(conversationId,
        List.of(lineMapper.toCompactionLine(summary, replacedCount, Instant.now(clock))));
  }

  /**
   * Returns the most recent compaction line.
   *
   * @param conversationId session identifier
   * @return the latest compaction, or empty if the history was never compacted
   */
  public synchronized Optional<JsonlLine> latestCompaction(String conversationId) {
    List<JsonlLine> compactions = lines(conversationId, JsonlLine.COMPACTION);
    return compactions.isEmpty() ? Optional.empty() : Optional.of(compactions.getLast());
  }

  /**
   * Appends a reasoning line.
   *
   * @param conversationId session identifier
   * @param thinking reasoning text of one turn
   */
  public synchronized void appendThinking(String conversationId, String thinking) {
    append(conversationId, List.of(lineMapper.toThinkingLine(thinking, Instant.now(clock))));
  }

  private void rewrite(String conversationId, List<Message> messages) {
    Instant now = Instant.now(clock);
    List<JsonlLine> all = new ArrayList<>(lines(conversationId, JsonlLine.META));
    messages.forEach(message -> all.add(lineMapper.toMessageLine(message, now)));
    write(conversationId, all);
  }

  private static boolean isPrefix(List<Message> stored, List<Message> messages) {
    if (stored.size() > messages.size()) {
      return false;
    }
    for (int i = 0; i < stored.size(); i++) {
      Message left = stored.get(i);
      Message right = messages.get(i);
      if (left.getMessageType() != right.getMessageType()
          || !Objects.equals(left.getText(), right.getText())) {
        return false;
      }
    }
    return true;
  }

  private List<JsonlLine> lines(String conversationId, String type) {
    return readAll(conversationId).stream().filter(line -> isType(line, type)).toList();
  }

  private static boolean isType(JsonlLine line, String type) {
    return type.equals(line.type());
  }

  private List<JsonlLine> readAll(String conversationId) {
    Path file = file(conversationId);
    if (!Files.exists(file)) {
      return List.of();
    }
    List<String> raw;
    try {
      raw = Files.readAllLines(file, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read session " + conversationId, e);
    }
    List<JsonlLine> parsed = new ArrayList<>(raw.size());
    for (int i = 0; i < raw.size(); i++) {
      String text = raw.get(i);
      if (text.isBlank()) {
        continue;
      }
      try {
        parsed.add(jsonMapper.readValue(text, JsonlLine.class));
      } catch (JacksonException e) {
        log.error("Skipping corrupted line {} of session {}: {}", i + 1, conversationId,
            e.getOriginalMessage());
      }
    }
    return parsed;
  }

  private void append(String conversationId, List<JsonlLine> lines) {
    if (lines.isEmpty()) {
      return;
    }
    try {
      Files.createDirectories(directory);
      Files.writeString(file(conversationId), serialize(lines), StandardCharsets.UTF_8,
          StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot write session " + conversationId, e);
    }
  }

  private void write(String conversationId, List<JsonlLine> lines) {
    try {
      Files.createDirectories(directory);
      Path temporary = Files.createTempFile(directory, conversationId, ".tmp");
      Files.writeString(temporary, serialize(lines), StandardCharsets.UTF_8);
      Files.move(temporary, file(conversationId),
          StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot rewrite session " + conversationId, e);
    }
  }

  private String serialize(List<JsonlLine> lines) {
    StringBuilder text = new StringBuilder();
    for (JsonlLine line : lines) {
      text.append(jsonMapper.writeValueAsString(line)).append('\n');
    }
    return text.toString();
  }

  private Path file(String conversationId) {
    return directory.resolve(conversationId + EXTENSION);
  }
}

package dev.localllm.harness.application.service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import dev.localllm.harness.application.exception.HarnessException;
import dev.localllm.harness.application.model.ChatCompletion;
import dev.localllm.harness.application.model.ChatMessage;
import dev.localllm.harness.application.model.ChatRequest;
import dev.localllm.harness.application.model.CompactionOutcome;
import dev.localllm.harness.application.model.ContextUsage;
import dev.localllm.harness.application.model.LlmProvider;
import dev.localllm.harness.application.model.Role;
import dev.localllm.harness.application.model.SessionId;
import dev.localllm.harness.application.model.ToolCall;
import dev.localllm.harness.application.model.TurnStatus;
import dev.localllm.harness.application.port.out.ChatModelPort;
import dev.localllm.harness.application.port.out.ConversationPort;

/**
 * Replaces the older part of a history with a summary written by the model.
 *
 * <p>The most recent messages stay verbatim as long as they fit into
 * {@link ContextMeter#tailBudget(LlmProvider, String)}; the kept tail never starts with a tool
 * result, so a result is never separated from the call it answers. Files attached as
 * {@code <file>} blocks are replaced by their {@code @path} in the transcript given to the
 * summarizer. If the model cannot write the summary, the older messages are dropped without
 * one, so the next request still fits into the context window.
 */
public class ConversationCompactor {

  private static final String SUMMARY_INSTRUCTIONS = """
      You compress a conversation between a user and an AI coding assistant.
      Write a concise summary in the language of the conversation. Keep facts, decisions,
      file names, code identifiers, commands and open tasks. Drop greetings and repetition.
      Attached files appear as @path references: mention which files were discussed.
      Output only the summary.""";

  private static final Pattern FILE_BLOCK =
      Pattern.compile("<file path=\"([^\"]*)\">\\n.*?</file>", Pattern.DOTALL);

  private final ConversationPort conversationPort;
  private final ChatModelPort chatModelPort;
  private final ContextMeter contextMeter;

  /**
   * Creates the compactor.
   *
   * @param conversationPort conversation history storage
   * @param chatModelPort language model writing the summary
   * @param contextMeter token estimates and the budget of the kept tail
   */
  public ConversationCompactor(
      ConversationPort conversationPort,
      ChatModelPort chatModelPort,
      ContextMeter contextMeter) {
    this.conversationPort = conversationPort;
    this.chatModelPort = chatModelPort;
    this.contextMeter = contextMeter;
  }

  /**
   * Compacts the history of a session.
   *
   * @param sessionId session to compact
   * @param provider provider of the model
   * @param model model writing the summary
   * @return outcome of the compaction
   */
  public CompactionOutcome compact(SessionId sessionId, LlmProvider provider, String model) {
    List<ChatMessage> history = conversationPort.history(sessionId);
    int tokensBefore = contextMeter.estimateTokens(history);
    int keep = tailSize(history, contextMeter.tailBudget(provider, model));
    List<ChatMessage> older = history.subList(0, history.size() - keep);
    if (older.stream().allMatch(message -> message.role() == Role.SYSTEM)) {
      return new CompactionOutcome(false, false, tokensBefore, tokensBefore);
    }
    String summary = summarize(provider, model, older);
    conversationPort.compact(sessionId, summary, keep);
    int tokensAfter = contextMeter.estimateTokens(conversationPort.history(sessionId));
    return new CompactionOutcome(true, !summary.isEmpty(), tokensBefore, tokensAfter);
  }

  /**
   * Compacts the history if it has reached the compaction threshold.
   *
   * @param sessionId session to check
   * @param provider provider of the model
   * @param model model of the session
   * @return outcome of the compaction, or empty if none was needed
   */
  public Optional<CompactionOutcome> compactIfNeeded(
      SessionId sessionId, LlmProvider provider, String model) {
    ContextUsage usage =
        contextMeter.measure(conversationPort.history(sessionId), provider, model);
    if (!contextMeter.isOverThreshold(usage)) {
      return Optional.empty();
    }
    return Optional.of(compact(sessionId, provider, model));
  }

  private int tailSize(List<ChatMessage> history, int budget) {
    int start = history.size();
    int tokens = 0;
    while (start > 0) {
      int messageTokens = contextMeter.estimateTokens(history.get(start - 1));
      if (tokens + messageTokens > budget) {
        break;
      }
      tokens += messageTokens;
      start--;
    }
    while (start < history.size() && history.get(start).role() == Role.TOOL_RESULT) {
      start++;
    }
    return history.size() - start;
  }

  private String summarize(LlmProvider provider, String model, List<ChatMessage> older) {
    ChatRequest request = new ChatRequest(provider, model, List.of(
        new ChatMessage(Role.SYSTEM, SUMMARY_INSTRUCTIONS),
        new ChatMessage(Role.USER, transcript(older))), false);
    try {
      ChatCompletion completion = chatModelPort.stream(request, chunk -> { });
      return completion.status() == TurnStatus.COMPLETED ? completion.content().strip() : "";
    } catch (HarnessException e) {
      return "";
    }
  }

  private static String transcript(List<ChatMessage> messages) {
    return messages.stream()
        .map(ConversationCompactor::transcriptLine)
        .collect(Collectors.joining("\n\n"));
  }

  private static String transcriptLine(ChatMessage message) {
    StringBuilder line = new StringBuilder(message.role().name().toLowerCase(Locale.ROOT));
    if (message.toolName() != null) {
      line.append(' ').append(message.toolName());
    }
    line.append(": ").append(withFileReferences(message.content()));
    for (ToolCall call : message.toolCalls()) {
      line.append("\n[called ").append(call.name()).append(' ').append(call.argumentsJson())
          .append(']');
    }
    return line.toString();
  }

  private static String withFileReferences(String content) {
    return FILE_BLOCK.matcher(content)
        .replaceAll(match -> Matcher.quoteReplacement("@" + match.group(1)));
  }
}

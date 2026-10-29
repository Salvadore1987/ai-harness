package dev.localllm.harness.application.service;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import dev.localllm.harness.application.exception.HarnessException;
import dev.localllm.harness.application.model.ChatCompletion;
import dev.localllm.harness.application.model.ChatMessage;
import dev.localllm.harness.application.model.ChatRequest;
import dev.localllm.harness.application.model.CompactionOutcome;
import dev.localllm.harness.application.model.ContextPolicy;
import dev.localllm.harness.application.model.Role;
import dev.localllm.harness.application.model.SessionId;
import dev.localllm.harness.application.model.TurnStatus;
import dev.localllm.harness.application.port.out.ChatModelPort;
import dev.localllm.harness.application.port.out.ConversationPort;

/**
 * Replaces the older part of a history with a summary written by the model.
 *
 * <p>The last {@link ContextPolicy#keepLastMessages()} messages stay verbatim. If the model
 * cannot write the summary, the older messages are dropped without one, so the next request
 * still fits into the context window.
 */
public class ConversationCompactor {

  private static final String SUMMARY_INSTRUCTIONS = """
      You compress a conversation between a user and an AI coding assistant.
      Write a concise summary in the language of the conversation. Keep facts, decisions,
      file names, code identifiers, commands and open tasks. Drop greetings and repetition.
      Output only the summary.""";

  private final ConversationPort conversationPort;
  private final ChatModelPort chatModelPort;
  private final ContextMeter contextMeter;
  private final ContextPolicy policy;

  /**
   * Creates the compactor.
   *
   * @param conversationPort conversation history storage
   * @param chatModelPort language model writing the summary
   * @param contextMeter token estimates before and after
   * @param policy number of messages kept verbatim
   */
  public ConversationCompactor(
      ConversationPort conversationPort,
      ChatModelPort chatModelPort,
      ContextMeter contextMeter,
      ContextPolicy policy) {
    this.conversationPort = conversationPort;
    this.chatModelPort = chatModelPort;
    this.contextMeter = contextMeter;
    this.policy = policy;
  }

  /**
   * Compacts the history of a session.
   *
   * @param sessionId session to compact
   * @param model model writing the summary
   * @return outcome of the compaction
   */
  public CompactionOutcome compact(SessionId sessionId, String model) {
    List<ChatMessage> history = conversationPort.history(sessionId);
    int tokensBefore = contextMeter.estimateTokens(history);
    int keep = policy.keepLastMessages();
    if (history.size() <= keep) {
      return new CompactionOutcome(false, false, tokensBefore, tokensBefore);
    }
    String summary = summarize(model, history.subList(0, history.size() - keep));
    conversationPort.compact(sessionId, summary, keep);
    int tokensAfter = contextMeter.estimateTokens(conversationPort.history(sessionId));
    return new CompactionOutcome(true, !summary.isEmpty(), tokensBefore, tokensAfter);
  }

  private String summarize(String model, List<ChatMessage> older) {
    ChatRequest request = new ChatRequest(model, List.of(
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
        .map(message -> message.role().name().toLowerCase(Locale.ROOT) + ": " + message.content())
        .collect(Collectors.joining("\n\n"));
  }
}

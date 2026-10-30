package dev.localllm.harness.application.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import dev.localllm.harness.application.dto.command.RunAgentTurnCommand;
import dev.localllm.harness.application.dto.result.TurnResult;
import dev.localllm.harness.application.exception.AgentLimitExceededException;
import dev.localllm.harness.application.mapper.TurnApplicationMapper;
import dev.localllm.harness.application.model.AgentPolicy;
import dev.localllm.harness.application.model.ChatChunk;
import dev.localllm.harness.application.model.ChatCompletion;
import dev.localllm.harness.application.model.ChatMessage;
import dev.localllm.harness.application.model.ChatRequest;
import dev.localllm.harness.application.model.Role;
import dev.localllm.harness.application.model.TokenUsage;
import dev.localllm.harness.application.model.ToolCall;
import dev.localllm.harness.application.model.ToolResult;
import dev.localllm.harness.application.model.ToolSpec;
import dev.localllm.harness.application.model.TurnStatus;
import dev.localllm.harness.application.port.in.RunAgentTurnUseCase;
import dev.localllm.harness.application.port.out.ChatModelPort;
import dev.localllm.harness.application.port.out.ConversationPort;
import dev.localllm.harness.application.port.out.ToolExecutorPort;
import dev.localllm.harness.application.port.out.TurnOutputPort;

/**
 * Runs an agent turn: asks the model, executes the tool calls it requests, returns the results
 * and repeats until the model answers without a call.
 *
 * <p>Every step is stored right away: the assistant message with its calls, then one result
 * message per call, so a resumed session always has a result for each call. The loop stops at
 * {@link AgentPolicy#maxIterations()} model requests or after
 * {@link AgentPolicy#maxParseFailures()} consecutive steps that called only unknown tools or
 * wrote a tool call that could not be read; such a call is stored as
 * {@link ToolCall#PARSE_ERROR} and answered with {@code TOOL_ERROR}.
 * Cancellation stops the running stream at once and the loop before the next tool call; calls
 * that were skipped get a "cancelled" result. The system prompt is not stored: it is rebuilt
 * from the working directory on every request and stays the same between turns, so the prompt
 * prefix can be reused by the model server.
 */
public class AgentLoopService implements RunAgentTurnUseCase {

  private static final String SYSTEM_PROMPT = """
      You are a coding assistant working in a terminal on the user's machine.
      Use the tools to read and change files, run commands and fetch web pages instead of \
      guessing. Prefer edit_file over write_file for changes to existing files. Relative paths \
      are resolved against the working directory. When the user asks to save a text you have \
      already written in this conversation, call save_answer: it copies the stored text \
      exactly. Never retype such a text with write_file. Answer without \
      a tool call as soon as you have enough information. Answer in the language of the user.
      Working directory:\s""";
  private static final String SUMMARY_HEADER = "\n\nSummary of the earlier conversation:\n";
  private static final String CANCELLED_OUTPUT = "Cancelled by the user before execution";
  private static final String TOOL_ERROR_PREFIX = "TOOL_ERROR: ";
  private static final String TOOL_ERROR_SUFFIX = ". Repeat the call in the correct format.";

  private final ConversationPort conversationPort;
  private final SessionRegistrar sessionRegistrar;
  private final ChatModelPort chatModelPort;
  private final ToolExecutorPort toolExecutorPort;
  private final TurnOutputPort turnOutputPort;
  private final ConversationCompactor compactor;
  private final TurnCancellation cancellation;
  private final AgentPolicy policy;
  private final TurnApplicationMapper mapper;

  /**
   * Creates the service.
   *
   * @param conversationPort conversation history storage
   * @param sessionRegistrar stores the session metadata on the first turn
   * @param chatModelPort language model
   * @param toolExecutorPort tools the model may call
   * @param turnOutputPort presentation of the streamed turn and the tool calls
   * @param compactor compacts the history when it gets too long
   * @param cancellation cancellation request set by {@link CancelTurnService}
   * @param policy limits of the loop
   * @param mapper converts outcomes into results
   */
  public AgentLoopService(
      ConversationPort conversationPort,
      SessionRegistrar sessionRegistrar,
      ChatModelPort chatModelPort,
      ToolExecutorPort toolExecutorPort,
      TurnOutputPort turnOutputPort,
      ConversationCompactor compactor,
      TurnCancellation cancellation,
      AgentPolicy policy,
      TurnApplicationMapper mapper) {
    this.conversationPort = conversationPort;
    this.sessionRegistrar = sessionRegistrar;
    this.chatModelPort = chatModelPort;
    this.toolExecutorPort = toolExecutorPort;
    this.turnOutputPort = turnOutputPort;
    this.compactor = compactor;
    this.cancellation = cancellation;
    this.policy = policy;
    this.mapper = mapper;
  }

  @Override
  public TurnResult execute(RunAgentTurnCommand command) {
    cancellation.reset();
    List<ToolSpec> tools = toolExecutorPort.available();
    Set<String> toolNames = tools.stream().map(ToolSpec::name).collect(Collectors.toSet());
    TurnProgress progress = new TurnProgress(new ChatMessage(Role.USER, command.text()));
    for (int step = 0; step < policy.maxIterations(); step++) {
      Step stepResult = runStep(command, tools, progress, step == 0);
      ChatCompletion completion = stepResult.completion();
      List<ToolCall> calls = stepResult.calls();
      if (calls.isEmpty()) {
        return finish(command, completion, completion.status(), progress);
      }

      progress.countToolStep(calls.stream().noneMatch(call -> toolNames.contains(call.name())));
      executeTools(command, calls, toolNames, completion.toolCallError());
      if (cancellation.isRequested()) {
        return finish(command, completion, TurnStatus.CANCELLED, progress);
      }
      if (progress.parseFailures >= policy.maxParseFailures()) {
        throw new AgentLimitExceededException(
            AgentLimitExceededException.Limit.PARSE_FAILURES, policy.maxParseFailures());
      }
      compactIfNeeded(command);
    }
    throw new AgentLimitExceededException(
        AgentLimitExceededException.Limit.ITERATIONS, policy.maxIterations());
  }

  private Step runStep(RunAgentTurnCommand command, List<ToolSpec> tools,
      TurnProgress progress, boolean firstStep) {
    List<ChatMessage> messages = requestMessages(command);
    if (firstStep) {
      messages.add(progress.userMessage);
    }
    StringBuilder thinking = new StringBuilder();
    ChatCompletion completion = chatModelPort.stream(
        new ChatRequest(command.model(), messages, command.thinkingEnabled(), tools),
        chunk -> present(chunk, thinking));
    progress.add(completion);
    if (firstStep) {
      sessionRegistrar.registerIfNew(command.sessionId(), command.workingDirectory(),
          command.model(), command.text());
      conversationPort.append(command.sessionId(), progress.userMessage);
    }
    List<ToolCall> calls = completion.status() == TurnStatus.COMPLETED
        ? requestedCalls(completion) : List.of();
    conversationPort.append(command.sessionId(),
        ChatMessage.assistant(completion.historyContent(), calls));
    if (!thinking.isEmpty()) {
      conversationPort.recordThinking(command.sessionId(), thinking.toString());
    }
    return new Step(completion, calls);
  }

  private List<ChatMessage> requestMessages(RunAgentTurnCommand command) {
    List<ChatMessage> history = conversationPort.history(command.sessionId());
    String systemPrompt = SYSTEM_PROMPT + command.workingDirectory();
    List<ChatMessage> messages = new ArrayList<>(history.size() + 2);
    if (!history.isEmpty() && history.getFirst().role() == Role.SYSTEM) {
      messages.add(new ChatMessage(
          Role.SYSTEM, systemPrompt + SUMMARY_HEADER + history.getFirst().content()));
      messages.addAll(history.subList(1, history.size()));
    } else {
      messages.add(new ChatMessage(Role.SYSTEM, systemPrompt));
      messages.addAll(history);
    }
    return messages;
  }

  private static List<ToolCall> requestedCalls(ChatCompletion completion) {
    if (completion.toolCalls().isEmpty() && completion.toolCallError() != null) {
      return List.of(ToolCall.parseError());
    }
    return completion.toolCalls();
  }

  private void executeTools(RunAgentTurnCommand command, List<ToolCall> calls,
      Set<String> toolNames, @Nullable String toolCallError) {
    for (ToolCall call : calls) {
      ToolResult result;
      if (cancellation.isRequested()) {
        result = ToolResult.rejected(call, CANCELLED_OUTPUT);
      } else {
        turnOutputPort.onToolCall(call);
        result = executeTool(command, call, toolNames, toolCallError);
        turnOutputPort.onToolResult(result);
      }
      conversationPort.append(command.sessionId(), ChatMessage.toolResult(result));
    }
  }

  private ToolResult executeTool(RunAgentTurnCommand command, ToolCall call,
      Set<String> toolNames, @Nullable String toolCallError) {
    if (toolNames.contains(call.name())) {
      return toolExecutorPort.execute(call, command.sessionId(), command.workingDirectory());
    }
    if (ToolCall.PARSE_ERROR.equals(call.name()) && toolCallError != null) {
      return ToolResult.rejected(call, TOOL_ERROR_PREFIX + toolCallError + TOOL_ERROR_SUFFIX);
    }
    return ToolResult.rejected(call, unknownToolMessage(call, toolNames));
  }

  private TurnResult finish(RunAgentTurnCommand command, ChatCompletion last, TurnStatus status,
      TurnProgress progress) {
    ChatCompletion total = new ChatCompletion(last.content(), status,
        new TokenUsage(last.usage().promptTokens(), progress.completionTokens),
        progress.elapsed);
    TurnResult result = mapper.toTurnResult(total);
    turnOutputPort.onComplete(result);
    compactIfNeeded(command);
    return result;
  }

  private void compactIfNeeded(RunAgentTurnCommand command) {
    compactor.compactIfNeeded(command.sessionId(), command.model())
        .ifPresent(outcome -> turnOutputPort.onCompaction(mapper.toCompactionResult(outcome)));
  }

  private void present(ChatChunk chunk, StringBuilder thinking) {
    switch (chunk) {
      case ChatChunk.Thinking reasoning -> {
        thinking.append(reasoning.text());
        turnOutputPort.onThinking(reasoning.text());
      }
      case ChatChunk.Content content -> turnOutputPort.onContent(content.text());
    }
  }

  private static String unknownToolMessage(ToolCall call, Set<String> toolNames) {
    return "Unknown tool " + call.name() + ". Available tools: "
        + toolNames.stream().sorted().collect(Collectors.joining(", "));
  }

  /** One model request with the tool calls it asked for. */
  private record Step(ChatCompletion completion, List<ToolCall> calls) {
  }

  /** Totals of one turn across its model requests. */
  private static final class TurnProgress {

    private final ChatMessage userMessage;
    private int completionTokens;
    private Duration elapsed = Duration.ZERO;
    private int parseFailures;

    private TurnProgress(ChatMessage userMessage) {
      this.userMessage = userMessage;
    }

    private void add(ChatCompletion completion) {
      completionTokens += completion.usage().completionTokens();
      elapsed = elapsed.plus(completion.elapsed());
    }

    private void countToolStep(boolean onlyUnknownTools) {
      parseFailures = onlyUnknownTools ? parseFailures + 1 : 0;
    }
  }
}

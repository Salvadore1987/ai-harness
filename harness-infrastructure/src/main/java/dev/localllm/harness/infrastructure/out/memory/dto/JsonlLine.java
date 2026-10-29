package dev.localllm.harness.infrastructure.out.memory.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

/**
 * One line of a session file; {@code type} tells which fields are set.
 *
 * <ul>
 *   <li>{@code meta} — {@code sessionId}, {@code cwd}, {@code model}, {@code title}</li>
 *   <li>{@code message} — {@code role}, {@code content}; an assistant message may carry
 *       {@code toolCalls}, a {@code TOOL_RESULT} message carries {@code callId} and
 *       {@code toolName}</li>
 *   <li>{@code compaction} — {@code summary} and {@code replacedCount}, the number of leading
 *       messages it replaces</li>
 *   <li>{@code thinking} — {@code content} with the reasoning of a turn</li>
 * </ul>
 *
 * @param type line type
 * @param sessionId session identifier
 * @param cwd working directory of the session
 * @param model model of the first turn
 * @param title beginning of the first user message
 * @param role message role: {@code SYSTEM}, {@code USER}, {@code ASSISTANT} or
 *     {@code TOOL_RESULT}
 * @param content message or reasoning text
 * @param toolCalls tool calls requested by an assistant message
 * @param callId tool call answered by a {@code TOOL_RESULT} message
 * @param toolName tool of the answered call
 * @param summary compaction summary, empty if messages were dropped without one
 * @param replacedCount number of leading messages replaced by the compaction
 * @param createdAt time the line was written
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JsonlLine(
    String type,
    @Nullable String sessionId,
    @Nullable String cwd,
    @Nullable String model,
    @Nullable String title,
    @Nullable String role,
    @Nullable String content,
    @Nullable List<JsonlToolCall> toolCalls,
    @Nullable String callId,
    @Nullable String toolName,
    @Nullable String summary,
    @Nullable Integer replacedCount,
    Instant createdAt) {

  /** Type of the session metadata line. */
  public static final String META = "meta";
  /** Type of a conversation message line. */
  public static final String MESSAGE = "message";
  /** Type of a compaction line. */
  public static final String COMPACTION = "compaction";
  /** Role of a tool result message; Spring AI calls it {@code TOOL}. */
  public static final String TOOL_RESULT_ROLE = "TOOL_RESULT";
  /** Type of a reasoning line. */
  public static final String THINKING = "thinking";
}

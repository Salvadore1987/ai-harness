package dev.localllm.harness.infrastructure.out.tools;

import java.nio.file.Path;

import org.springframework.ai.chat.model.ToolContext;

import dev.localllm.harness.application.model.SessionId;

/**
 * Reads the working directory and the session that {@link SpringAiToolExecutorAdapter} puts
 * into the {@link ToolContext} of every call.
 */
final class ToolContexts {

  private ToolContexts() {
  }

  /**
   * Working directory of the call.
   *
   * @param context tool context
   * @return absolute, normalized working directory
   */
  static Path workingDirectory(ToolContext context) {
    Object value = context.getContext().get(SpringAiToolExecutorAdapter.WORKING_DIRECTORY);
    if (value instanceof Path directory) {
      return directory.toAbsolutePath().normalize();
    }
    throw new IllegalStateException("The working directory is missing from the tool context");
  }

  /**
   * Session of the call.
   *
   * @param context tool context
   * @return session identifier
   */
  static SessionId sessionId(ToolContext context) {
    Object value = context.getContext().get(SpringAiToolExecutorAdapter.SESSION_ID);
    if (value instanceof SessionId sessionId) {
      return sessionId;
    }
    throw new IllegalStateException("The session is missing from the tool context");
  }

  /**
   * Resolves a path given by the model; absolute paths are kept as they are.
   *
   * @param context tool context
   * @param path relative or absolute path
   * @return normalized path
   */
  static Path resolve(ToolContext context, String path) {
    return workingDirectory(context).resolve(path).normalize();
  }
}

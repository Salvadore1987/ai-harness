package dev.localllm.harness.application.service;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;

import dev.localllm.harness.application.model.LlmProvider;
import dev.localllm.harness.application.model.SessionId;
import dev.localllm.harness.application.model.SessionInfo;
import dev.localllm.harness.application.port.out.SessionCatalogPort;

/**
 * Stores the metadata of a session on its first answered turn.
 */
public class SessionRegistrar {

  private static final int TITLE_LENGTH = 60;

  private final SessionCatalogPort sessionCatalogPort;
  private final Clock clock;

  /**
   * Creates the registrar.
   *
   * @param sessionCatalogPort session metadata storage
   * @param clock source of the session creation time
   */
  public SessionRegistrar(SessionCatalogPort sessionCatalogPort, Clock clock) {
    this.sessionCatalogPort = sessionCatalogPort;
    this.clock = clock;
  }

  /**
   * Registers the session unless it is already known; the title is the beginning of the first
   * user message on a single line.
   *
   * @param sessionId session identifier
   * @param workingDirectory directory the session works in
   * @param provider provider of the session
   * @param model model of the first turn
   * @param firstMessage text of the first user message
   */
  public void registerIfNew(SessionId sessionId, Path workingDirectory, LlmProvider provider,
      String model, String firstMessage) {
    if (sessionCatalogPort.find(sessionId).isEmpty()) {
      sessionCatalogPort.register(new SessionInfo(sessionId, workingDirectory, provider, model,
          title(firstMessage), Instant.now(clock), 0));
    }
  }

  private static String title(String text) {
    String singleLine = text.strip().replaceAll("\\s+", " ");
    return singleLine.length() <= TITLE_LENGTH ? singleLine : singleLine.substring(0, TITLE_LENGTH);
  }
}

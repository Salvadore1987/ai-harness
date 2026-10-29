package dev.localllm.harness.application.port.out;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import dev.localllm.harness.application.model.SessionId;
import dev.localllm.harness.application.model.SessionInfo;

/**
 * Stores and lists session metadata.
 */
public interface SessionCatalogPort {

  /**
   * Records the metadata of a session on its first turn; later calls for the same session are
   * ignored.
   *
   * @param info session metadata
   */
  void register(SessionInfo info);

  /**
   * Looks up a stored session.
   *
   * @param sessionId identifier of the session
   * @return the session, or empty if it was never registered
   */
  Optional<SessionInfo> find(SessionId sessionId);

  /**
   * Lists the sessions started in a directory, newest first.
   *
   * @param workingDirectory directory of the sessions
   * @return stored sessions
   */
  List<SessionInfo> list(Path workingDirectory);
}

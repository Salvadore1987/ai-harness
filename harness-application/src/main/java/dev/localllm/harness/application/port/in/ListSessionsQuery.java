package dev.localllm.harness.application.port.in;

import java.util.List;

import dev.localllm.harness.application.dto.query.ListSessionsCriteria;
import dev.localllm.harness.application.dto.result.SessionSummaryResult;

/**
 * Lists the stored sessions of a directory.
 */
public interface ListSessionsQuery {

  /**
   * Returns the sessions, newest first.
   *
   * @param criteria directory filter
   * @return stored sessions
   */
  List<SessionSummaryResult> execute(ListSessionsCriteria criteria);
}

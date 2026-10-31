package dev.localllm.harness.application.service;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import dev.localllm.harness.application.dto.command.CancelTurnCommand;
import dev.localllm.harness.application.dto.command.PurgeSessionsCommand;
import dev.localllm.harness.application.dto.command.ResumeSessionCommand;
import dev.localllm.harness.application.dto.command.StartSessionCommand;
import dev.localllm.harness.application.dto.query.ListSessionsCriteria;
import dev.localllm.harness.application.dto.result.PurgedSessionsResult;
import dev.localllm.harness.application.dto.result.SessionResult;
import dev.localllm.harness.application.dto.result.SessionSummaryResult;
import dev.localllm.harness.application.exception.SessionNotFoundException;
import dev.localllm.harness.application.fixture.FakeChatModelPort;
import dev.localllm.harness.application.fixture.FakeSessionCatalogPort;
import dev.localllm.harness.application.mapper.TurnApplicationMapper;
import dev.localllm.harness.application.model.SessionId;
import dev.localllm.harness.application.model.SessionInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionServicesTest {

  private static final Path CWD = Path.of("/work/project");
  private static final Path OTHER = Path.of("/work/other");

  private final TurnApplicationMapper mapper = Mappers.getMapper(TurnApplicationMapper.class);
  private final FakeSessionCatalogPort catalog = new FakeSessionCatalogPort();
  private final ResumeSessionService resume = new ResumeSessionService(catalog, mapper);

  @Test
  void startSession_Always_ReturnsNewTimeOrderedId() {
    // Arrange
    StartSessionService service = new StartSessionService(mapper);

    // Act
    SessionResult first = service.execute(new StartSessionCommand());
    SessionResult second = service.execute(new StartSessionCommand());

    // Assert
    assertThat(first.sessionId().value().version()).isEqualTo(7);
    assertThat(second.sessionId()).isNotEqualTo(first.sessionId());
  }

  @Test
  void resume_WithoutReference_ReturnsLatestSessionOfDirectory() {
    // Arrange
    SessionInfo older = register(CWD, "older", "2026-10-28T10:00:00Z");
    SessionInfo newer = register(CWD, "newer", "2026-10-29T10:00:00Z");
    register(OTHER, "elsewhere", "2026-10-30T10:00:00Z");

    // Act
    SessionSummaryResult result = resume.execute(new ResumeSessionCommand(CWD, ""));

    // Assert
    assertThat(result.sessionId()).isEqualTo(newer.sessionId()).isNotEqualTo(older.sessionId());
  }

  @Test
  void resume_WithUniquePrefix_ReturnsMatchingSession() {
    // Arrange
    SessionInfo session = register(CWD, "one", "2026-10-29T10:00:00Z");
    String prefix = session.sessionId().toString().substring(0, 13);

    // Act
    SessionSummaryResult result = resume.execute(new ResumeSessionCommand(CWD, prefix));

    // Assert
    assertThat(result.title()).isEqualTo("one");
  }

  @Test
  void resume_WithFullIdFromOtherDirectory_ReturnsThatSession() {
    // Arrange
    SessionInfo session = register(OTHER, "foreign", "2026-10-29T10:00:00Z");

    // Act
    SessionSummaryResult result =
        resume.execute(new ResumeSessionCommand(CWD, session.sessionId().toString()));

    // Assert
    assertThat(result.title()).isEqualTo("foreign");
  }

  @Test
  void resume_WithoutStoredSessions_ThrowsSessionNotFound() {
    // Act & Assert
    assertThatThrownBy(() -> resume.execute(new ResumeSessionCommand(CWD, "")))
        .isInstanceOf(SessionNotFoundException.class);
  }

  @Test
  void resume_WithAmbiguousPrefix_ThrowsSessionNotFound() {
    // Arrange
    register(CWD, "a", "2026-10-29T10:00:00Z");
    register(CWD, "b", "2026-10-29T11:00:00Z");

    // Act & Assert
    assertThatThrownBy(() -> resume.execute(new ResumeSessionCommand(CWD, "0")))
        .isInstanceOf(SessionNotFoundException.class)
        .hasMessageContaining("0");
  }

  @Test
  void listSessions_ForDirectory_ReturnsItsSessionsNewestFirst() {
    // Arrange
    register(CWD, "older", "2026-10-28T10:00:00Z");
    register(CWD, "newer", "2026-10-29T10:00:00Z");
    register(OTHER, "elsewhere", "2026-10-30T10:00:00Z");
    ListSessionsQueryService service = new ListSessionsQueryService(catalog, mapper);

    // Act
    List<SessionSummaryResult> result = service.execute(new ListSessionsCriteria(CWD));

    // Assert
    assertThat(result).extracting(SessionSummaryResult::title).containsExactly("newer", "older");
  }

  @Test
  void purgeSessions_ForDirectory_DeletesItsSessionsExceptCurrent() {
    // Arrange
    SessionInfo current = register(CWD, "current", "2026-10-29T10:00:00Z");
    register(CWD, "older", "2026-10-28T10:00:00Z");
    register(CWD, "oldest", "2026-10-27T10:00:00Z");
    SessionInfo foreign = register(OTHER, "elsewhere", "2026-10-30T10:00:00Z");
    PurgeSessionsService service = new PurgeSessionsService(catalog, mapper);

    // Act
    PurgedSessionsResult result =
        service.execute(new PurgeSessionsCommand(CWD, current.sessionId()));

    // Assert
    assertThat(result.deletedCount()).isEqualTo(2);
    assertThat(catalog.list(CWD)).extracting(SessionInfo::title).containsExactly("current");
    assertThat(catalog.find(foreign.sessionId())).isPresent();
  }

  @Test
  void purgeSessions_WithoutCurrentSession_DeletesAllSessionsOfDirectory() {
    // Arrange
    register(CWD, "one", "2026-10-29T10:00:00Z");
    PurgeSessionsService service = new PurgeSessionsService(catalog, mapper);

    // Act
    PurgedSessionsResult result = service.execute(new PurgeSessionsCommand(CWD, null));

    // Assert
    assertThat(result.deletedCount()).isEqualTo(1);
    assertThat(catalog.list(CWD)).isEmpty();
  }

  @Test
  void cancelTurn_Always_CancelsActiveStreamAndRequestsLoopStop() {
    // Arrange
    FakeChatModelPort chatModel = new FakeChatModelPort();
    TurnCancellation cancellation = new TurnCancellation();
    CancelTurnService service = new CancelTurnService(chatModel, cancellation);

    // Act
    service.execute(new CancelTurnCommand(SessionId.newId()));

    // Assert
    assertThat(chatModel.cancelCount()).isEqualTo(1);
    assertThat(cancellation.isRequested()).isTrue();
  }

  private SessionInfo register(Path cwd, String title, String createdAt) {
    SessionInfo info = new SessionInfo(SessionId.newId(), cwd, "qwen3.5:27b", title,
        Instant.parse(createdAt), 2);
    catalog.register(info);
    return info;
  }
}

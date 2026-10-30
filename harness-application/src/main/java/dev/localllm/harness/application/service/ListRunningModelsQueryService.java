package dev.localllm.harness.application.service;

import java.util.Comparator;
import java.util.List;

import dev.localllm.harness.application.dto.query.ListRunningModelsCriteria;
import dev.localllm.harness.application.dto.result.RunningModelResult;
import dev.localllm.harness.application.mapper.TurnApplicationMapper;
import dev.localllm.harness.application.model.RunningModelInfo;
import dev.localllm.harness.application.port.in.ListRunningModelsQuery;
import dev.localllm.harness.application.port.out.RunningModelsPort;

/**
 * Lists the loaded models matching a name prefix, sorted by name.
 */
public class ListRunningModelsQueryService implements ListRunningModelsQuery {

  private final RunningModelsPort runningModelsPort;
  private final TurnApplicationMapper mapper;

  /**
   * Creates the service.
   *
   * @param runningModelsPort loaded models
   * @param mapper converts loaded models into results
   */
  public ListRunningModelsQueryService(
      RunningModelsPort runningModelsPort, TurnApplicationMapper mapper) {
    this.runningModelsPort = runningModelsPort;
    this.mapper = mapper;
  }

  @Override
  public List<RunningModelResult> execute(ListRunningModelsCriteria criteria) {
    return runningModelsPort.listRunning().stream()
        .filter(model -> model.name().startsWith(criteria.namePrefix()))
        .sorted(Comparator.comparing(RunningModelInfo::name))
        .map(mapper::toRunningModelResult)
        .toList();
  }
}

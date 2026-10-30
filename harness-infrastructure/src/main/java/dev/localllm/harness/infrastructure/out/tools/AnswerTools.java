package dev.localllm.harness.infrastructure.out.tools;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import dev.localllm.harness.application.dto.result.SavedAnswerResult;
import dev.localllm.harness.application.port.in.SaveAnswerUseCase;
import dev.localllm.harness.infrastructure.out.tools.mapper.AnswerToolMapper;

/**
 * {@code save_answer}: copies a previous answer from the stored history into a file, so the model
 * does not have to write the text again with {@code write_file}.
 */
@Component
public class AnswerTools {

  private final SaveAnswerUseCase saveAnswerUseCase;
  private final AnswerToolMapper mapper;

  /**
   * Creates the tools.
   *
   * @param saveAnswerUseCase writes the answer
   * @param mapper builds the command from the tool arguments
   */
  public AnswerTools(SaveAnswerUseCase saveAnswerUseCase, AnswerToolMapper mapper) {
    this.saveAnswerUseCase = saveAnswerUseCase;
    this.mapper = mapper;
  }

  /**
   * Saves the latest answer given before the current request.
   *
   * @param path target file, a generated name in the working directory if empty
   * @param context tool context with the session and the working directory
   * @return path and size of the written file
   */
  @Tool(name = SaveAnswerUseCase.TOOL_NAME, resultConverter = PlainTextResultConverter.class,
      description = """
      Save your latest answer given before the current user request to a file, exactly as it \
      was shown to the user. Use it whenever the user asks to save, export or write this \
      text, an earlier answer or the result to a file, also when the previous turns used \
      write_file for that: the text is copied from the history, so do not repeat it. \
      Overwrites the file.""")
  public String saveAnswer(
      @ToolParam(required = false, description = "File path, relative to the working directory "
          + "or absolute, for example answer.md; default is answer-<date>-<time>.md")
      @Nullable String path,
      ToolContext context) {
    SavedAnswerResult result = saveAnswerUseCase.execute(mapper.toSaveAnswerCommand(
        ToolContexts.sessionId(context), ToolContexts.workingDirectory(context),
        path == null ? "" : path));
    return "Saved " + result.characters() + " characters to " + result.file();
  }
}

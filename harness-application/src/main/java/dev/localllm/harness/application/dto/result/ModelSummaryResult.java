package dev.localllm.harness.application.dto.result;

/**
 * An installed model as shown to the user.
 *
 * @param name model tag
 * @param parameterSize parameter count, for example {@code 27B}
 * @param quantization quantization level, for example {@code Q4_K_M}
 * @param thinkingSupported whether the model can reason before answering
 * @param toolsSupported whether the model can call tools, natively or through a text protocol
 */
public record ModelSummaryResult(
    String name,
    String parameterSize,
    String quantization,
    boolean thinkingSupported,
    boolean toolsSupported) {
}

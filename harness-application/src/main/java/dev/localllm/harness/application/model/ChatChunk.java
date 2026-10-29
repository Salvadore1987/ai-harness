package dev.localllm.harness.application.model;

/**
 * A fragment of a streamed model response.
 */
public sealed interface ChatChunk permits ChatChunk.Thinking, ChatChunk.Content {

  /**
   * Text of the fragment.
   *
   * @return fragment text
   */
  String text();

  /**
   * Fragment of the model reasoning, shown to the user but not stored in the history.
   *
   * @param text reasoning text
   */
  record Thinking(String text) implements ChatChunk {
  }

  /**
   * Fragment of the answer itself.
   *
   * @param text answer text
   */
  record Content(String text) implements ChatChunk {
  }
}

package se.rainmaker.missioncontrol.navigatorapi;

import java.util.List;
import java.util.Optional;

/**
 * Counts a question against several limits at once, all or nothing (ADR 0003). An implementation
 * must be atomic across Lambda instances: in DynamoDB, one transaction with a conditional update
 * per counter, never read-then-write.
 */
public interface QuestionCounter {

  /**
   * Adds one to every counter if all of them are below their limit; otherwise changes none.
   *
   * @return the id of the first counter that is full, or empty when the question was counted
   */
  Optional<String> tryIncrement(List<CounterLimit> limits);
}

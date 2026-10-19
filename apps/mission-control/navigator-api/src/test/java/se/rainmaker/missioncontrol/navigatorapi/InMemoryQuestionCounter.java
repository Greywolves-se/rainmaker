package se.rainmaker.missioncontrol.navigatorapi;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Test stand-in for the DynamoDB counter: all or nothing, like one conditional transaction. */
final class InMemoryQuestionCounter implements QuestionCounter {

  private final Map<String, Integer> counts = new HashMap<>();

  @Override
  public synchronized Optional<String> tryIncrement(List<CounterLimit> limits) {
    for (CounterLimit limit : limits) {
      if (counts.getOrDefault(limit.id(), 0) >= limit.limit()) {
        return Optional.of(limit.id());
      }
    }
    limits.forEach(limit -> counts.merge(limit.id(), 1, Integer::sum));
    return Optional.empty();
  }

  synchronized int count(String id) {
    return counts.getOrDefault(id, 0);
  }
}

package se.rainmaker.missioncontrol.collector.pipeline;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * One list of the snapshot (e.g. workItems) that keeps its ids unique, as the contract requires.
 */
final class ItemList<T> {

  private final String name;
  private final Function<T, String> idOf;
  private final List<T> items = new ArrayList<>();
  private final Set<String> ids = new HashSet<>();

  ItemList(String name, Function<T, String> idOf) {
    this.name = name;
    this.idOf = idOf;
  }

  void add(T item) {
    Objects.requireNonNull(item, name + ": item must not be null");
    String id = idOf.apply(item);
    if (id == null || id.isBlank()) {
      throw new SourceException(name + ": an item has no id");
    }
    if (!ids.add(id)) {
      throw new SourceException(name + ": id " + id + " is used twice");
    }
    items.add(item);
  }

  /** Problems that stop {@code other} from being added, e.g. ids this list already has. */
  List<String> conflictsWith(ItemList<?> other) {
    return other.ids().stream()
        .filter(ids::contains)
        .map(id -> name + ": id " + id + " is already used by another source")
        .toList();
  }

  void addAll(ItemList<T> other) {
    other.items.forEach(this::add);
  }

  List<T> items() {
    return List.copyOf(items);
  }

  private List<String> ids() {
    return items.stream().map(idOf).toList();
  }
}

package se.rainmaker.missioncontrol.collector.pipeline;

/**
 * A data source (GitHub, data files, ...). Implement this as a Spring bean and the pipeline picks
 * it up; nothing else has to change. Sources are read-only: they never write to the system they
 * read from.
 */
public interface SourceCollector {

  /**
   * Stable id, used as {@code source} on every item and as the key to switch the source off ({@code
   * mission-control.collector.sources.<id>.enabled=false}). Lowercase letters, digits and dashes,
   * e.g. {@code github}.
   */
  String id();

  /** Display name, e.g. {@code GitHub}. */
  String name();

  /**
   * Adds this source's items to {@code snapshot}. Throw {@link SourceException} with a message that
   * is safe to show on the dashboard when the source cannot deliver. Never put secrets such as
   * tokens in exception messages; they are logged.
   *
   * <p>Rules every source follows:
   *
   * <ul>
   *   <li>Item ids are unique within each list across all sources. If a list can get items from
   *       more than one source, prefix the ids with the source (e.g. {@code gh-123}). On a clash
   *       the later source, in id order, is marked as error and adds nothing.
   *   <li>Items must match the contract schema. A source whose data does not is marked as error and
   *       adds nothing, so one bad item never breaks the whole dashboard.
   *   <li>Calls to external systems set their own timeouts and retries with back-off (CLAUDE.md,
   *       "Architecture rules"). The pipeline does not stop a source that hangs.
   * </ul>
   */
  void collect(SnapshotBuilder snapshot);
}

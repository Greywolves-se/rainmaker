package se.rainmaker.missioncontrol.collector.snapshot;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The JSON settings of the snapshot contract, in one place. Writes every field (nulls included),
 * timestamps and dates as ISO 8601 strings, and enum values in lowercase ({@code IN_PROGRESS} is
 * written as {@code in_progress}). Reading fails on fields the records do not know.
 */
public final class SnapshotJson {

  private static final JsonMapper MAPPER =
      JsonMapper.builder()
          .enable(SerializationFeature.INDENT_OUTPUT)
          .enable(EnumFeature.WRITE_ENUMS_TO_LOWERCASE)
          .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
          .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
          .build();

  private SnapshotJson() {}

  /** Returns the mapper. It is immutable and safe to share between threads. */
  public static JsonMapper mapper() {
    return MAPPER;
  }
}

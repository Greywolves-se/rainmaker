package se.rainmaker.missioncontrol.collector.snapshot;

import java.time.Instant;

/** Something that happened in a source, for the recent-events feed. */
public record Event(String id, Instant occurredAt, String title, String source, String url) {}

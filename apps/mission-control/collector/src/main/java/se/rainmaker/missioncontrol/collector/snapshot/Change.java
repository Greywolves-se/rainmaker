package se.rainmaker.missioncontrol.collector.snapshot;

import java.time.LocalDate;

/** An infrastructure or DNS change from the change log. */
public record Change(
    String id,
    LocalDate date,
    String title,
    String owner,
    String rollbackPlan,
    String source,
    String url) {}

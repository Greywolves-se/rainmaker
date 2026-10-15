package se.rainmaker.missioncontrol.collector.snapshot;

import java.time.LocalDate;

/** The current sprint. {@code goal} is null when none is set. */
public record Sprint(
    String name, LocalDate startDate, LocalDate endDate, String goal, String source, String url) {}

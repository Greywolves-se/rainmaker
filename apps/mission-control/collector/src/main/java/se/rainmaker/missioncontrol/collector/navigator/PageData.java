package se.rainmaker.missioncontrol.collector.navigator;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import se.rainmaker.missioncontrol.collector.snapshot.Decision;
import se.rainmaker.missioncontrol.collector.snapshot.Question;
import se.rainmaker.missioncontrol.collector.snapshot.Risk;
import se.rainmaker.missioncontrol.collector.snapshot.Snapshot;
import se.rainmaker.missioncontrol.collector.snapshot.SnapshotJson;
import se.rainmaker.missioncontrol.collector.snapshot.WorkItem;

/**
 * What the AI model is told about each page (#115, ADR 0002): only the data that page shows, as
 * JSON, without URLs or source ids. The names in it are hidden by the caller before it leaves.
 */
final class PageData {

  /** The dashboard pages that get AI text, in the order they are generated. */
  enum Page {
    OVERVIEW,
    DEVELOPMENT,
    SOURCES;

    String id() {
      return name().toLowerCase(java.util.Locale.ROOT);
    }
  }

  /** Splits owner fields such as "Mission Control developers (Emil, Gabriel, Mai)". */
  private static final Pattern NAME_LIST =
      Pattern.compile("\\s*(?:[,/&()]|\\band\\b|\\boch\\b)\\s*");

  private PageData() {}

  /** The text sent for {@code page}: today's date, the page id and the page's data as JSON. */
  static String text(Snapshot snapshot, Page page) {
    Map<String, Object> data =
        switch (page) {
          case OVERVIEW -> overview(snapshot);
          case DEVELOPMENT -> development(snapshot);
          case SOURCES -> sources(snapshot);
        };
    return "Today: "
        + LocalDate.ofInstant(snapshot.generatedAt(), ZoneOffset.UTC)
        + "\nPage: "
        + page.id()
        + "\nData (JSON):\n"
        + SnapshotJson.mapper().writeValueAsString(data);
  }

  /**
   * Every person in the snapshot: assignees, authors, owners and last editors. Owner fields that
   * list several people are also split, so each name inside is hidden too.
   */
  static Set<String> people(Snapshot snapshot) {
    Set<String> people = new TreeSet<>();
    snapshot.workItems().forEach(item -> people.addAll(item.assignees()));
    snapshot.pullRequests().forEach(pull -> people.add(pull.author()));
    snapshot.decisions().forEach(decision -> addOwner(people, decision.owner()));
    snapshot.risks().forEach(risk -> addOwner(people, risk.owner()));
    snapshot.questions().forEach(question -> addOwner(people, question.owner()));
    snapshot.changes().forEach(change -> addOwner(people, change.owner()));
    snapshot.documents().stream()
        .filter(document -> document.lastEditor() != null)
        .forEach(document -> people.add(document.lastEditor()));
    return people;
  }

  private static void addOwner(Set<String> people, String owner) {
    people.addAll(ownerNames(owner));
  }

  /**
   * The names in an owner field: the whole field, plus each name in a list. Only parts that start
   * with a capital letter count, and the group label before a parenthesis does not, so "Mission
   * Control developers (Emil, Gabriel, Mai)" gives Emil, Gabriel and Mai, and "Kalle och teamet"
   * gives Kalle, never "teamet" or "Mission Control developers".
   */
  static Set<String> ownerNames(String owner) {
    Set<String> names = new LinkedHashSet<>();
    names.add(owner);
    int bracket = owner.indexOf('(');
    String list = bracket < 0 ? owner : owner.substring(bracket + 1);
    Arrays.stream(NAME_LIST.split(list))
        .map(String::strip)
        .filter(part -> part.length() > 1 && Character.isUpperCase(part.charAt(0)))
        .forEach(names::add);
    return names;
  }

  private static Map<String, Object> overview(Snapshot snapshot) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("project", snapshot.project().name());
    data.put("milestones", snapshot.project().milestones());
    data.put("sprint", sprint(snapshot));
    data.put("workInSprintByStatus", statusCounts(snapshot));
    data.put(
        "decisionsWaitingForOwners",
        snapshot.decisions().stream()
            .filter(d -> d.needsOwnerDecision() && d.status() == Decision.Status.PROPOSED)
            .map(
                d -> Map.of("id", d.id(), "title", d.title(), "owner", d.owner(), "date", d.date()))
            .toList());
    data.put(
        "redAndYellowRisks",
        snapshot.risks().stream()
            .filter(risk -> risk.level() != Risk.Level.GREEN)
            .map(
                risk ->
                    Map.of(
                        "id", risk.id(),
                        "title", risk.title(),
                        "level", risk.level(),
                        "owner", risk.owner(),
                        "mitigation", risk.mitigation()))
            .toList());
    data.put("openQuestions", openQuestions(snapshot));
    data.put("recentEvents", snapshot.events().stream().limit(10).map(e -> e.title()).toList());
    return data;
  }

  private static Map<String, Object> development(Snapshot snapshot) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("sprint", sprint(snapshot));
    data.put(
        "workInSprint",
        snapshot.workItems().stream()
            .filter(WorkItem::inCurrentSprint)
            .limit(40)
            .map(
                item ->
                    Map.of(
                        "id", item.id(),
                        "title", item.title(),
                        "status", item.status(),
                        "assignees", item.assignees()))
            .toList());
    data.put(
        "pullRequests",
        snapshot.pullRequests().stream()
            .map(
                pull -> {
                  Map<String, Object> entry = new LinkedHashMap<>();
                  entry.put("number", pull.number());
                  entry.put("title", pull.title());
                  entry.put("author", pull.author());
                  entry.put("draft", pull.draft());
                  entry.put("state", pull.state());
                  entry.put("review", pull.review());
                  entry.put("checks", pull.checks());
                  entry.put("openedAt", pull.createdAt());
                  return entry;
                })
            .toList());
    data.put(
        "latestBuilds",
        snapshot.builds().stream()
            .limit(5)
            .map(build -> Map.of("workflow", build.workflow(), "status", build.status()))
            .toList());
    data.put("openQuestions", openQuestions(snapshot));
    return data;
  }

  private static Map<String, Object> sources(Snapshot snapshot) {
    List<Map<String, Object>> sources = new ArrayList<>();
    snapshot
        .sources()
        .forEach(
            source -> {
              Map<String, Object> entry = new LinkedHashMap<>();
              entry.put("name", source.name());
              entry.put("status", source.status());
              entry.put("fetchedAt", source.fetchedAt());
              entry.put("message", source.message());
              sources.add(entry);
            });
    return Map.of("sources", sources);
  }

  private static Map<String, Object> sprint(Snapshot snapshot) {
    if (snapshot.sprint() == null) {
      return null;
    }
    Map<String, Object> sprint = new LinkedHashMap<>();
    sprint.put("name", snapshot.sprint().name());
    sprint.put("startDate", snapshot.sprint().startDate());
    sprint.put("endDate", snapshot.sprint().endDate());
    sprint.put("goal", snapshot.sprint().goal());
    return sprint;
  }

  private static Map<WorkItem.Status, Long> statusCounts(Snapshot snapshot) {
    Map<WorkItem.Status, Long> counts = new LinkedHashMap<>();
    for (WorkItem.Status status : WorkItem.Status.values()) {
      counts.put(
          status,
          snapshot.workItems().stream()
              .filter(item -> item.inCurrentSprint() && item.status() == status)
              .count());
    }
    return counts;
  }

  private static List<Map<String, Object>> openQuestions(Snapshot snapshot) {
    return snapshot.questions().stream()
        .filter(question -> question.status() == Question.Status.OPEN)
        .map(
            question -> {
              Map<String, Object> entry = new LinkedHashMap<>();
              entry.put("id", question.id());
              entry.put("title", question.title());
              entry.put("owner", question.owner());
              entry.put("neededBy", question.neededBy());
              entry.put("critical", question.critical());
              return entry;
            })
        .toList();
  }
}

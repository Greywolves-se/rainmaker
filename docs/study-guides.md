# Study guides

After Claude opens a draft PR that changes code, it writes a study guide for it: one page that
explains all the changed code and ends with questions that check the developer understood it
(CLAUDE.md, "Study guides"; #179). A PR that only changes docs or configuration gets none. The
developer works through the guide before marking the PR ready, and reviewers can use it before
reviewing.

A guide is light by default and deep on request (#212): a main path of about 15 minutes teaches
the code, and closed **Go deeper** boxes hold the full detail. The example to follow is the guide
for #151 part 2: <https://claude.ai/artifact/KPgZANAPEVVUBSMvkKq3TR>.

Every guide is listed in the team's hub, **Rainmaker Study Guides**:
<https://claude.ai/artifact/UVZo7HVFGFevPFHQa3BCTy> (owned by Gabriel).

## What Claude does, and what the developer does

| Step | Who |
|---|---|
| 1. Write the guide as one HTML file | Claude |
| 2. Publish it as a private claude.ai page (Artifact tool) | Claude |
| 3. Add it to the hub (ArtifactData tool) | Claude |
| 4. Share the guide with the team from its Share menu | **The developer.** Claude cannot change who has access to a page |
| 5. When the PR changes after a review: update the guide and its hub entry | Claude |

Claude ends step 3 by reminding the developer of step 4: until the guide is shared, the link in
the hub doesn't open for anyone else.

**One-time setup per person:** the hub's owner shares the hub with you as **Contributor** (a
Viewer can read it but not add entries), and you run Claude Code signed in to claude.ai, so the
Artifact and ArtifactData tools are available. Without them, Claude writes the guide as a local
file and says it couldn't publish it.

## What a guide contains

Write for the developer of the PR, who may not know every language or library it touches.

**Write it in English**, like the code, the docs and the PRs, even when the conversation with
Claude is in Swedish. That includes the quiz and the hub entry. Swedish only appears where the
code or product uses it (for example the panel label "Annons").

### The main path (about 15 minutes)

Everything a developer needs to understand the code and pass the quiz, and nothing more.

1. **Intro:** how to read the guide (the main path is enough; the boxes are optional), a button
   that opens or closes all Go deeper boxes, and a **Sources** line linking the PR, its changed
   files, the issue and the contract section.
2. **Five things to know:** the ideas the developer must leave with, as a numbered list (three to
   five). Someone who reads only this list still knows what the change guarantees.
3. **The flow:** what the change does for the user or the product, in a few steps through the
   apps.
4. **Concepts, only when new to the team:** the language, framework or browser ideas the code
   depends on, briefly (for example CORS, or `@ConfigurationProperties` for Spring). An idea an
   earlier guide already explains gets a link to that guide instead.
5. **The code with logic in it:** the real, final code (not paraphrased) of the parts that make
   decisions, usually one or two methods or classes. A block may leave out lines that don't
   matter there, marked `…` and with the label saying so; the full code is in its Go deeper box.
   Each block is followed by **at most five notes** on why, not what: what is surprising, easy to
   get wrong or a product rule.
6. **The rest, one line each:** a table of the other changed files (records, configuration, POM,
   properties, contract files), each linked to the file at the PR's commit
   (`…/blob/<full commit sha>/<path>`, which keeps working after the branch is deleted), with one
   line on what it does.
7. **Tests and decisions, as bullets:** what the tests prove, what they can't, and the two or
   three decisions or limits that matter. The PR description has the rest.
8. **Quiz:** about 8–10 multiple-choice questions with an explanation for every answer, then 3
   open questions with model answers to reveal. Mostly "what happens if …" scenarios rather than
   recall. Every "thing to know" is covered by at least one question, and the two quizzes don't
   ask the same thing.

### Go deeper boxes

After each part of the main path, a closed `<details class="deeper">` box whose summary starts
with "Go deeper:" holds the detail, so nothing from a full guide is lost:

- the full code of every changed file, with its notes (a box may skip a part the main path already
  shows in full, with a comment saying so);
- more on a concept (for example what CORS doesn't protect);
- every test and the acceptance criterion it covers;
- each decision with the alternative it beat, and any flaky test or probe found on the way.

The boxes look different from the main path (a dashed border and an accent-coloured summary), and
the intro's button opens or closes all of them. The quiz never needs a box.

### The quiz

Check every quiz answer against the code. A guide that teaches something wrong is worse than none.

Spread the correct answers over all positions (A, B, C, D) with no pattern: written as they
come, they tend to land on the same letter, and the quiz then tests nothing. Count the letters of
the correct answers before publishing.

Don't let the options give the answer away. Written as they come, the correct option is the long,
careful one and the wrong ones are short throwaways ("It is faster."), so picking the longest
option wins (#195):

- **Wrong options are real misconceptions:** the mistake a developer could plausibly make, such
  as "a CountDownLatch guarantees the inserts overlap". Each one must still be false; check it
  against the code like the correct answer.
- **All four options are about as long.** Keep the correct option short, and put the reasons in
  the explanation shown after answering.
- **Before publishing, compare the lengths:** the correct option should be the longest in about a
  quarter of the questions, as often as chance would make it.

## Publishing the guide

- **One self-contained HTML file**, written for the Artifact tool (it adds the document skeleton;
  see the tool's page contract). Keep the source in the session's scratchpad, not in the repo.
- **Title:** a short name, e.g. `Widget Panel Study Guide`. **Icon:** `book`. **Description:** one
  sentence naming the issue and PRs.
- **Before publishing,** check that every Go deeper box starts closed, that the intro's button
  opens and closes all of them, and that every code block renders.
- **The page stays private** until the developer shares it. It contains code from this private
  repository: share it with the team only, never by public link.

## Adding the guide to the hub

The hub keeps one document per guide in the collection `guides` of its database.

1. **Find the developer's user id on the hub:** an ArtifactData `list` of the collection
   `data/users/me` on the hub's URL. The result says what `me` resolved to (`u_…`). That id makes
   the hub show the entry as added by the developer, and lets them remove it.
2. **Create the entry** with an ArtifactData `set` on collection `guides`, doc id
   `issue-<number>-<short-name>` (e.g. `issue-152-widget-panel`):

   | Field | Value |
   |---|---|
   | `title` | e.g. `#152: the panel in rainmaker.js` (at most 120 characters) |
   | `url` | the guide's `https://claude.ai/artifact/…` link |
   | `refs` | issue and PRs, e.g. `#152 · PR #177, #178` |
   | `area` | one of `g1`, `g2`, `g3`, `g4`, `mission-control`, `infra`, `other` |
   | `summary` | one sentence on what the PR does (at most 200 characters) |
   | `addedBy` | the id from step 1 |
   | `addedAt` | the current time, ISO 8601 in UTC (e.g. `2026-10-23T10:47:00.000Z`). Not a guessed or rounded time: the hub sorts by it |

3. **Read it back** with an ArtifactData `get`, to check the fields.

## When the PR changes after a review

- **The guide:** change the code excerpts, notes and quiz answers that the review changed, and
  publish to the **same link**. In the session that published it, publish the same file again; in
  another session, first `read` the guide's URL with the Artifact tool, then publish with `url`.
- **The hub entry:** `get` it, then `update` only the fields that changed (usually `refs` or
  `summary`), with `if_version` set to the version the `get` showed. Don't change `addedBy` or
  `addedAt`.
- If the developer added the entry by hand in the hub instead, find it with an ArtifactData `list`
  of `guides`.

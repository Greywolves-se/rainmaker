# Study guides

After Claude opens a draft PR that changes code, it writes a study guide for it: one page that
explains all the changed code and ends with questions that check the developer understood it
(CLAUDE.md, "Study guides"; #179). A PR that only changes docs or configuration gets none. The
developer works through the guide before marking the PR ready, and reviewers can use it before
reviewing.

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

1. **The big picture:** what the change does for the user or the product, the flow through the
   apps, and the product rules it must keep.
2. **Concepts first:** the language, framework or browser ideas the code depends on, explained
   briefly (for example Promises and closures for TypeScript, `@ConfigurationProperties` for
   Spring).
3. **File by file:** the real, final code of each changed file (not paraphrased), each block
   followed by numbered notes on what it does and why.
4. **How it was tested:** the tools, which test covers which acceptance criterion, what the tests
   cannot prove, and any probe that showed a test can fail.
5. **Decisions and limitations:** each choice with the alternative it beat, and the known
   weaknesses.
6. **Quiz:** about 15–25 multiple-choice questions with an explanation for every answer (include
   "what happens if …" scenarios), then 5–8 open questions with model answers to reveal.

Check every quiz answer against the code. A guide that teaches something wrong is worse than none.

Spread the correct answers over all positions (A, B, C, D) with no pattern: written as they
come, they tend to land on the same letter, and the quiz then tests nothing. Count the letters of
the correct answers before publishing.

## Publishing the guide

- **One self-contained HTML file**, written for the Artifact tool (it adds the document skeleton;
  see the tool's page contract). Keep the source in the session's scratchpad, not in the repo.
- **Title:** a short name, e.g. `Widget Panel Study Guide`. **Icon:** `book`. **Description:** one
  sentence naming the issue and PRs.
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

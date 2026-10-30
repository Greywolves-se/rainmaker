# ADR 0005: A behaviour-based reader flow replaces the headline hover

- **Status:** Accepted by Peter (29 Oct 2026, #215). Detailed design and user tests remain (#222).
- **Date:** 30 Oct 2026
- **Decision log:** B34 (`apps/mission-control/data/rainmaker/decisions.yaml`)
- **Issue:** #221. The work: #217–#220, #222. Replaces the hover part of ADR 0004.

## Context

Solution Architecture v0.5 (UC-06) and ADR 0004 describe a panel that opens when the reader
hovers the headline, and the team built it for the demo (#152). Peter's comments on v0.5
("Rainmaker – Kommentarer till Solution Architecture v0.5", 8 Oct 2026, sections 4–5) asked to
reconsider it, but the comments didn't reach v0.5 or this repository:

- Hover doesn't exist on mobile, and the solution must work as naturally there as on desktop.
- The commercial step should come after the reader has read the article, not before.
- Rainmaker must not disturb the reading.

## Decision

**First the article, then confirmed interest, then a relevant commercial next step.**

1. The reader opens the article and reads it undisturbed.
2. **Qualified reading:** after enough active reading, the interest counts as confirmed. The first
   hypothesis is 60 seconds of active time, combined with simple activity signals (the page is
   visible, the reader scrolls, a part of the article has been reached), so a phone left on the
   table doesn't count. The 60 seconds is a setting to test, never a hard-coded rule.
3. **Leaving the article:** when the reader then shows signs of leaving the article content
   (for example the scroll position or another defined signal), the widget shows the article's
   approved links as a clearly labelled ("Annons") next step.
4. **Placement:** beside the article on desktop; after the article text, or in another discreet
   component that never covers or interrupts the reading, on mobile.

The exact signals are proposed by the team, demonstrated for both desktop and mobile on the test
site, and approved by Peter (#222). The definition of "read" can be refined during the pilot.

**What stays the same:** the widget API (contract 7.4) and the hops behind it (G1 → G3 → G2,
contract 7.3), the signed click links (7.5), no links for red or unclassified articles (D-4),
and failing open for the reader. Only when rainmaker.js asks for the links, and where it shows
them, changes.

**Privacy:** the reading signals stay in the browser. Nothing about them is stored or sent, so
there are no reader profiles (CLAUDE.md, product rules).

## Consequences

- rainmaker.js gets new parts: qualified reading (#218), the leaving signal (#219), and the
  placement that replaces `hover-panel.ts` (#220). The test site gets places for the next step
  and an end-of-article marker (#217).
- **The demo on 30 Oct 2026 still uses the hover** (#152). The new flow replaces it after the
  demo.
- **When the widget API is called:** rainmaker.js asks for the links in a page view that reaches
  qualified reading, instead of on the first hover. A page view that never qualifies makes no call.
- **The end-to-end test** (#154) keeps the step that makes the widget show the links in one
  helper, so only that helper changes when #220 lands.
- **Open until #222:** how long without scrolling counts as idle, which signal means "leaving",
  what counts as mobile, whether the reader can close the next step, and paywalled articles.

## Alternatives considered

- **Keep the headline hover** (#152): built and working on desktop, but hover doesn't exist on
  mobile, and it shows the commercial step before the reader has read the article.

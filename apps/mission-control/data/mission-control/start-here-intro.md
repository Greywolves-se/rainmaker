## What we are building

Mission Control is the team's internal status app for Rainmaker. It shows, per project, the current sprint, pull requests, decisions, risks, open questions and links, and writes this report so a new person or an AI assistant can get up to speed.

- **How it works:** a Java collector reads GitHub and the YAML data files and writes one JSON snapshot per project; a static React app shows it. No backend and no database (ADR 0001, see Links).
- **Where it runs:** locally with `docker compose`, and on the staging account in AWS behind the team's Google login.
- **What belongs here:** GitHub issues and pull requests labelled `mission-control`, and the files in `apps/mission-control/data/mission-control/`. Everything else is Rainmaker's.

## How we work

- Work from a GitHub issue; branch `feature/<issue>-short-name` from `develop`; PRs under ~400 lines with a summary; a human approves every PR.
- Label Mission Control issues **and pull requests** `mission-control`, or they show up under Rainmaker.
- Read `CLAUDE.md` and `apps/mission-control/README.md` before using Claude Code.

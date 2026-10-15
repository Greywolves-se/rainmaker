# Mission Control - Claude Code instructions

The root `CLAUDE.md` applies here too. These rules add to it for `apps/mission-control/`
and take precedence where they are more specific.

## What this module is
The team's internal status app, not part of the Rainmaker product. A Java collector
(`collector/`) reads GitHub and the YAML files in `data/` and writes one JSON snapshot to
`out/`; the React app (`web/`) shows it. See `README.md` and the V1 decision in #6.

## Rules
- V1 is read-only: the collector never writes to GitHub or any other source.
- The snapshot contract (#10) is the only interface between `collector/` and `web/`.
  Change the schema, the Java records and the TypeScript types together.
- The collector is a command-line run, not a server: it runs once and exits. Do not add a
  web starter to it.
- Java package root: `se.rainmaker.missioncontrol.collector`.
- `out/` holds real project data and is git-ignored. Never commit or publish a real
  snapshot; tests use the fictional sample in `contract/`.
- The GitHub token comes from the environment (`MC_GITHUB_TOKEN`, see `.env.example`).
  Never log it or put it in the snapshot.
- Log lines are JSON (ECS) and carry the run's `correlation_id`. A one-shot run has no
  incoming request, so there is no `trace_id`.

## Build and test
- Collector only, from the repository root:
  `./mvnw -pl apps/mission-control/collector -am verify`
- Web app: `npm ci`, `npm run lint`, `npm test` and `npm run build` inside `web/`.

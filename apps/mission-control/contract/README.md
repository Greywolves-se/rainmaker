# Mission Control – snapshot contract

The snapshot is the one JSON file the collector writes and the web app reads (#10). This
folder is the single source of truth for its shape.

| File | What |
|---|---|
| `snapshot.schema.json` | JSON Schema (draft 2020-12) with a description for every field |
| `fixtures/snapshot.sample.json` | Fictional sample. Both the collector and the web app test against it |

## Rules
- **Domain-shaped, not page-shaped.** The snapshot describes work items, pull requests, risks and so on. How a page shows them is decided in the web app (#18).
- **Every field is required.** A value that is unknown is `null`, never missing. Every object has `additionalProperties: false`, so a typo in a field name fails the contract tests.
- **Every item from a source has `source` and `url`.** `source` is the `id` of an entry in `sources`, and `url` links to the details. `sources` is open-ended: a new source (e.g. `slack`) needs no schema change.
- **Timestamps** are UTC instants (`2026-10-15T07:30:00Z`); **dates** are calendar dates (`2026-12-18`).
- **Ids** are unique within their array. The schema cannot check this, so the collector must.
- **Versioning:** `schemaVersion` changes only for breaking changes (removing or renaming a field, or changing its type or meaning). Adding an enum value or a new optional source is not breaking for the schema, but the web app must handle it.

## Changing the contract
Change these together, in one PR:
1. `snapshot.schema.json`
2. the sample in `fixtures/`
3. the Java records in the collector
4. the TypeScript types in the web app

The contract tests on both sides fail when they disagree.

## Tests

### Collector
`SnapshotSchemaTest` checks that the sample matches the schema, and that the schema rejects unknown fields, missing fields, unknown status values and broken dates.

`SnapshotJsonTest` reads the sample into the Java records (package `se.rainmaker.missioncontrol.collector.snapshot`) and writes it back unchanged. It also checks that a snapshot built in code matches the schema, and that unknown fields fail. `SnapshotEnumTest` checks that every Java enum has exactly the values the schema allows, including values the sample does not use. `SnapshotJson` holds the JSON settings the collector writes with; Java enum names are written in lowercase (`IN_PROGRESS` → `in_progress`).

### Web app
`src/contract/snapshot.ts` holds the zod schemas, the TypeScript types (derived from them) and `parseSnapshot()`, the runtime check for a fetched snapshot. Unknown fields are dropped there, so a newer collector never breaks an older dashboard. `snapshot.contract.test.ts` checks the sample with the JSON Schema (ajv), reads it without losing or changing a field, rejects missing fields and unknown values, and compares every enum with the schema. It is excluded from `tsc`, because the web Docker build copies only `web/`; Vitest runs it.

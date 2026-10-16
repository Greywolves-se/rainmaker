# ADR 0001: Mission Control V1 runs on a snapshot, no backend

- **Status:** Accepted by the Mission Control developers (Emil, Gabriel, Mai), who own Mission
  Control decisions and approve each other's work.
- **Date:** 16 Oct 2026
- **Decision log:** B34 (`apps/mission-control/data/decisions.yaml`)
- **Issue:** #6

## Context

Architecture v0.5, section 10.6, describes Mission Control as a Spring Boot service with its own
database. For V1 the team needs a working status dashboard early, before AWS login is in place,
and all the data it shows already lives in GitHub and in files in this repository.

## Decision

- A Java collector reads GitHub (issues and milestones; later pull requests and builds) and the
  YAML data files, and writes **one JSON snapshot** that follows the snapshot contract
  (`apps/mission-control/contract/snapshot.schema.json`, #10).
- A **static React app** reads that snapshot. There is no backend, no database and no writing
  from the UI.
- It runs locally (`docker compose`) and in the AWS **staging** account: GitHub Actions builds the
  images and pushes them to ECR, and ECS (Fargate) runs them (#84). The staging web image carries
  a fresh snapshot from the collector on every develop deploy and on weekday mornings (#17).
  CDK brings this setup under code (#61–#63).
- Staging is reached only through a URL behind a Google login, checked by the load balancer, with
  an allowlist of team emails in Cognito (#67–#71). Production later uses the same pattern.
- GitHub Pages is not an option, because it is public on our plan.

## Why no backend or database in V1

- Every piece of data already has a source of truth (GitHub, data files). A database would copy it.
- One validated file is easy to test, review and debug. Java and TypeScript check it against the
  same schema.
- Fewer moving parts: no API to secure, no migrations, no long-running service with state.

## Consequences

- **No live data.** The dashboard is as fresh as the latest collector run ("Last updated" in the
  top bar).
- **No history.** Each snapshot replaces the previous one. Workflow artifacts keep it for 14 days.
- **No editing in the UI.** Changes go through GitHub or the data files.
- **Moving to a backend later is cheap.** A Spring Boot service on AWS can serve the same JSON at
  the same address (`/snapshot.json`). The contract stays the same, so the web app does not
  change, and the contract tests in Java and TypeScript keep both sides aligned.

## Data and access

- The repository stays **private** (from 16 Oct 2026).
- Real snapshots contain internal project data and are **never published publicly**. They are not
  committed (`apps/mission-control/out/` is git-ignored), never served from a public URL, and only
  shown on staging behind the login.

## Before production

- The allowlist login is in place and tested (#69).
- The ECS setup is under CDK (#62).
- A decision on how production gets its snapshot, for example a scheduled ECS task instead of
  baking it into the image.

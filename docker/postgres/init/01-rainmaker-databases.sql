-- Rainmaker's local databases (#155, architecture D16): one database and one login per module,
-- on one PostgreSQL server. A login can only connect to its own module's database, so a module
-- can never read another module's data.
--
-- Run once by PostgreSQL's image when its data volume is new (docker-compose.yml), and by the
-- apps' tests through Testcontainers. To start over: docker compose down -v
--
-- The password is for a developer's own machine only, never used anywhere else. In AWS each
-- module's credentials come from Secrets Manager.

CREATE ROLE content_ingestion LOGIN PASSWORD 'local-dev-only';
CREATE DATABASE content_ingestion OWNER content_ingestion;
REVOKE CONNECT ON DATABASE content_ingestion FROM PUBLIC;

CREATE ROLE content_intent LOGIN PASSWORD 'local-dev-only';
CREATE DATABASE content_intent OWNER content_intent;
REVOKE CONNECT ON DATABASE content_intent FROM PUBLIC;

CREATE ROLE matching_ranking LOGIN PASSWORD 'local-dev-only';
CREATE DATABASE matching_ranking OWNER matching_ranking;
REVOKE CONNECT ON DATABASE matching_ranking FROM PUBLIC;

CREATE ROLE advertiser_marketplace LOGIN PASSWORD 'local-dev-only';
CREATE DATABASE advertiser_marketplace OWNER advertiser_marketplace;
REVOKE CONNECT ON DATABASE advertiser_marketplace FROM PUBLIC;

CREATE ROLE publisher_control LOGIN PASSWORD 'local-dev-only';
CREATE DATABASE publisher_control OWNER publisher_control;
REVOKE CONNECT ON DATABASE publisher_control FROM PUBLIC;

CREATE ROLE tracking_click LOGIN PASSWORD 'local-dev-only';
CREATE DATABASE tracking_click OWNER tracking_click;
REVOKE CONNECT ON DATABASE tracking_click FROM PUBLIC;

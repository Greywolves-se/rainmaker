# syntax=docker/dockerfile:1
# Image for one Rainmaker app (G1, G2 or G3), for the local demo stack (#146).
# Build context: the repository root, because the apps need the parent POM, the Maven Wrapper and
# the services they assemble.
#   docker build -f apps/Dockerfile --build-arg APP=g2-content-app --target app .
#   docker build -f apps/Dockerfile --build-arg APP=g1-reader-app --target g1 .
# docker-compose.yml at the root builds all three.

# rainmaker.js, which G1 serves at /widget/rainmaker.js (#152). Only the g1 target uses it, so the
# other apps never build it.
FROM node:22-alpine AS widget
WORKDIR /widget
COPY widget/package.json widget/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY widget/ ./
RUN npm run build

FROM eclipse-temurin:21-jdk-noble AS build
# Without unzip the Maven Wrapper downloads the .tar.gz instead of the .zip, and the
# distributionSha256Sum in .mvn/wrapper/maven-wrapper.properties (made for the .zip) fails.
RUN apt-get update \
    && apt-get install -y --no-install-recommends unzip \
    && rm -rf /var/lib/apt/lists/*
ARG APP
WORKDIR /src
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY services/ services/
COPY apps/${APP}/ apps/${APP}/
# The root POM alone (-N), then the services, then the app against them. Building this way means
# the other modules of the root build (Mission Control, infra, ...) don't have to be copied.
# Tests, formatting, Checkstyle and coverage run in CI; the image only packages the jar.
# sharing=locked: G1, G2 and G3 build at the same time, and the Maven Wrapper's first download of
# Maven into the shared cache fails now and then when two builds write it at once.
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    ./mvnw -B -q -N install \
        -DskipTests -Dspotless.check.skip=true -Dcheckstyle.skip=true -Djacoco.skip=true \
    && ./mvnw -B -q -f services/pom.xml install \
        -DskipTests -Dspotless.check.skip=true -Dcheckstyle.skip=true -Djacoco.skip=true \
    && ./mvnw -B -q -f apps/${APP}/pom.xml package \
        -DskipTests -Dspotless.check.skip=true -Dcheckstyle.skip=true -Djacoco.skip=true \
    && cp apps/${APP}/target/${APP}-*.jar /app.jar

FROM eclipse-temurin:21-jre-noble AS app
# A home folder, so G2 can find a mounted ~/.aws for Bedrock (docker-compose.bedrock.yml).
RUN groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --create-home --home-dir /home/app app
WORKDIR /app
COPY --from=build /app.jar /opt/app/app.jar
USER app
ENTRYPOINT ["java", "-jar", "/opt/app/app.jar"]

# G1 also serves rainmaker.js. The file sits where G1 looks by default
# (rainmaker.widget-delivery.script-location=file:widget/dist/rainmaker.js, relative to /app).
FROM app AS g1
COPY --from=widget /widget/dist/rainmaker.js widget/dist/rainmaker.js

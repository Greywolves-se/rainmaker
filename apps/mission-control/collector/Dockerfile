# syntax=docker/dockerfile:1
# Mission Control collector image.
# Build context: repository root, because the collector needs the parent POMs and the Maven Wrapper.
#   docker build -f apps/mission-control/collector/Dockerfile .

FROM eclipse-temurin:21-jdk-noble AS build
# Without unzip the Maven Wrapper downloads the .tar.gz instead of the .zip, and the
# distributionSha256Sum in .mvn/wrapper/maven-wrapper.properties (made for the .zip) fails.
RUN apt-get update \
    && apt-get install -y --no-install-recommends unzip \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /src
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY apps/mission-control/pom.xml apps/mission-control/
COPY apps/mission-control/collector/ apps/mission-control/collector/
# The snapshot schema is packed into the jar (see collector/pom.xml).
COPY apps/mission-control/contract/snapshot.schema.json apps/mission-control/contract/
# Tests run in CI (#9); the image build only packages the jar.
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q -f apps/mission-control/pom.xml -pl collector -am package -DskipTests \
    && cp apps/mission-control/collector/target/*.jar /collector.jar

FROM eclipse-temurin:21-jre-noble
RUN groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --no-create-home app
# Mirror the repository layout so the collector's default paths work unchanged:
# it reads apps/mission-control/data/ and writes apps/mission-control/out/ (#11).
WORKDIR /workspace
RUN mkdir -p apps/mission-control/data apps/mission-control/out \
    && chown app:app apps/mission-control/out
COPY --from=build /collector.jar /opt/collector/collector.jar
USER app
ENTRYPOINT ["java", "-jar", "/opt/collector/collector.jar"]

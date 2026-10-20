# syntax=docker/dockerfile:1
# Test site image (Vintrabladet): the Spring Boot jar plus the content files.
# Build context: repository root, because the module needs the parent POM and the Maven Wrapper.
#   docker build -f apps/test-site/Dockerfile .

FROM eclipse-temurin:21-jdk-noble AS build
# Without unzip the Maven Wrapper downloads the .tar.gz instead of the .zip, and the
# distributionSha256Sum in .mvn/wrapper/maven-wrapper.properties (made for the .zip) fails.
RUN apt-get update \
    && apt-get install -y --no-install-recommends unzip \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /src
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY apps/test-site/pom.xml apps/test-site/
COPY apps/test-site/src/ apps/test-site/src/
# -f builds only this module; the root POM is read as its parent, not as the whole reactor.
# Tests run in CI (test-site-ci.yml); the image build only packages the jar.
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q -f apps/test-site/pom.xml package -DskipTests \
    && cp apps/test-site/target/*.jar /test-site.jar

FROM eclipse-temurin:21-jre-noble
RUN groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --no-create-home app
# The site reads content/ relative to the working directory (site.content-directory).
WORKDIR /app
COPY apps/test-site/content/ content/
COPY --from=build /test-site.jar /opt/test-site/test-site.jar
USER app
EXPOSE 3000
ENTRYPOINT ["java", "-jar", "/opt/test-site/test-site.jar"]

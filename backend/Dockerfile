# syntax=docker/dockerfile:1.7
# One image recipe for every backend service (docs/13-deployment.md §3, REQ-DEVOPS-001 AC1):
#   docker build -f backend/Dockerfile --build-arg SERVICE=api-gateway --build-arg MANAGEMENT_PORT=9080 backend
# Tests are not run here: CI runs `mvnw verify` before any image is built.

ARG JDK_IMAGE=eclipse-temurin:21.0.12_8-jdk-alpine
ARG JRE_IMAGE=eclipse-temurin:21.0.12_8-jre-alpine

FROM ${JDK_IMAGE} AS build
WORKDIR /workspace
COPY mvnw pom.xml ./
COPY .mvn/ .mvn/
COPY platform/ platform/
COPY services/ services/
ARG SERVICE
RUN test -n "${SERVICE}" || (echo "Build argument SERVICE is required" >&2; exit 1)
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q -pl "services/${SERVICE}" -am package -DskipTests -Dspotless.check.skip=true \
    -Djacoco.skip=true \
 && cp "services/${SERVICE}/target/${SERVICE}-"*.jar /workspace/app.jar \
 && java -Djarmode=tools -jar /workspace/app.jar extract --layers --launcher --destination /workspace/extracted

FROM ${JRE_IMAGE}
ARG SERVICE
ARG MANAGEMENT_PORT
RUN test -n "${MANAGEMENT_PORT}" || (echo "Build argument MANAGEMENT_PORT is required" >&2; exit 1) \
 && addgroup -S -g 10001 app && adduser -S -D -H -u 10001 -G app app
WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/extracted/dependencies/ ./
COPY --from=build --chown=10001:10001 /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=10001:10001 /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=10001:10001 /workspace/extracted/application/ ./
USER 10001:10001

ENV MANAGEMENT_PORT=${MANAGEMENT_PORT} \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError -Djava.io.tmpdir=/tmp"
LABEL org.opencontainers.image.title="fooddelivery-${SERVICE}" \
      org.opencontainers.image.source="https://github.com/vipultikhe234/Food_Delivery_System"
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=5 \
  CMD wget -q -O /dev/null "http://127.0.0.1:${MANAGEMENT_PORT}/actuator/health/liveness" || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]

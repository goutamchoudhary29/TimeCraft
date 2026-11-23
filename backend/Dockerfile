# =====================================================================
# TimeCraft multi-stage production Dockerfile (Java 21)
# Build from the project root: docker build -f backend/Dockerfile -t timecraft .
# =====================================================================
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY backend/pom.xml backend/mvnw ./backend/
COPY backend/.mvn ./backend/.mvn
COPY backend/src ./backend/src
COPY frontend ./frontend
WORKDIR /workspace/backend
RUN chmod +x mvnw && ./mvnw -B -DskipTests clean package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S timecraft && adduser -S timecraft -G timecraft
COPY --from=build /workspace/backend/target/timecraft.jar /app/timecraft.jar
USER timecraft
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/timecraft.jar"]

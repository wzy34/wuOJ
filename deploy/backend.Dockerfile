# The backend: the Spring Boot jar on a Java 25 JRE. Built by deploy/compose.yaml with the repository root as context.

FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY backend/gradlew backend/settings.gradle.kts backend/build.gradle.kts ./
COPY backend/gradle gradle
COPY backend/src src
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon bootJar \
    && find build/libs -name '*.jar' ! -name '*-plain.jar' -exec cp {} wuoj.jar \;

# Not Alpine: docker-java reaches the Docker socket through JNA, whose native library needs glibc.
FROM eclipse-temurin:25-jre
COPY --from=build /src/wuoj.jar /app/wuoj.jar
EXPOSE 8080
# Arguments are passed to the application, e.g. `import-users /import/users.csv`.
ENTRYPOINT ["java", "-jar", "/app/wuoj.jar"]

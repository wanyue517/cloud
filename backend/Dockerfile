FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN mkdir -p /app/data && chown -R 10001:10001 /app
COPY --from=build /build/target/cloud-0.0.1-SNAPSHOT.jar app.jar
USER 10001
EXPOSE 8000
ENTRYPOINT ["java", "-jar", "app.jar"]

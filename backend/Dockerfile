FROM maven:3.9-eclipse-temurin-17 AS build
ARG MODULE=cloud-api
WORKDIR /build
COPY pom.xml .
COPY cloud-common ./cloud-common
COPY cloud-api ./cloud-api
COPY cloud-worker ./cloud-worker
RUN mvn -B -pl ${MODULE} -am -DskipTests package

FROM eclipse-temurin:17-jre
ARG MODULE=cloud-api
WORKDIR /app
RUN mkdir -p /app/data && chown -R 10001:10001 /app
COPY --from=build /build/${MODULE}/target/${MODULE}-0.0.1-SNAPSHOT.jar app.jar
USER 10001
EXPOSE 8000
ENTRYPOINT ["java", "-jar", "app.jar"]

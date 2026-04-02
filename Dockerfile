FROM maven:3.9.9-eclipse-temurin-21 AS build 
COPY . /app
WORKDIR /app
RUN chmod +x mvnw && ./mvnw clean package -DskipTests
FROM mcr.microsoft.com/playwright/java:v1.50.0-noble
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
RUN apt-get update && apt-get install -y openjdk-25-jdk-headless
ENTRYPOINT ["java", "-jar", "app.jar"]

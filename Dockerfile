FROM mcr.microsoft.com/playwright/java:v1.58.0-noble AS build
WORKDIR /app
RUN apt-get update && apt-get install -y openjdk-25-jdk-headless maven
COPY . /app
RUN chmod +x mvnw && ./mvnw clean package -DskipTests

FROM mcr.microsoft.com/playwright/java:v1.58.0-noble
WORKDIR /app
RUN apt-get update && apt-get install -y openjdk-25-jdk-headless
COPY --from=build /app/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
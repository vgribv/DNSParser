FROM mcr.microsoft.com/playwright/java:v1.58.0-noble AS build
WORKDIR /app
RUN apt-get update && apt-get install -y openjdk-25-jdk-headless maven
ENV JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64
ENV PATH="$JAVA_HOME/bin:$PATH"
COPY . /app
RUN chmod +x mvnw && ./mvnw clean package -DskipTests

FROM mcr.microsoft.com/playwright/java:v1.58.0-noble
WORKDIR /app
RUN apt-get update && apt-get install -y openjdk-25-jdk-headless xvfb
ENV JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64
ENV PATH="$JAVA_HOME/bin:$PATH"
COPY --from=build /app/target/*.jar app.jar
ENTRYPOINT ["xvfb-run", "-a", "--server-args=-screen 0 1920x1080x24", "java", "--add-opens", "java.base/java.lang=ALL-UNNAMED", "-cp", "app.jar", "-Dloader.main=ru.vgribv.parser.DNSParserApplication", "org.springframework.boot.loader.launch.PropertiesLauncher"]









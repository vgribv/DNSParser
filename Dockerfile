FROM mcr.microsoft.com/playwright/java:v1.58.0-noble AS build
WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests

FROM mcr.microsoft.com/playwright/java:v1.58.0-noble
WORKDIR /app

RUN apt-get update && apt-get install -y \
    openjdk-21-jdk-headless \
    maven \
    xvfb \
    wget \
    gnupg \
    && wget -q -O - https://dl-ssl.google.com/linux/linux_signing_key.pub | apt-key add - \
    && echo "deb [arch=amd64] http://dl.google.com/linux/chrome/deb/ stable main" >> /etc/apt/sources.list.d/google-chrome.list \
    && apt-get update && apt-get install -y google-chrome-stable \
    && ln -s /usr/bin/google-chrome-stable /usr/bin/chromium \
    && rm -rf /var/lib/apt/lists/*

ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
ENV PATH="$JAVA_HOME/bin:$PATH"
COPY --from=build /app/target/*.jar app.jar
ENTRYPOINT ["sh", "-c", "xvfb-run -a --server-args='-screen 0 1920x1080x24' java -jar app.jar 2>&1"]


# Spring Boot only — Vue is independently deployed on Render Static Site.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew settings.gradle build.gradle ./
COPY gradle/ gradle/
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
COPY src/ src/
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends fontconfig fonts-nanum && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/build/libs/*SNAPSHOT.jar /app/app.jar
ENV JAVA_TOOL_OPTIONS="-Xmx400m -Duser.timezone=Asia/Seoul"
EXPOSE 5555
CMD ["java", "-jar", "/app/app.jar"]
